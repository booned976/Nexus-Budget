package com.nexusbudget.core.model

import java.time.Instant

enum class AccountGroup(val label: String, val isLiability: Boolean) {
    CASH("Cash", false),
    CREDIT("Credit cards", true),
    INVESTMENTS("Investments", false),
    LOANS("Loans", true),
    PROPERTY("Property & other", false),
}

enum class AccountType(val label: String, val group: AccountGroup) {
    CHECKING("Checking", AccountGroup.CASH),
    SAVINGS("Savings", AccountGroup.CASH),
    CASH("Cash", AccountGroup.CASH),
    CREDIT_CARD("Credit card", AccountGroup.CREDIT),
    LINE_OF_CREDIT("Line of credit", AccountGroup.CREDIT),
    BROKERAGE("Brokerage", AccountGroup.INVESTMENTS),
    RETIREMENT("Retirement", AccountGroup.INVESTMENTS),
    HSA("Health savings", AccountGroup.INVESTMENTS),
    CRYPTO("Crypto", AccountGroup.INVESTMENTS),
    STUDENT_LOAN("Student loan", AccountGroup.LOANS),
    AUTO_LOAN("Auto loan", AccountGroup.LOANS),
    MORTGAGE("Mortgage", AccountGroup.LOANS),
    PERSONAL_LOAN("Personal loan", AccountGroup.LOANS),
    OTHER_LOAN("Other loan", AccountGroup.LOANS),
    PROPERTY("Property", AccountGroup.PROPERTY),
    OTHER_ASSET("Other asset", AccountGroup.PROPERTY),
    ;

    val isLiability: Boolean get() = group.isLiability

    /** Money you can spend today without moving it first. */
    val isSpendable: Boolean get() = this == CHECKING || this == CASH

    /** Money that can be reached within a few days (used for emergency fund coverage). */
    val isLiquid: Boolean get() = this == CHECKING || this == SAVINGS || this == CASH

    /** Debts that usually carry a revolving, high interest rate. */
    val isRevolving: Boolean get() = this == CREDIT_CARD || this == LINE_OF_CREDIT
}

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    /** Current balance. Assets are positive; for debts this is the positive amount owed. */
    val balance: Long,
    val institution: String? = null,
    val available: Long? = null,
    val creditLimit: Long? = null,
    val currency: String = "USD",
    val mask: String? = null,
    /** Null for accounts the user tracks by hand. */
    val connectionId: String? = null,
    /** Annual percentage rate for debts, e.g. 24.99. */
    val apr: Double? = null,
    val minimumPayment: Long? = null,
    val paymentDueDay: Int? = null,
    val isHidden: Boolean = false,
    val includeInNetWorth: Boolean = true,
    val lastUpdated: Instant? = null,
) {
    val isManual: Boolean get() = connectionId == null

    /** Contribution to net worth: positive for assets, negative for debts. */
    val signedBalance: Long get() = if (type.isLiability) -balance else balance

    /** Balance you can actually spend for cash accounts (available if the bank reports it). */
    val spendableBalance: Long get() = available ?: balance

    val displayName: String get() = if (mask.isNullOrBlank()) name else "$name ••$mask"

    /** Credit utilization in the range 0.0-1.0+, or null when there is no limit. */
    val utilization: Double?
        get() = if (type.isRevolving && creditLimit != null && creditLimit > 0) balance.toDouble() / creditLimit else null
}

data class Holding(
    val accountId: String,
    val securityId: String,
    val name: String,
    val ticker: String?,
    val quantity: Double,
    val value: Long,
    val costBasis: Long?,
) {
    val gain: Long? get() = costBasis?.let { value - it }
}
