package com.nexusbudget.core.model

enum class CategoryKind { INCOME, EXPENSE, TRANSFER }

/** The 50/30/20 bucket a category belongs to: needs, wants, or savings and debt repayment. */
enum class BudgetBucket(val label: String, val targetShare: Double) {
    NEEDS("Needs", 0.50),
    WANTS("Wants", 0.30),
    SAVINGS("Savings & debt", 0.20),
}

data class Category(
    val id: String,
    val name: String,
    val group: String,
    val kind: CategoryKind,
    val icon: String,
    val bucket: BudgetBucket = BudgetBucket.WANTS,
    val isCustom: Boolean = false,
) {
    /** Essential spending counts toward emergency fund coverage. */
    val isEssential: Boolean get() = kind == CategoryKind.EXPENSE && bucket != BudgetBucket.WANTS
}

object Categories {
    const val PAYCHECK = "income.paycheck"
    const val OTHER_INCOME = "income.other"
    const val INTEREST_INCOME = "income.interest"
    const val REFUNDS = "income.refund"

    const val RENT = "housing.rent"
    const val UTILITIES = "housing.utilities"
    const val INTERNET_PHONE = "housing.internet"
    const val HOME = "housing.home"
    const val INSURANCE = "housing.insurance"

    const val GROCERIES = "food.groceries"
    const val RESTAURANTS = "food.restaurants"
    const val COFFEE = "food.coffee"

    const val GAS = "transport.gas"
    const val TRANSIT = "transport.transit"
    const val CAR = "transport.car"

    const val MEDICAL = "health.medical"
    const val FITNESS = "health.fitness"

    const val SHOPPING = "life.shopping"
    const val CLOTHING = "life.clothing"
    const val ENTERTAINMENT = "life.entertainment"
    const val SUBSCRIPTIONS = "life.subscriptions"
    const val TRAVEL = "life.travel"
    const val PERSONAL_CARE = "life.personal"
    const val GIFTS = "life.gifts"
    const val PETS = "life.pets"
    const val KIDS = "life.kids"
    const val EDUCATION = "life.education"

    const val LOAN_PAYMENT = "fin.loan"
    const val FEES = "fin.fees"
    const val TAXES = "fin.taxes"
    const val CASH_ATM = "fin.cash"

    const val TRANSFER = "transfer.internal"
    const val CREDIT_CARD_PAYMENT = "transfer.cc_payment"
    const val SAVINGS_TRANSFER = "transfer.savings"

    const val UNCATEGORIZED = "uncategorized"

