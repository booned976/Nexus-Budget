package com.nexusbudget.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.Money
import com.nexusbudget.core.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Asks Claude to categorize transactions the local rules couldn't. Only the merchant text and
 * amount of each uncategorized transaction are sent. Results come back as schema-validated JSON.
 */
class AiCategorizer(private val clientFactory: (String) -> AnthropicClient = FinancialAssistant::defaultClient) {

    suspend fun categorize(
        transactions: List<Transaction>,
        categories: CategoryIndex,
        settings: AssistantSettings,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) return@withContext emptyMap()
        val allowed = categories.all.filter { it.id != Categories.UNCATEGORIZED }
        val client = clientFactory(settings.apiKey)
        try {
            val result = mutableMapOf<String, String>()
            transactions.chunked(60).forEach { batch ->
                // Short numeric keys keep the prompt small and avoid echoing internal ids.
                val keyed = batch.mapIndexed { i, t -> "t$i" to t }.toMap()
                val lines = keyed.entries.joinToString("\n") { (key, t) ->
                    "$key | ${t.merchant.take(60)} | ${t.description.take(80)} | ${Money.toPlainString(t.amount)}"
                }
                val categoryList = allowed.joinToString("\n") { "${it.id}: ${it.name} (${it.group}, ${it.kind.name.lowercase()})" }
                val params = MessageCreateParams.builder()
                    .model(settings.model.id)
                    .maxTokens(16_000L)
                    .system(
                        "You categorize bank transactions for a personal budgeting app. Pick the single best category id for each " +
                            "transaction. Negative amounts are money spent; positive amounts are money received. Transfers between the " +
                            "user's own accounts use transfer categories. If nothing fits, use the closest general category.",
                    )
                    .addUserMessage("Categories:\n$categoryList\n\nTransactions (key | merchant | description | amount):\n$lines")
                    .outputConfig(
                        OutputConfig.builder()
                            .format(JsonOutputFormat.builder().schema(schema(allowed.map { it.id })).build())
                            .apply { if (settings.model.supportsEffort) effort(OutputConfig.Effort.LOW) }
                            .build(),
                    )
                    .build()
                val message = client.messages().create(params)
                if (message.stopReason().orElse(null) != StopReason.END_TURN) return@forEach
                val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
                val parsed = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return@forEach
                parsed["assignments"]?.jsonArray?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val key = (obj["key"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    val category = (obj["category_id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    val txn = keyed[key] ?: return@forEach
                    if (categories.find(category) != null) result[txn.id] = category
                }
            }
            result
        } finally {
            client.close()
        }
    }

    private fun schema(categoryIds: List<String>): JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(
                mapOf(
                    "assignments" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "key" to mapOf("type" to "string"),
                                "category_id" to mapOf("type" to "string", "enum" to categoryIds),
                            ),
                            "required" to listOf("key", "category_id"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("assignments")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()
}
