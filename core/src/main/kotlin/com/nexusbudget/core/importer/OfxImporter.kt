package com.nexusbudget.core.importer

import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Money
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class StatementKind { BANK, CREDIT_CARD, INVESTMENT }

data class StatementTransaction(
    /** The institution's id for the transaction (FITID), unique within the account. */
    val id: String?,
    val date: LocalDate,
    /** Negative when money leaves the account. */
    val amount: Long,
    val description: String,
)

data class StatementHolding(
    val securityId: String,
    val name: String,
    val ticker: String?,
    val quantity: Double,
    val value: Long,
)

/** One account's statement from an OFX, QFX or QBO file. */
data class Statement(
    val kind: StatementKind,
    val accountNumber: String,
    /** CHECKING, SAVINGS, MONEYMRKT, CREDITLINE or CD for bank statements. */
    val bankAccountType: String?,
    val institution: String?,
    val institutionId: String?,
    val currency: String,
    /** In the app's convention: assets positive, debts as the positive amount owed. Null if the file has no balance. */
    val balance: Long?,
    val balanceDate: LocalDate?,
    val available: Long?,
    val transactions: List<StatementTransaction>,
    val holdings: List<StatementHolding>,
) {
    val mask: String get() = accountNumber.filter(Char::isLetterOrDigit).takeLast(4)

    val suggestedType: AccountType
        get() = when (kind) {
            StatementKind.CREDIT_CARD -> AccountType.CREDIT_CARD
            StatementKind.INVESTMENT -> AccountType.BROKERAGE
            StatementKind.BANK -> when (bankAccountType) {
                "SAVINGS", "MONEYMRKT", "CD" -> AccountType.SAVINGS
                "CREDITLINE" -> AccountType.LINE_OF_CREDIT
                else -> AccountType.CHECKING
            }
        }

    /**
     * A stable key for matching this account on later imports. The account number is hashed so the full
     * number is never stored.
     */
    val accountKey: String
        get() {
            val source = "${institutionId ?: institution.orEmpty()}|$kind|$accountNumber"
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            return "file:" + digest.take(12).joinToString("") { "%02x".format(it) }
        }
}

/**
 * Reads OFX statement files, which most banks, card issuers and brokerages offer as a download
 * (often labeled OFX, QFX, QBO or "Money / spreadsheet software"). Handles both the older SGML
 * format, where simple values have no closing tags, and the XML format.
 */
object OfxImporter {

    fun looksLikeOfx(text: String): Boolean = text.contains("<OFX>", ignoreCase = true) || text.contains("OFXHEADER", ignoreCase = true)

    fun parse(text: String): List<Statement> {
        val root = parseTree(text)
        val institution = root.find("FI")?.let { fi -> fi.value("ORG") }
        val institutionId = root.find("FI")?.value("FID")
        val securities = securityInfo(root)

        val bank = root.findAll("STMTRS").mapNotNull { bankStatement(it, institution, institutionId) }
        val cards = root.findAll("CCSTMTRS").mapNotNull { cardStatement(it, institution, institutionId) }
        val investments = root.findAll("INVSTMTRS").mapNotNull { investmentStatement(it, institution, institutionId, securities) }
        return bank + cards + investments
    }

    private fun bankStatement(node: Node, institution: String?, institutionId: String?): Statement? {
        val from = node.child("BANKACCTFROM") ?: return null
        val number = from.value("ACCTID") ?: return null
        val type = from.value("ACCTTYPE")?.uppercase()
        // A credit line reports what's owed as a negative balance, like a card.
        val owed = type == "CREDITLINE"
        val ledger = node.child("LEDGERBAL")
        val ledgerAmount = ledger?.value("BALAMT")?.let(::amount)
        return Statement(
            kind = StatementKind.BANK,
            accountNumber = number,
            bankAccountType = type,
            institution = institution,
            institutionId = from.value("BANKID") ?: institutionId,
            currency = node.value("CURDEF") ?: "USD",
            balance = ledgerAmount?.let { if (owed) -it else it },
            balanceDate = ledger?.value("DTASOF")?.let(::date),
            available = if (owed) null else node.child("AVAILBAL")?.value("BALAMT")?.let(::amount),
            transactions = transactions(node.child("BANKTRANLIST")),
            holdings = emptyList(),
        )
    }

    private fun cardStatement(node: Node, institution: String?, institutionId: String?): Statement? {
        val number = node.child("CCACCTFROM")?.value("ACCTID") ?: return null
        val ledger = node.child("LEDGERBAL")
        return Statement(
            kind = StatementKind.CREDIT_CARD,
            accountNumber = number,
            bankAccountType = null,
            institution = institution,
            institutionId = institutionId,
            currency = node.value("CURDEF") ?: "USD",
            // Cards report the amount owed as a negative balance.
            balance = ledger?.value("BALAMT")?.let(::amount)?.let { -it },
            balanceDate = ledger?.value("DTASOF")?.let(::date),
            available = null,
            transactions = transactions(node.child("BANKTRANLIST")),
            holdings = emptyList(),
        )
    }

