package com.nexusbudget.connectors.plaid

import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Categories

/** Converts Plaid's account types and transaction categories into the app's own. */
object PlaidMapping {

    private val retirementSubtypes = setOf(
        "401a", "401k", "403b", "457b", "ira", "roth", "roth 401k", "roth 403b", "roth 457b", "sep ira", "simple ira",
        "retirement", "pension", "keogh", "thrift savings plan", "roth thrift savings plan", "profit sharing plan",
        "roth profit sharing plan", "sarsep", "rrsp", "rrif", "tfsa", "lira", "lif", "prif", "rlif", "lrif", "lrsp",
        "sipp", "roth pension", "fixed annuity", "variable annuity", "other annuity",
    )

    fun accountType(type: String, subtype: String?): AccountType {
        val sub = subtype?.lowercase().orEmpty()
        return when (type.lowercase()) {
            "depository" -> when (sub) {
                "savings", "money market", "cd", "cash management", "cash isa", "isa", "education savings account" -> AccountType.SAVINGS
                "hsa" -> AccountType.HSA
                else -> AccountType.CHECKING
            }
            "credit" -> AccountType.CREDIT_CARD
            "loan" -> when (sub) {
                "student" -> AccountType.STUDENT_LOAN
                "auto" -> AccountType.AUTO_LOAN
                "mortgage", "home equity", "home equity loan" -> AccountType.MORTGAGE
                "line of credit", "overdraft" -> AccountType.LINE_OF_CREDIT
                "consumer", "loan", "installment" -> AccountType.PERSONAL_LOAN
                else -> AccountType.OTHER_LOAN
            }
            "investment", "brokerage" -> when {
                sub in retirementSubtypes -> AccountType.RETIREMENT
                sub == "hsa" -> AccountType.HSA
                sub == "crypto exchange" || sub == "non-custodial wallet" -> AccountType.CRYPTO
                else -> AccountType.BROKERAGE
            }
            else -> AccountType.OTHER_ASSET
        }
    }

    /** Maps Plaid's personal finance category (primary + detailed) to a core category id. */
    fun category(primary: String?, detailed: String?): String? {
        val p = primary?.uppercase() ?: return null
        val d = detailed?.uppercase().orEmpty()
        return when {
            p == "INCOME" -> when {
                d.contains("WAGES") || d.contains("SALARY") -> Categories.PAYCHECK
                d.contains("INTEREST") || d.contains("DIVIDEND") -> Categories.INTEREST_INCOME
                else -> Categories.OTHER_INCOME
            }
            p == "TRANSFER_IN" || p == "TRANSFER_OUT" -> when {
                d.contains("INVESTMENT") || d.contains("RETIREMENT") || d.contains("SAVINGS") -> Categories.SAVINGS_TRANSFER
                d.contains("WITHDRAWAL") && p == "TRANSFER_OUT" -> Categories.CASH_ATM
                else -> Categories.TRANSFER
            }
            p == "LOAN_PAYMENTS" -> when {
                d.contains("CREDIT_CARD") -> Categories.CREDIT_CARD_PAYMENT
                d.contains("MORTGAGE") -> Categories.RENT
                else -> Categories.LOAN_PAYMENT
            }
            p == "BANK_FEES" -> Categories.FEES
            p == "ENTERTAINMENT" -> when {
                d.contains("TV_AND_MOVIES") || d.contains("MUSIC_AND_AUDIO") -> Categories.SUBSCRIPTIONS
                else -> Categories.ENTERTAINMENT
            }
            p == "FOOD_AND_DRINK" -> when {
                d.contains("GROCERIES") -> Categories.GROCERIES
                d.contains("COFFEE") -> Categories.COFFEE
                else -> Categories.RESTAURANTS
            }
            p == "GENERAL_MERCHANDISE" -> when {
                d.contains("CLOTHING") -> Categories.CLOTHING
                d.contains("PET") -> Categories.PETS
                else -> Categories.SHOPPING
            }
            p == "HOME_IMPROVEMENT" -> Categories.HOME
            p == "MEDICAL" -> Categories.MEDICAL
            p == "PERSONAL_CARE" -> when {
                d.contains("GYMS") || d.contains("FITNESS") -> Categories.FITNESS
                else -> Categories.PERSONAL_CARE
            }
            p == "GENERAL_SERVICES" -> when {
                d.contains("INSURANCE") -> Categories.INSURANCE
                d.contains("EDUCATION") -> Categories.EDUCATION
                d.contains("CHILDCARE") -> Categories.KIDS
                d.contains("AUTOMOTIVE") -> Categories.CAR
                d.contains("TELECOMMUNICATION") || d.contains("INTERNET") -> Categories.INTERNET_PHONE
                else -> Categories.SHOPPING
            }
            p == "GOVERNMENT_AND_NON_PROFIT" -> when {
                d.contains("DONATIONS") -> Categories.GIFTS
                else -> Categories.TAXES
            }
            p == "TRANSPORTATION" -> when {
                d.contains("GAS") -> Categories.GAS
                else -> Categories.TRANSIT
            }
            p == "TRAVEL" -> Categories.TRAVEL
            p == "RENT_AND_UTILITIES" -> when {
                d.contains("RENT") -> Categories.RENT
                d.contains("TELEPHONE") || d.contains("INTERNET") || d.contains("CABLE") -> Categories.INTERNET_PHONE
                else -> Categories.UTILITIES
            }
            else -> null
        }
    }
}
