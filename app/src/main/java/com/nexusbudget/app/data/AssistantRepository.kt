package com.nexusbudget.app.data

import com.nexusbudget.app.data.db.ChatMessageEntity
import com.nexusbudget.app.data.db.NexusDatabase
import com.nexusbudget.assistant.AiCategorizer
import com.nexusbudget.assistant.AssistantContext
import com.nexusbudget.assistant.AssistantListener
import com.nexusbudget.assistant.AssistantSettings
import com.nexusbudget.assistant.BudgetChange
import com.nexusbudget.assistant.ChatRole
import com.nexusbudget.assistant.ChatTurn
import com.nexusbudget.assistant.FinancialAssistant
import com.nexusbudget.assistant.Proposal
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import java.util.UUID

enum class ProposalState { PENDING, APPLIED, DISMISSED }

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val proposal: Proposal?,
    val proposalState: ProposalState?,
    val isError: Boolean,
)

/** Live state of an answer being generated. */
data class AssistantProgress(val question: String, val text: String, val status: String?)

class AssistantRepository(
    private val db: NexusDatabase,
    private val secure: SecureStore,
    private val settings: SettingsRepository,
    private val finance: FinanceRepository,
    private val scope: CoroutineScope,
) {
    private val assistant = FinancialAssistant()
    private val categorizer = AiCategorizer()
    private var restored = false
    private var job: Job? = null

    private val _progress = MutableStateFlow<AssistantProgress?>(null)
    val progress: StateFlow<AssistantProgress?> = _progress.asStateFlow()

    val messages: Flow<List<ChatMessage>> = db.chat().observeAll().map { list -> list.map { it.toMessage() } }

    private val _hasKey = MutableStateFlow(secure.has(SecureStore.ANTHROPIC_API_KEY))

    /** Whether an Anthropic API key is saved; screens observe it so they update right after setup. */
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun hasApiKey(): Boolean = _hasKey.value

    fun saveApiKey(key: String) {
        secure.put(SecureStore.ANTHROPIC_API_KEY, key.trim().ifBlank { null })
        _hasKey.value = secure.has(SecureStore.ANTHROPIC_API_KEY)
    }

    private suspend fun assistantSettings(): AssistantSettings? {
        val key = secure.get(SecureStore.ANTHROPIC_API_KEY) ?: return null
        val prefs = settings.current()
        return AssistantSettings(key, prefs.aiModel, prefs.aiDepth, prefs.aiShareDetails)
    }

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            val config = assistantSettings()
            db.chat().insert(ChatMessageEntity(role = ChatRole.USER.name, text = text, createdAt = System.currentTimeMillis()))
            if (config == null) {
                db.chat().insert(
                    ChatMessageEntity(
                        role = ChatRole.ASSISTANT.name,
                        text = "Add your Anthropic API key in Settings → AI assistant to start chatting.",
                        createdAt = System.currentTimeMillis(),
                        isError = true,
                    ),
                )
                return@launch
            }
            _progress.value = AssistantProgress(text, "", "Thinking")
            try {
                if (!restored) {
                    val history = db.chat().all().dropLast(1).filter { !it.isError }
                    assistant.restore(history.map { ChatTurn(ChatRole.valueOf(it.role), it.text) })
                    restored = true
                }
                val state = finance.state.filterNotNull().first()
                val context = AssistantContext(state.picture, state.transactions, state.categories, state.budgets, config.shareTransactionDetails)
                val reply = assistant.send(text, config, context, object : AssistantListener {
                    override fun onTextDelta(text: String) {
                        _progress.update { it?.copy(text = it.text + text, status = null) }
                    }

                    override fun onToolStatus(label: String) {
                        _progress.update { it?.copy(status = label) }
                    }
                })
                val now = System.currentTimeMillis()
                val first = reply.proposals.firstOrNull()
                db.chat().insert(
                    ChatMessageEntity(
                        role = ChatRole.ASSISTANT.name,
                        text = reply.text,
                        createdAt = now,
                        proposalJson = first?.let(::encode),
                        proposalState = first?.let { ProposalState.PENDING.name },
                        isError = reply.error != null,
                    ),
                )
                reply.proposals.drop(1).forEach { proposal ->
                    db.chat().insert(
                        ChatMessageEntity(
                            role = ChatRole.ASSISTANT.name, text = "", createdAt = now,
                            proposalJson = encode(proposal), proposalState = ProposalState.PENDING.name,
                        ),
                    )
                }
            } finally {
                _progress.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        _progress.value = null
    }

    suspend fun clear() {
        stop()
        db.chat().clear()
        assistant.reset()
        restored = true
    }

    suspend fun applyProposal(message: ChatMessage) {
        when (val proposal = message.proposal ?: return) {
            is Proposal.BudgetChanges -> finance.setBudgets(proposal.changes.associate { it.categoryId to it.monthlyAmount })
            is Proposal.NewGoal -> finance.saveGoal(
                Goal(
                    id = UUID.randomUUID().toString(),
                    name = proposal.name,
                    type = proposal.type,
                    target = proposal.target,
                    saved = 0,
                    monthlyContribution = proposal.monthlyContribution,
                    targetDate = proposal.targetDate,
                    createdOn = LocalDate.now(),
                ),
            )
        }
        db.chat().setProposalState(message.id, ProposalState.APPLIED.name)
    }

    suspend fun dismissProposal(message: ChatMessage) = db.chat().setProposalState(message.id, ProposalState.DISMISSED.name)

    /** Uses Claude to categorize recent transactions the local rules couldn't. Returns how many were categorized. */
    suspend fun categorizeWithAi(): Int {
        val config = assistantSettings() ?: throw IllegalStateException("Add your Anthropic API key in Settings first.")
        if (!config.shareTransactionDetails) throw IllegalStateException("Turn on sharing transaction details with the assistant to use this.")
        val state = finance.state.filterNotNull().first()
        val targets = state.transactions
            .filter { !it.userCategorized && (it.categoryId == null || it.categoryId == Categories.UNCATEGORIZED) }
            .take(300)
        val assignments = categorizer.categorize(targets, state.categories, config)
        finance.applyCategories(assignments)
        return assignments.size
    }

    private fun ChatMessageEntity.toMessage() = ChatMessage(
        id = id,
        role = if (role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT,
        text = text,
        proposal = proposalJson?.let { runCatching { decode(it) }.getOrNull() },
        proposalState = proposalState?.let { s -> ProposalState.entries.firstOrNull { it.name == s } },
        isError = isError,
    )

    private fun encode(proposal: Proposal): String = when (proposal) {
        is Proposal.BudgetChanges -> buildJsonObject {
            put("kind", "budget")
            put("id", proposal.id)
            put("rationale", proposal.rationale)
            putJsonArray("changes") {
                proposal.changes.forEach { c ->
                    addJsonObject {
                        put("category_id", c.categoryId)
                        put("name", c.categoryName)
                        put("amount", c.monthlyAmount)
                        c.previousAmount?.let { put("previous", it) }
                    }
                }
            }
        }
        is Proposal.NewGoal -> buildJsonObject {
            put("kind", "goal")
            put("id", proposal.id)
            put("rationale", proposal.rationale)
            put("name", proposal.name)
            put("type", proposal.type.name)
            put("target", proposal.target)
            put("monthly", proposal.monthlyContribution)
            proposal.targetDate?.let { put("date", it.toEpochDay()) }
        }
    }.toString()

    private fun decode(json: String): Proposal {
        val obj = Json.parseToJsonElement(json).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.content.orEmpty()
        return if (str("kind") == "budget") {
            Proposal.BudgetChanges(
                id = str("id"),
                changes = obj["changes"]!!.jsonArray.map { element ->
                    val c = element as JsonObject
                    BudgetChange(
                        categoryId = c["category_id"]!!.jsonPrimitive.content,
                        categoryName = c["name"]!!.jsonPrimitive.content,
                        monthlyAmount = c["amount"]!!.jsonPrimitive.long,
                        previousAmount = c["previous"]?.jsonPrimitive?.longOrNull,
                    )
                },
                rationale = str("rationale"),
            )
        } else {
            Proposal.NewGoal(
                id = str("id"),
                name = str("name"),
                type = GoalType.entries.firstOrNull { it.name == str("type") } ?: GoalType.OTHER,
                target = obj["target"]!!.jsonPrimitive.long,
                monthlyContribution = obj["monthly"]!!.jsonPrimitive.long,
                targetDate = obj["date"]?.jsonPrimitive?.longOrNull?.let(LocalDate::ofEpochDay),
                rationale = str("rationale"),
            )
        }
    }
}
