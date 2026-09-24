package com.nexusbudget.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Every amount in Nexus Budget is a [Long] number of cents (minor currency units).
 * Floating point is only used for rates (APR, percentages), never for balances.
 */
object Money {

    /** Parses strings such as "1,234.56", "-12.30", "$45", "(19.99)" into cents. */
    fun parse(value: String): Long? {
        var text = value.trim()
        if (text.isEmpty()) return null
        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1)
        }
        text = text.replace(Regex("[^0-9.+\\-]"), "")
        if (text.startsWith("-")) {
            negative = !negative
            text = text.substring(1)
        } else if (text.startsWith("+")) {
            text = text.substring(1)
        }
        if (text.isEmpty() || text.count { it == '.' } > 1 || text.contains('-') || text.contains('+')) return null
        val cents = try {
            BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            return null
        } catch (e: NumberFormatException) {
            return null
        }
        return if (negative) -cents else cents
    }

    fun fromDollars(dollars: Double): Long = BigDecimal.valueOf(dollars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    fun toDollars(cents: Long): Double = cents / 100.0

    /** Plain decimal string without currency symbol or grouping, e.g. 123456 -> "1234.56". */
    fun toPlainString(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    fun format(
        cents: Long,
        currencyCode: String = "USD",
        showCents: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        val digits = if (showCents) 2 else 0
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        val value = if (showCents) cents / 100.0 else Math.round(cents / 100.0).toDouble()
        return format.format(value)
    }

    /** Signed format with an explicit "+" for positive values, used for transaction amounts. */
    fun formatSigned(cents: Long, currencyCode: String = "USD", locale: Locale = Locale.getDefault()): String {
        val body = format(abs(cents), currencyCode, true, locale)
        return when {
            cents > 0 -> "+$body"
            cents < 0 -> "-$body"
            else -> body
        }
    }

    /** Short format for charts and tight layouts: $950, $1.2K, $3.4M. */
    fun formatCompact(cents: Long, currencyCode: String = "USD", locale: Locale = Locale.getDefault()): String {
        val dollars = abs(cents) / 100.0
        val sign = if (cents < 0) "-" else ""
        val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }.getOrDefault("$")
        return when {
            dollars >= 1_000_000 -> "$sign$symbol${trim(dollars / 1_000_000)}M"
            dollars >= 10_000 -> "$sign$symbol${Math.round(dollars / 1_000)}K"
            dollars >= 1_000 -> "$sign$symbol${trim(dollars / 1_000)}K"
            else -> "$sign$symbol${Math.round(dollars)}"
        }
    }

    private fun trim(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    /** Rounds cents up to the next multiple of [step] cents (e.g. to the next $10). */
    fun roundUp(cents: Long, step: Long): Long {
        if (step <= 0 || cents <= 0) return cents
        val remainder = cents % step
        return if (remainder == 0L) cents else cents + (step - remainder)
    }
}
