package com.nexusbudget.core.engine

import com.nexusbudget.core.model.AccountGroup
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryRule
import com.nexusbudget.core.model.Transaction
import java.util.Locale
import kotlin.math.abs

/**
 * Assigns categories in priority order:
 * 1. The user's own rules.
 * 2. What the user chose for the same merchant before (learned from history).
 * 3. A category hint from the data provider.
 * 4. Generic keyword rules.
 */
class Categorizer(
    private val rules: List<CategoryRule> = emptyList(),
    history: List<Transaction> = emptyList(),
) {
    private val learned: Map<String, String> = learnFrom(history)

    fun categorize(transaction: Transaction, providerHint: String? = null): String {
        val upper = transaction.description.uppercase(Locale.US)
        val merchantUpper = transaction.merchant.uppercase(Locale.US)
        rules.firstOrNull { rule ->
            val pattern = rule.pattern.trim().uppercase(Locale.US)
            pattern.isNotEmpty() && (upper.contains(pattern) || merchantUpper.contains(pattern))
        }?.let { return it.categoryId }

        learned[transaction.merchantKey]?.let { return it }
        providerHint?.let { return it }
        return keywordCategory(upper, transaction.amount)
    }

    /** Applies the user's rename rules to a merchant name, if any match. */
    fun rename(transaction: Transaction): String? {
        val upper = transaction.description.uppercase(Locale.US)
        return rules.firstOrNull { !it.renameTo.isNullOrBlank() && upper.contains(it.pattern.trim().uppercase(Locale.US)) }?.renameTo
    }

    companion object {
        private data class KeywordRule(val categoryId: String, val keywords: List<String>, val inflowOnly: Boolean = false, val outflowOnly: Boolean = false) {
            val regex = Regex("\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b")
        }

        // Ordered: earlier rules win. Keywords are generic words, never specific brands.
        private val keywordRules = listOf(
            KeywordRule(Categories.CREDIT_CARD_PAYMENT, listOf("PAYMENT THANK YOU", "THANK YOU FOR YOUR PAYMENT", "AUTOPAY PAYMENT", "CREDIT CARD PAYMENT", "CARD PAYMENT", "CRCARDPMT", "EPAYMENT", "CARDMEMBER SERV")),
            KeywordRule(Categories.LOAN_PAYMENT, listOf("STUDENT LOAN", "STUDENT LN", "LOAN PMT", "LOAN PAYMENT", "AUTO LOAN", "AUTO PAY LOAN", "DEPT OF ED", "DEPT EDUCATION", "DEPARTMENT OF EDUCATION", "LOAN SERVICING", "LOAN SERVICES", "MORTGAGE PMT"), outflowOnly = true),
            KeywordRule(Categories.SAVINGS_TRANSFER, listOf("BROKERAGE", "INVESTMENT", "IRA CONTRIB", "401K", "ROTH")),
            KeywordRule(Categories.TRANSFER, listOf("TRANSFER", "XFER", "TFR", "ONLINE BANKING TRANSFER", "INTERNAL TRANSFER")),
            KeywordRule(Categories.PAYCHECK, listOf("PAYROLL", "DIRECT DEP", "DIR DEP", "DIRECT DEPOSIT", "SALARY", "PAYCHECK", "WAGES"), inflowOnly = true),
            KeywordRule(Categories.INTEREST_INCOME, listOf("INTEREST PAID", "INTEREST EARNED", "INTEREST PAYMENT", "DIVIDEND", "DIV REINV"), inflowOnly = true),
            KeywordRule(Categories.REFUNDS, listOf("REFUND", "RETURN", "REVERSAL", "CASHBACK", "CASH BACK REWARD", "STATEMENT CREDIT"), inflowOnly = true),
            KeywordRule(Categories.FEES, listOf("OVERDRAFT", "NSF", "SERVICE CHARGE", "MONTHLY FEE", "ATM FEE", "LATE FEE", "FOREIGN TRANSACTION FEE", "INTEREST CHARGE", "FINANCE CHARGE", "ANNUAL FEE", "FEE")),
            KeywordRule(Categories.CASH_ATM, listOf("ATM", "CASH WITHDRAWAL", "WITHDRAWAL")),
            KeywordRule(Categories.TAXES, listOf("IRS", "US TREASURY TAX", "TAX PAYMENT", "STATE TAX", "FRANCHISE TAX", "PROPERTY TAX", "DMV")),
            KeywordRule(Categories.RENT, listOf("RENT", "APARTMENT", "APARTMENTS", "PROPERTY MGMT", "PROPERTY MANAGEMENT", "LEASING", "HOA", "MORTGAGE", "HOME LOAN")),
            KeywordRule(Categories.UTILITIES, listOf("ELECTRIC", "POWER", "ENERGY", "WATER", "SEWER", "UTILITY", "UTILITIES", "GAS & ELECTRIC", "TRASH", "WASTE")),
            KeywordRule(Categories.INTERNET_PHONE, listOf("INTERNET", "BROADBAND", "WIRELESS", "MOBILE", "TELECOM", "CABLE", "FIBER", "CELLULAR", "PHONE")),
            KeywordRule(Categories.INSURANCE, listOf("INSURANCE", "ASSURANCE", "INS PREM", "INSUR")),
            KeywordRule(Categories.GROCERIES, listOf("GROCERY", "GROCERIES", "GROCER", "SUPERMARKET", "MARKET", "FOODS", "FOOD STORE", "FARMERS", "BUTCHER")),
            KeywordRule(Categories.COFFEE, listOf("COFFEE", "ESPRESSO", "CAFE", "ROASTERS", "TEA HOUSE")),
            KeywordRule(Categories.RESTAURANTS, listOf("RESTAURANT", "BISTRO", "GRILL", "PIZZA", "PIZZERIA", "BURGER", "TACO", "SUSHI", "DINER", "KITCHEN", "EATERY", "BBQ", "BAKERY", "DELI", "NOODLE", "RAMEN", "WINGS", "STEAKHOUSE", "TAVERN", "PUB", "BREWING", "FOOD DELIVERY")),
            KeywordRule(Categories.GAS, listOf("GAS STATION", "FUEL", "PETRO", "PETROLEUM", "GAS", "GASOLINE", "EV CHARGING")),
            KeywordRule(Categories.TRANSIT, listOf("PARKING", "TRANSIT", "TOLL", "TOLLS", "RAIL", "BUS", "SUBWAY", "TAXI", "CAB", "RIDESHARE", "METRO")),
            KeywordRule(Categories.CAR, listOf("AUTO REPAIR", "AUTO PARTS", "TIRE", "TIRES", "OIL CHANGE", "CAR WASH", "AUTOMOTIVE", "MECHANIC", "SMOG")),
            KeywordRule(Categories.MEDICAL, listOf("PHARMACY", "DRUG", "MEDICAL", "CLINIC", "HOSPITAL", "DENTAL", "DENTIST", "VISION", "OPTICAL", "HEALTH", "DOCTOR", "LAB", "LABS", "URGENT CARE", "THERAPY")),
            KeywordRule(Categories.FITNESS, listOf("GYM", "FITNESS", "YOGA", "PILATES", "ATHLETIC CLUB", "CROSSFIT", "CLIMBING")),
            KeywordRule(Categories.SUBSCRIPTIONS, listOf("SUBSCRIPTION", "MEMBERSHIP", "STREAMING", "MONTHLY PLAN", "PREMIUM PLAN", "CLOUD STORAGE")),
            KeywordRule(Categories.ENTERTAINMENT, listOf("CINEMA", "THEATER", "THEATRE", "MOVIES", "TICKETS", "CONCERT", "GAMES", "GAMING", "BOWLING", "ARCADE", "MUSEUM", "ZOO", "MUSIC")),
            KeywordRule(Categories.TRAVEL, listOf("AIRLINE", "AIRLINES", "AIRWAYS", "AIRPORT", "HOTEL", "HOTELS", "RESORT", "MOTEL", "INN", "LODGING", "TRAVEL", "CAR RENTAL", "CRUISE", "VACATION")),
            KeywordRule(Categories.EDUCATION, listOf("TUITION", "UNIVERSITY", "COLLEGE", "SCHOOL", "BOOKSTORE", "COURSE", "ACADEMY", "TEXTBOOK")),
            KeywordRule(Categories.PERSONAL_CARE, listOf("SALON", "BARBER", "SPA", "NAIL", "NAILS", "BEAUTY", "COSMETICS", "MASSAGE")),
            KeywordRule(Categories.PETS, listOf("PET", "PETS", "VET", "VETERINARY", "ANIMAL HOSPITAL", "GROOMING")),
            KeywordRule(Categories.KIDS, listOf("DAYCARE", "CHILDCARE", "CHILD CARE", "TOYS", "TOY", "BABY", "PRESCHOOL")),
            KeywordRule(Categories.GIFTS, listOf("DONATION", "CHARITY", "FOUNDATION", "CHURCH", "GIFT", "GIFTS", "FLORIST", "FLOWERS")),
            KeywordRule(Categories.HOME, listOf("HARDWARE", "HOME IMPROVEMENT", "FURNITURE", "GARDEN", "NURSERY", "HOME GOODS", "LUMBER", "APPLIANCE")),
            KeywordRule(Categories.CLOTHING, listOf("APPAREL", "CLOTHING", "SHOES", "FASHION", "BOUTIQUE", "DENIM", "TAILOR")),
            KeywordRule(Categories.SHOPPING, listOf("STORE", "SHOP", "MART", "OUTLET", "DEPOT", "MALL", "RETAIL", "ONLINE ORDER", "MARKETPLACE", "WAREHOUSE", "ELECTRONICS", "BOOKS")),
        )

        fun keywordCategory(description: String, amount: Long): String {
            val upper = description.uppercase(Locale.US)
            for (rule in keywordRules) {
                if (rule.inflowOnly && amount <= 0) continue
                if (rule.outflowOnly && amount >= 0) continue
                if (rule.regex.containsMatchIn(upper)) return rule.categoryId
            }
            return if (amount > 0) Categories.OTHER_INCOME else Categories.UNCATEGORIZED
        }

        /** Most common user-confirmed category for each merchant. */
        fun learnFrom(history: List<Transaction>): Map<String, String> =
            history.asSequence()
                .filter { it.userCategorized && it.categoryId != null && it.categoryId != Categories.UNCATEGORIZED }
                .groupBy { it.merchantKey }
                .mapValues { (_, txns) -> txns.groupingBy { it.categoryId!! }.eachCount().maxBy { it.value }.key }

        /**
         * Finds pairs of opposite amounts between two of the user's accounts within a few days
         * (e.g. -500 from checking and +500 into savings) so they can be treated as transfers or
         * debt payments rather than spending and income. Returns (outflow, inflow) pairs.
         */
        fun findTransferPairs(transactions: List<Transaction>, windowDays: Long = 4): List<Pair<Transaction, Transaction>> {
            val used = mutableSetOf<String>()
            val pairs = mutableListOf<Pair<Transaction, Transaction>>()
            val inflowsByAmount = transactions.filter { it.amount > 0 && !it.userCategorized }.groupBy { it.amount }
            for (out in transactions.filter { it.amount < 0 && !it.userCategorized }.sortedBy { it.date }) {
                val match = inflowsByAmount[abs(out.amount)]?.firstOrNull { inflow ->
                    inflow.accountId != out.accountId &&
                        inflow.id !in used &&
                        abs(inflow.date.toEpochDay() - out.date.toEpochDay()) <= windowDays
                } ?: continue
                used += out.id
                used += match.id
                pairs += out to match
            }
            return pairs
        }

        /** Categories for both sides of a detected transfer, based on where the money went. */
        fun transferCategories(destination: AccountType): Pair<String, String> = when {
            destination.isRevolving -> Categories.CREDIT_CARD_PAYMENT to Categories.CREDIT_CARD_PAYMENT
            destination.group == AccountGroup.LOANS -> Categories.LOAN_PAYMENT to Categories.TRANSFER
            destination.group == AccountGroup.INVESTMENTS -> Categories.SAVINGS_TRANSFER to Categories.SAVINGS_TRANSFER
            destination == AccountType.SAVINGS -> Categories.SAVINGS_TRANSFER to Categories.SAVINGS_TRANSFER
            else -> Categories.TRANSFER to Categories.TRANSFER
        }
    }
}
