package com.nexusbudget.core.engine

import java.util.Locale

/**
 * Turns raw bank descriptions ("POS DEBIT 0412 GREENLEAF GROCERY #1234 SPRINGFIELD IL")
 * into readable merchant names ("Greenleaf Grocery") and stable grouping keys.
 */
object MerchantNormalizer {

    private val prefixes = listOf(
        "POS DEBIT", "POS PURCHASE", "POS", "DEBIT CARD PURCHASE", "DEBIT PURCHASE", "DEBIT CARD",
        "CHECKCARD", "CHECK CARD", "PURCHASE AUTHORIZED ON", "PURCHASE", "RECURRING PAYMENT",
        "RECURRING", "PREAUTHORIZED DEBIT", "ACH DEBIT", "ACH CREDIT", "ACH", "ELECTRONIC PAYMENT",
        "ONLINE PAYMENT", "WEB PMT", "VISA DDA PUR", "DDA", "SQ *", "SQ*", "TST*", "TST *", "SP *", "SP*",
    )

    private val usStates = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
        "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY",
        "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
        "WI", "WY", "DC",
    )

    private val keepUpper = setOf("ATM", "ACH", "IRS", "DMV", "USA", "LLC", "HOA", "CVS", "KFC", "BBQ", "IRA", "HSA")

    fun clean(description: String): String {
        var text = description.uppercase(Locale.US).trim()
        text = text.replace(Regex("\\s+"), " ")
        // Strip repeated leading boilerplate (some banks stack several prefixes).
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (text.startsWith("$prefix ") || text == prefix || (prefix.endsWith("*") && text.startsWith(prefix))) {
                    text = text.removePrefix(prefix).trim()
                    changed = true
                }
            }
            // Dates like 01/02 or 01/02/24 and bare numbers at the start.
            val stripped = text.replace(Regex("^(\\d{1,2}/\\d{1,2}(/\\d{2,4})?|\\d+)\\s+"), "")
            if (stripped != text) {
                text = stripped
                changed = true
            }
        }
        text = text
            .replace(Regex("\\bWWW\\."), "")
            .replace(Regex("\\.(COM|NET|ORG|IO)\\b"), "")
            .replace(Regex("#\\s*\\d+"), " ")
            .replace(Regex("\\b[A-Z]*\\d[A-Z0-9]*\\b"), " ") // store numbers, reference ids
            .replace(Regex("[*_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Drop a trailing two-letter state code, and the city before it when it's a single word.
        val words = text.split(" ").toMutableList()
        if (words.size > 2 && words.last() in usStates) {
            words.removeAt(words.lastIndex)
            if (words.size > 2) words.removeAt(words.lastIndex)
        }
        text = words.joinToString(" ").trim(' ', '-', ',', '.')
        return text.ifBlank { description.trim().uppercase(Locale.US) }
    }

    fun displayName(description: String): String {
        val cleaned = clean(description)
        return cleaned.split(" ").joinToString(" ") { word ->
            when {
                word in keepUpper -> word
                word.isEmpty() -> word
                else -> word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
        }
    }

    /** Grouping key used to link transactions from the same merchant. */
    fun key(merchant: String): String =
        merchant.lowercase(Locale.US).replace(Regex("[^a-z]"), "").take(24).ifEmpty { merchant.lowercase(Locale.US) }
}