    val defaults: List<Category> = listOf(
        Category(PAYCHECK, "Paycheck", "Income", CategoryKind.INCOME, "💼"),
        Category(OTHER_INCOME, "Other income", "Income", CategoryKind.INCOME, "💵"),
        Category(INTEREST_INCOME, "Interest & dividends", "Income", CategoryKind.INCOME, "📈"),
        Category(REFUNDS, "Refunds", "Income", CategoryKind.INCOME, "↩️"),

        Category(RENT, "Rent & mortgage", "Housing & bills", CategoryKind.EXPENSE, "🏠", BudgetBucket.NEEDS),
        Category(UTILITIES, "Utilities", "Housing & bills", CategoryKind.EXPENSE, "💡", BudgetBucket.NEEDS),
        Category(INTERNET_PHONE, "Internet & phone", "Housing & bills", CategoryKind.EXPENSE, "📶", BudgetBucket.NEEDS),
        Category(INSURANCE, "Insurance", "Housing & bills", CategoryKind.EXPENSE, "🛡️", BudgetBucket.NEEDS),
        Category(HOME, "Home & garden", "Housing & bills", CategoryKind.EXPENSE, "🛠️"),

        Category(GROCERIES, "Groceries", "Food", CategoryKind.EXPENSE, "🛒", BudgetBucket.NEEDS),
        Category(RESTAURANTS, "Restaurants & takeout", "Food", CategoryKind.EXPENSE, "🍽️"),
        Category(COFFEE, "Coffee", "Food", CategoryKind.EXPENSE, "☕"),

        Category(GAS, "Gas & fuel", "Transportation", CategoryKind.EXPENSE, "⛽", BudgetBucket.NEEDS),
        Category(TRANSIT, "Transit & parking", "Transportation", CategoryKind.EXPENSE, "🚇", BudgetBucket.NEEDS),
        Category(CAR, "Car care", "Transportation", CategoryKind.EXPENSE, "🚗", BudgetBucket.NEEDS),

        Category(MEDICAL, "Medical & pharmacy", "Health", CategoryKind.EXPENSE, "💊", BudgetBucket.NEEDS),
        Category(FITNESS, "Fitness", "Health", CategoryKind.EXPENSE, "🏋️"),

        Category(SHOPPING, "Shopping", "Lifestyle", CategoryKind.EXPENSE, "🛍️"),
        Category(CLOTHING, "Clothing", "Lifestyle", CategoryKind.EXPENSE, "👕"),
        Category(ENTERTAINMENT, "Entertainment", "Lifestyle", CategoryKind.EXPENSE, "🎬"),
        Category(SUBSCRIPTIONS, "Subscriptions", "Lifestyle", CategoryKind.EXPENSE, "🔁"),
        Category(TRAVEL, "Travel", "Lifestyle", CategoryKind.EXPENSE, "✈️"),
        Category(PERSONAL_CARE, "Personal care", "Lifestyle", CategoryKind.EXPENSE, "💇"),
        Category(GIFTS, "Gifts & donations", "Lifestyle", CategoryKind.EXPENSE, "🎁"),
        Category(PETS, "Pets", "Lifestyle", CategoryKind.EXPENSE, "🐾"),
        Category(KIDS, "Kids & childcare", "Lifestyle", CategoryKind.EXPENSE, "🧸", BudgetBucket.NEEDS),
        Category(EDUCATION, "Education", "Lifestyle", CategoryKind.EXPENSE, "🎓", BudgetBucket.NEEDS),

        Category(LOAN_PAYMENT, "Loan payments", "Financial", CategoryKind.EXPENSE, "🏦", BudgetBucket.SAVINGS),
        Category(FEES, "Fees & interest", "Financial", CategoryKind.EXPENSE, "🧾", BudgetBucket.NEEDS),
        Category(TAXES, "Taxes", "Financial", CategoryKind.EXPENSE, "🏛️", BudgetBucket.NEEDS),
        Category(CASH_ATM, "Cash & ATM", "Financial", CategoryKind.EXPENSE, "🏧"),

        Category(TRANSFER, "Transfers", "Transfers", CategoryKind.TRANSFER, "🔄"),
        Category(CREDIT_CARD_PAYMENT, "Credit card payment", "Transfers", CategoryKind.TRANSFER, "💳"),
        Category(SAVINGS_TRANSFER, "Savings & investing", "Transfers", CategoryKind.TRANSFER, "🐖", BudgetBucket.SAVINGS),

        Category(UNCATEGORIZED, "Uncategorized", "Other", CategoryKind.EXPENSE, "❓"),
    )

    private val byId = defaults.associateBy { it.id }

    fun default(id: String): Category? = byId[id]

    /** Categories whose fixed, repeating charges are treated as subscriptions. */
    val subscriptionLike: Set<String> = setOf(SUBSCRIPTIONS, ENTERTAINMENT, FITNESS)
}

/** Lookup helper that falls back to "Uncategorized" for unknown ids. */
class CategoryIndex(categories: Collection<Category>) {
    private val map: Map<String, Category> = categories.associateBy { it.id }
    val uncategorized: Category = map[Categories.UNCATEGORIZED] ?: Categories.default(Categories.UNCATEGORIZED)!!

    val all: Collection<Category> get() = map.values

    operator fun get(id: String?): Category = if (id == null) uncategorized else map[id] ?: uncategorized

    fun find(id: String?): Category? = id?.let { map[it] }

    /** Kind of a transaction, treating uncategorized inflows as income. */
    fun kindOf(transaction: Transaction): CategoryKind {
        val category = find(transaction.categoryId)
        return when {
            category != null && category.id != Categories.UNCATEGORIZED -> category.kind
            transaction.amount > 0 -> CategoryKind.INCOME
            else -> CategoryKind.EXPENSE
        }
    }
}