    private fun investmentStatement(node: Node, institution: String?, institutionId: String?, securities: Map<String, Pair<String, String?>>): Statement? {
        val from = node.child("INVACCTFROM") ?: return null
        val number = from.value("ACCTID") ?: return null
        val holdings = node.child("INVPOSLIST")?.children.orEmpty().mapNotNull { position ->
            val pos = position.child("INVPOS") ?: return@mapNotNull null
            val id = pos.child("SECID")?.value("UNIQUEID") ?: return@mapNotNull null
            val value = pos.value("MKTVAL")?.let(::amount) ?: return@mapNotNull null
            val (name, ticker) = securities[id] ?: ((pos.value("MEMO") ?: id) to null)
            StatementHolding(id, name, ticker, pos.value("UNITS")?.toDoubleOrNull() ?: 0.0, value)
        }
        val cash = node.child("INVBAL")?.value("AVAILCASH")?.let(::amount) ?: 0
        val withCash = if (cash != 0L) holdings + StatementHolding("CASH", "Cash", null, 0.0, cash) else holdings
        val total = if (withCash.isEmpty()) null else withCash.sumOf { it.value }
        // Only cash moving in and out of the account belongs in spending and income; buys and sells stay inside it.
        val cashTransactions = node.child("INVTRANLIST")?.children.orEmpty()
            .filter { it.name == "INVBANKTRAN" }
            .flatMap { transactions(it) }
        return Statement(
            kind = StatementKind.INVESTMENT,
            accountNumber = number,
            bankAccountType = null,
            institution = institution ?: from.value("BROKERID"),
            institutionId = from.value("BROKERID") ?: institutionId,
            currency = node.value("CURDEF") ?: "USD",
            balance = total,
            balanceDate = node.value("DTASOF")?.let(::date),
            available = null,
            transactions = cashTransactions,
            holdings = withCash,
        )
    }

    private fun transactions(list: Node?): List<StatementTransaction> =
        list?.children.orEmpty().filter { it.name == "STMTTRN" }.mapNotNull { trn ->
            val date = (trn.value("DTPOSTED") ?: trn.value("DTUSER"))?.let(::date) ?: return@mapNotNull null
            val amount = trn.value("TRNAMT")?.let(::amount) ?: return@mapNotNull null
            val description = listOf(trn.value("NAME"), trn.child("PAYEE")?.value("NAME"), trn.value("MEMO"), trn.value("TRNTYPE"))
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                ?: "Transaction"
            StatementTransaction(trn.value("FITID")?.trim()?.ifEmpty { null }, date, amount, description)
        }

    /** Security id to (name, ticker) from the file's security list. */
    private fun securityInfo(root: Node): Map<String, Pair<String, String?>> =
        root.findAll("SECINFO").mapNotNull { info ->
            val id = info.child("SECID")?.value("UNIQUEID") ?: return@mapNotNull null
            val ticker = info.value("TICKER")?.ifBlank { null }
            id to ((info.value("SECNAME") ?: ticker ?: id) to ticker)
        }.toMap()

    private val compactDate = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** OFX dates look like 20240115, 20240115120000 or 20240115120000.000[-5:EST]. */
    internal fun date(value: String): LocalDate? {
        val digits = value.trim().take(8)
        if (digits.length != 8 || !digits.all(Char::isDigit)) return null
        return runCatching { LocalDate.parse(digits, compactDate) }.getOrNull()
    }

    /** Amounts use a dot or, from some institutions, a comma as the decimal separator. */
    internal fun amount(value: String): Long? {
        val trimmed = value.trim()
        val normalized = if (trimmed.contains(',') && !trimmed.contains('.')) trimmed.replace(',', '.') else trimmed.replace(",", "")
        return Money.parse(normalized)
    }

    // A small tolerant parser: elements with a value and no closing tag (SGML) are closed automatically.

    private class Node(val name: String) {
        var text: String? = null
        val children = mutableListOf<Node>()

        fun child(name: String): Node? = children.firstOrNull { it.name == name }
        fun value(name: String): String? = child(name)?.text

        fun find(name: String): Node? {
            for (c in children) {
                if (c.name == name) return c
                c.find(name)?.let { return it }
            }
            return null
        }

        fun findAll(name: String): List<Node> = children.flatMap { c -> (if (c.name == name) listOf(c) else emptyList()) + c.findAll(name) }
    }

    private val tag = Regex("<(/?)([A-Za-z0-9._]+)[^>]*>")

    private fun parseTree(text: String): Node {
        val matches = tag.findAll(text).toList()
        // In SGML files only groups have closing tags, so a name that is never closed is always a single value.
        val closedNames = matches.filter { it.groupValues[1] == "/" }.map { it.groupValues[2].uppercase() }.toSet()
        val root = Node("#root")
        val stack = ArrayDeque<Node>().apply { addLast(root) }
        var position = 0
        var lastOpened: Node? = null

        fun handleText(raw: String) {
            val value = decode(raw).trim()
            val open = lastOpened
            if (value.isEmpty() || open == null) return
            // A value right after an opening tag makes it a leaf, closed or not.
            open.text = value
            if (stack.lastOrNull() === open) stack.removeLast()
            lastOpened = null
        }

        for (match in matches) {
            handleText(text.substring(position, match.range.first))
            position = match.range.last + 1
            val closing = match.groupValues[1] == "/"
            val name = match.groupValues[2].uppercase()
            if (closing) {
                lastOpened = null
                if (stack.drop(1).any { it.name == name }) {
                    while (stack.size > 1) {
                        if (stack.removeLast().name == name) break
                    }
                }
            } else {
                val node = Node(name)
                stack.last().children += node
                val selfClosing = match.value.endsWith("/>")
                if (!selfClosing && name in closedNames) stack.addLast(node)
                lastOpened = if (selfClosing) null else node
            }
        }
        handleText(text.substring(position))
        return root
    }

    private fun decode(value: String): String =
        value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&nbsp;", " ").replace("&amp;", "&")
}
