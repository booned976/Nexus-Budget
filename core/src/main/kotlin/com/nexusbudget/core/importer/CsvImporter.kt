package com.nexusbudget.core.importer

import com.nexusbudget.core.model.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ImportedRow(val date: LocalDate, val description: String, val amount: Long)

data class CsvImportResult(
    val rows: List<ImportedRow>,
    val skipped: Int,
    val errors: List<String>,
    val columns: CsvColumns?,
)

data class CsvColumns(
    val date: Int,
    val description: Int,
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
)

/**
 * Imports transaction exports from any bank's website. Columns are detected from the header row
 * (date, description/payee, and either a single amount column or separate debit/credit columns).
 */
object CsvImporter {

    private val dateFormats = listOf(
        "yyyy-MM-dd", "M/d/yyyy", "MM/dd/yyyy", "M/d/yy", "MM/dd/yy", "yyyy/MM/dd", "M-d-yyyy", "MM-dd-yyyy",
        "d MMM yyyy", "MMM d, yyyy", "dd-MMM-yyyy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    fun parse(text: String, invertAmounts: Boolean = false): CsvImportResult {
        val lines = parseCsv(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (lines.isEmpty()) return CsvImportResult(emptyList(), 0, listOf("The file is empty."), null)

        val headerIndex = lines.take(10).indexOfFirst { detectColumns(it) != null }
        if (headerIndex < 0) {
            return CsvImportResult(emptyList(), lines.size, listOf("Couldn't find date, description and amount columns in the header row."), null)
        }
        val columns = detectColumns(lines[headerIndex])!!
        val rows = mutableListOf<ImportedRow>()
        val errors = mutableListOf<String>()
        var skipped = 0
        for ((offset, row) in lines.drop(headerIndex + 1).withIndex()) {
            val lineNumber = headerIndex + offset + 2
            val date = row.getOrNull(columns.date)?.let(::parseDate)
            val description = row.getOrNull(columns.description)?.trim().orEmpty()
            val amount = when {
                columns.amount != null -> row.getOrNull(columns.amount)?.let(Money::parse)
                else -> {
                    val debit = columns.debit?.let { row.getOrNull(it) }?.let(Money::parse)
                    val credit = columns.credit?.let { row.getOrNull(it) }?.let(Money::parse)
                    if (debit == null && credit == null) null else (credit?.let { kotlin.math.abs(it) } ?: 0) - (debit?.let { kotlin.math.abs(it) } ?: 0)
                }
            }
            if (date == null || amount == null || description.isEmpty()) {
                skipped++
                if (errors.size < 5) errors += "Line $lineNumber: couldn't read date, description or amount."
                continue
            }
            rows += ImportedRow(date, description, if (invertAmounts) -amount else amount)
        }
        return CsvImportResult(rows, skipped, errors, columns)
    }

    fun detectColumns(header: List<String>): CsvColumns? {
        val names = header.map { it.trim().lowercase(Locale.US) }
        fun find(vararg keys: String): Int? = names.indexOfFirst { name -> keys.any { name == it } }.takeIf { it >= 0 }
            ?: names.indexOfFirst { name -> keys.any { name.contains(it) } }.takeIf { it >= 0 }

        val date = find("date", "posted date", "transaction date", "posting date", "post date") ?: return null
        val description = find("description", "payee", "merchant", "name", "memo", "details", "transaction") ?: return null
        val amount = find("amount", "amt", "value")
        val debit = find("debit", "withdrawal", "withdrawals", "money out", "outflow")
        val credit = find("credit", "deposit", "deposits", "money in", "inflow")
        if (amount == null && debit == null && credit == null) return null
        if (description == date) return null
        return if (amount != null) CsvColumns(date, description, amount = amount) else CsvColumns(date, description, debit = debit, credit = credit)
    }

    fun parseDate(value: String): LocalDate? {
        val text = value.trim().substringBefore('T').substringBefore(' ').ifEmpty { return null }
        val candidates = listOf(text, value.trim())
        for (candidate in candidates) {
            for (format in dateFormats) {
                try {
                    return LocalDate.parse(candidate, format)
                } catch (_: DateTimeParseException) {
                }
            }
        }
        return null
    }

    /** RFC 4180 CSV parsing: quoted fields, escaped quotes, and newlines inside quotes. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row += field.toString(); field.clear() }
                    '\r' -> {}
                    '\n' -> { row += field.toString(); field.clear(); rows += row.toList(); row.clear() }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row.toList()
        }
        return rows
    }
}
