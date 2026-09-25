package com.nexusbudget.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.coroutineContext

interface AssistantListener {
    fun onTextDelta(text: String) {}
    fun onToolStatus(label: String) {}
    fun onProposal(proposal: Proposal) {}
}

data class AssistantReply(
    val text: String,
    val proposals: List<Proposal> = emptyList(),
    /** Set when the request failed; [text] is then a friendly explanation. */
    val error: String? = null,
)

enum class ChatRole { USER, ASSISTANT }

data class ChatTurn(val role: ChatRole, val text: String)

/**
 * Conversational financial coach backed by Claude. Claude answers by calling [FinanceTools],
 * which run on the device against the user's local data. The conversation history is kept in
 * memory, append-only, so earlier turns (including thinking blocks) are always sent back unchanged.
 */
class FinancialAssistant(private val clientFactory: (String) -> AnthropicClient = ::defaultClient) {

    private val history = mutableListOf<MessageParam>()
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    val isEmpty: Boolean get() = history.isEmpty()

    fun reset() = history.clear()

    /** Rebuilds context from saved chat text, e.g. after the app restarts. */
    fun restore(turns: List<ChatTurn>) {
        history.clear()
        val merged = mutableListOf<ChatTurn>()
        for (turn in turns.filter { it.text.isNotBlank() }) {
            val last = merged.lastOrNull()
            if (last != null && last.role == turn.role) {
                merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
            } else {
                merged += turn
            }
        }
        while (merged.firstOrNull()?.role == ChatRole.ASSISTANT) merged.removeAt(0)
        if (merged.lastOrNull()?.role == ChatRole.USER) merged.removeAt(merged.lastIndex)
        merged.takeLast(20).dropWhile { it.role == ChatRole.ASSISTANT }.forEach { turn ->
            history += MessageParam.builder()
                .role(if (turn.role == ChatRole.USER) MessageParam.Role.USER else MessageParam.Role.ASSISTANT)
                .content(turn.text)
                .build()
        }
    }

