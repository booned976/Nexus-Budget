package com.nexusbudget.assistant

import com.anthropic.models.messages.OutputConfig

/** Claude models offered in Settings. The user brings their own Anthropic API key. */
enum class AssistantModel(
    val id: String,
    val label: String,
    val description: String,
    val supportsEffort: Boolean,
    /** Supports server-side refusal fallbacks (fallbacks: "default"). */
    val supportsFallbacks: Boolean,
) {
    OPUS_5("claude-opus-5", "Claude Opus 5", "Recommended. Thoughtful plans with good speed.", true, true),
    FABLE_5_1("claude-fable-5-1", "Claude Fable 5.1", "Most capable. Slower and costs more per message.", true, true),
    SONNET_5("claude-sonnet-5", "Claude Sonnet 5", "Faster and lower cost.", true, false),
    HAIKU_4_5("claude-haiku-4-5", "Claude Haiku 4.5", "Fastest and cheapest, for quick questions.", false, false),
    ;

    companion object {
        val DEFAULT = OPUS_5
        fun fromId(id: String?): AssistantModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class ResponseDepth(val label: String, val description: String, val effort: OutputConfig.Effort) {
    QUICK("Quick", "Short answers, lowest cost", OutputConfig.Effort.LOW),
    BALANCED("Balanced", "Good for everyday questions", OutputConfig.Effort.MEDIUM),
    THOROUGH("Thorough", "Deeper analysis for plans (default)", OutputConfig.Effort.HIGH),
    ;

    companion object {
        val DEFAULT = THOROUGH
        fun fromName(name: String?): ResponseDepth = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class AssistantSettings(
    val apiKey: String,
    val model: AssistantModel = AssistantModel.DEFAULT,
    val depth: ResponseDepth = ResponseDepth.DEFAULT,
    /** When false the assistant only sees totals and categories, never individual transactions or merchant names. */
    val shareTransactionDetails: Boolean = true,
)
