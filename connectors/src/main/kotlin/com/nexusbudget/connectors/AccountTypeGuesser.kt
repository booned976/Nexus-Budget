package com.nexusbudget.connectors

import com.nexusbudget.core.model.AccountGroup
import com.nexusbudget.core.model.AccountType
import java.util.Locale

/**
 * Some providers only report an account's name and balance. This guesses the account type from
 * words in the name; users can always correct it in the app.
 */
object AccountTypeGuesser {

    private val rules: List<Pair<AccountType, List<String>>> = listOf(
        AccountType.STUDENT_LOAN to listOf("student", "stafford", "direct loan", "dept of ed", "education loan", "subsidized", "unsubsidized"),
        AccountType.MORTGAGE to listOf("mortgage", "home loan", "heloc", "home equity"),
        AccountType.AUTO_LOAN to listOf("auto loan", "car loan", "vehicle", "auto"),
        AccountType.LINE_OF_CREDIT to listOf("line of credit", "loc"),
        AccountType.PERSONAL_LOAN to listOf("personal loan", "installment"),
        AccountType.OTHER_LOAN to listOf("loan"),
        AccountType.CREDIT_CARD to listOf("credit card", "card", "visa", "mastercard", "rewards", "signature", "platinum"),
        AccountType.HSA to listOf("hsa", "health savings"),
        AccountType.RETIREMENT to listOf("401k", "401(k)", "403b", "403(b)", "457", "ira", "roth", "retirement", "pension", "sep", "tsp", "thrift"),
        AccountType.CRYPTO to listOf("crypto", "bitcoin", "btc", "ethereum", "wallet"),
        AccountType.BROKERAGE to listOf("brokerage", "invest", "individual", "joint", "trading", "stock", "margin", "portfolio", "529", "managed"),
        AccountType.SAVINGS to listOf("savings", "money market", "mmda", "cd", "certificate", "reserve"),
        AccountType.CHECKING to listOf("checking", "chk", "spending", "everyday", "debit", "share draft"),
    )

    private val patterns: List<Pair<AccountType, Regex>> = rules.map { (type, words) ->
        type to Regex(words.joinToString("|") { "(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])" })
    }

    fun guess(name: String, rawBalance: Long, hasHoldings: Boolean = false): AccountType {
        val lower = name.lowercase(Locale.US)
        if (hasHoldings) {
            return patterns.firstOrNull { (type, regex) -> type.group == AccountGroup.INVESTMENTS && regex.containsMatchIn(lower) }?.first
                ?: AccountType.BROKERAGE
        }
        patterns.firstOrNull { (_, regex) -> regex.containsMatchIn(lower) }?.let { return it.first }
        // An unrecognized account with a negative balance is most likely a card.
        return if (rawBalance < 0) AccountType.CREDIT_CARD else AccountType.CHECKING
    }
}