    suspend fun send(
        text: String,
        settings: AssistantSettings,
        context: AssistantContext,
        listener: AssistantListener,
    ): AssistantReply = withContext(Dispatchers.IO) {
        val anthropic = clientFor(settings.apiKey)
        val turnStart = history.size
        history += MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(
                listOf(
                    ContentBlockParam.ofText("Today is ${context.today}."),
                    ContentBlockParam.ofText(text),
                ),
            )
            .build()

        val proposals = mutableListOf<Proposal>()
        val reply = StringBuilder()
        try {
            repeat(MAX_TOOL_ROUNDS) {
                val message = stream(anthropic, buildParams(settings), listener)
                val roundText = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
                when (message.stopReason().orElse(null)) {
                    StopReason.REFUSAL -> {
                        rollback(turnStart)
                        return@withContext AssistantReply(
                            "I can't help with that request. Try asking about your budget, debts, savings or goals.",
                            error = "refusal",
                        )
                    }
                    StopReason.TOOL_USE -> {
                        history += message.toParam()
                        appendParagraph(reply, roundText)
                        val results = message.content().mapNotNull { it.toolUse().orElse(null) }.map { use ->
                            FinanceTools.spec(use.name())?.let { listener.onToolStatus(it.statusLabel) }
                            val outcome = FinanceTools.execute(use.name(), toolInputJson(use), context)
                            outcome.proposal?.let {
                                proposals += it
                                listener.onProposal(it)
                            }
                            ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                    .toolUseId(use.id())
                                    .content(outcome.content)
                                    .isError(outcome.isError)
                                    .build(),
                            )
                        }
                        // All results for one assistant turn go back together in a single user message.
                        history += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
                    }
                    StopReason.MAX_TOKENS -> {
                        history += message.toParam()
                        appendParagraph(reply, roundText)
                        appendParagraph(reply, "_(The answer was cut short. Ask me to continue.)_")
                        return@withContext AssistantReply(reply.toString(), proposals)
                    }
                    else -> {
                        history += message.toParam()
                        appendParagraph(reply, roundText)
                        return@withContext AssistantReply(reply.toString().ifBlank { "Done." }, proposals)
                    }
                }
            }
            // Too many tool rounds: close the turn so the history stays valid.
            history += MessageParam.builder().role(MessageParam.Role.ASSISTANT).content("I looked up a lot of data; let me know what to focus on.").build()
            AssistantReply(reply.toString().ifBlank { "I looked up a lot of data but couldn't finish. Try a more specific question." }, proposals)
        } catch (e: CancellationException) {
            rollback(turnStart)
            throw e
        } catch (e: Exception) {
            rollback(turnStart)
            val message = friendlyError(e, settings.model)
            AssistantReply(message, error = message)
        }
    }

    private fun appendParagraph(builder: StringBuilder, text: String) {
        if (text.isBlank()) return
        if (builder.isNotEmpty()) builder.append("\n\n")
        builder.append(text.trim())
    }

    private fun rollback(size: Int) {
        while (history.size > size) history.removeAt(history.lastIndex)
    }

    @Synchronized
    private fun clientFor(apiKey: String): AnthropicClient {
        val existing = client
        if (existing != null && clientKey == apiKey) return existing
        existing?.close()
        return clientFactory(apiKey).also {
            client = it
            clientKey = apiKey
        }
    }

    private fun buildParams(settings: AssistantSettings): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(settings.model.id)
            .maxTokens(64_000L)
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(SYSTEM_PROMPT).build()))
            .messages(history.toList())
            // Automatic prompt caching: the stable tools + system prompt + earlier turns are reused across tool rounds.
            .cacheControl(CacheControlEphemeral.builder().build())
        tools(settings).forEach { builder.addTool(it) }
        if (settings.model.supportsEffort) {
            builder.outputConfig(OutputConfig.builder().effort(settings.depth.effort).build())
        }
        if (settings.model.supportsFallbacks) {
            // If a safety classifier declines a request, let the API retry it on a fallback model.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder.build()
    }

    private suspend fun stream(client: AnthropicClient, params: MessageCreateParams, listener: AssistantListener): Message {
        val job = coroutineContext[Job]
        val accumulator = MessageAccumulator.create()
        client.messages().createStreaming(params).use { response ->
            val events = response.stream().iterator()
            while (events.hasNext()) {
                job?.ensureActive()
                val event = accumulator.accumulate(events.next())
                event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { listener.onTextDelta(it.text()) }
            }
        }
        return accumulator.message()
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 10

        fun defaultClient(apiKey: String): AnthropicClient = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMinutes(5))
            .maxRetries(2)
            .build()

        fun tools(settings: AssistantSettings): List<Tool> = FinanceTools.specs
            .filter { settings.shareTransactionDetails || !it.needsTransactionDetails }
            .map { spec ->
                Tool.builder()
                    .name(spec.name)
                    .description(spec.description)
                    .inputSchema(
                        Tool.InputSchema.builder()
                            .properties(
                                Tool.InputSchema.Properties.builder()
                                    .apply { spec.properties.forEach { (key, schema) -> putAdditionalProperty(key, JsonValue.from(schema)) } }
                                    .build(),
                            )
                            .required(spec.required)
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build(),
                    )
                    // Stream tool arguments as they're generated; FinanceTools validates every input itself.
                    .eagerInputStreaming(true)
                    .build()
            }

        /** Serializes a tool call's arguments to JSON for local validation and execution. */
        fun toolInputJson(block: ToolUseBlock): String =
            try {
                jsonMapper().writeValueAsString(block._input())
            } catch (e: Exception) {
                ""
            }

        fun friendlyError(e: Throwable, model: AssistantModel): String = when (e) {
            is UnauthorizedException -> "Claude didn't accept your API key. Check it in Settings → AI assistant."
            is PermissionDeniedException -> "Your API key can't use ${model.label}. Pick another model in Settings."
            is NotFoundException -> "${model.label} isn't available to your API key. Pick another model in Settings."
            is RateLimitException -> "You've reached your API rate limit. Wait a minute, then try again."
            is BadRequestException -> "Claude couldn't process that request. " + (e.message?.take(200) ?: "")
            is InternalServerException -> "Claude is busy right now. Please try again in a moment."
            is AnthropicServiceException -> if (e.statusCode() == 529) {
                "Claude is overloaded right now. Please try again in a moment."
            } else {
                "Claude returned an error (${e.statusCode()}). Please try again."
            }
            is AnthropicIoException -> "Couldn't reach Claude. Check your internet connection."
            else -> "Something went wrong talking to Claude: ${e.message?.take(200) ?: e.javaClass.simpleName}"
        }

        val SYSTEM_PROMPT = """
            You are the financial coach inside Nexus Budget, an open-source personal budgeting app. You help one person understand their money and make plans: budgeting, paying down debt, building savings and reaching goals.

            How to work:
            - Look up real numbers with the tools before answering. Never guess balances, amounts or dates. get_financial_overview is a good first call.
            - Be specific: use actual dollar amounts, dates, and the names of the user's accounts and categories. Lead with a clear recommendation, then the reasoning.
            - Make plans concrete and ordered: what to do this month, next, and later. Back up payoff and savings numbers with simulate_debt_payoff, debt_extra_needed and project_goal.
            - When a budget change or a new goal would help, call propose_budget_changes or propose_goal so the user can apply it with one tap. Only propose what you actually recommend.
            - Sound guidance to draw on: build a starter emergency fund of about one month of essential expenses, then grow it to three to six months; always pay at least the minimum on every debt; the avalanche method minimizes interest while the snowball method builds momentum; keep credit utilization under 30%; treat 50/30/20 as a starting point, not a rule; capture any employer retirement match before making extra payments on low-interest debt; federal student loans offer income-driven repayment and forgiveness programs that are lost if refinanced into a private loan.
            - The app has read-only access. You cannot move money, pay bills, see account numbers or contact banks. Say so if asked.
            - You are not a licensed financial, tax or legal professional. For big or irreversible decisions (bankruptcy, tax questions, large investments, refinancing), briefly suggest confirming with a qualified professional.
            - If a tool returns an error, adjust and try again or explain what's missing.

            Style: the user reads on a phone. Use short paragraphs, bullet points for steps and **bold** for key numbers. Keep most answers under 200 words unless asked for detail. Be warm, direct and non-judgmental.
        """.trimIndent()
    }
}
