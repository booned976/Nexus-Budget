package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import java.time.YearMonth
import kotlin.math.roundToLong

data class Debt(
    val id: String,
    val name: String,
    val balance: Long,
    /** Annual percentage rate, e.g. 22.9. */
    val apr: Double,
    val minimumPayment: Long,
    val type: AccountType = AccountType.OTHER_LOAN,
    /** True when the minimum payment was estimated because the lender didn't report one. */
    val minimumEstimated: Boolean = false,
) {
    val monthlyInterest: Long get() = (balance * apr / 100.0 / 12.0).roundToLong()

    companion object {
        /** Builds payoff inputs from debt accounts, estimating missing minimum payments. */
        fun fromAccounts(accounts: List<Account>): List<Debt> = accounts
            .filter { it.type.isLiability && it.balance > 0 && !it.isHidden }
            .map { account ->
                val apr = account.apr ?: defaultApr(account.type)
                val estimated = account.minimumPayment == null || account.minimumPayment <= 0
                val minimum = if (estimated) estimateMinimum(account.balance, apr, account.type) else account.minimumPayment!!
                Debt(account.id, account.name, account.balance, apr, minimum, account.type, estimated)
            }

        fun defaultApr(type: AccountType): Double = when (type) {
            AccountType.CREDIT_CARD, AccountType.LINE_OF_CREDIT -> 22.0
            AccountType.STUDENT_LOAN -> 6.0
            AccountType.AUTO_LOAN -> 7.5
            AccountType.MORTGAGE -> 6.5
            AccountType.PERSONAL_LOAN -> 12.0
            else -> 10.0
        }

        /**
         * Typical minimums: revolving credit charges interest + 1% of the balance (at least $25);
         * installment loans are approximated as a 10-year amortization.
         */
        fun estimateMinimum(balance: Long, apr: Double, type: AccountType): Long {
            if (balance <= 0) return 0
            val monthlyRate = apr / 100.0 / 12.0
            val estimate = if (type.isRevolving) {
                (balance * monthlyRate + balance * 0.01).roundToLong().coerceAtLeast(25_00)
            } else {
                val months = if (type == AccountType.MORTGAGE) 360 else 120
                if (monthlyRate == 0.0) balance / months else (balance * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months.toDouble()))).roundToLong()
            }
            return estimate.coerceAtMost(balance)
        }
    }
}

enum class PayoffStrategy(val label: String, val description: String) {
    AVALANCHE("Avalanche", "Pay the highest interest rate first. Saves the most money."),
    SNOWBALL("Snowball", "Pay the smallest balance first. Quick wins keep you motivated."),
    CUSTOM("Custom", "Pay debts in the order you choose."),
    MINIMUM_ONLY("Minimums only", "Keep paying just the minimums."),
}

data class DebtPayoffResult(
    val debt: Debt,
    val order: Int,
    /** Months from the start until this debt is paid off, or null if it never is. */
    val payoffMonths: Int?,
    val payoffMonth: YearMonth?,
    val interestPaid: Long,
    val totalPaid: Long,
)

data class PaymentAllocation(val debtId: String, val debtName: String, val amount: Long, val isExtra: Boolean)

data class PayoffPlan(
    val strategy: PayoffStrategy,
    val extraMonthly: Long,
    val monthlyPayment: Long,
    val startMonth: YearMonth,
    val months: Int,
    val totalInterest: Long,
    val totalPaid: Long,
    /** False if the payments can't keep up with interest, or payoff takes longer than the simulation limit. */
    val feasible: Boolean,
    val debts: List<DebtPayoffResult>,
    /** Total balance remaining at the start and at the end of each month. */
    val balanceTimeline: List<Long>,
    /** What to pay each debt this month. */
    val firstMonthPayments: List<PaymentAllocation>,
) {
    val debtFreeMonth: YearMonth? get() = if (feasible) startMonth.plusMonths(maxOf(months, 1).toLong() - 1) else null
    val startingBalance: Long get() = balanceTimeline.firstOrNull() ?: 0
}

data class PayoffComparison(
    val minimumOnly: PayoffPlan,
    val avalanche: PayoffPlan,
    val snowball: PayoffPlan,
) {
    fun plan(strategy: PayoffStrategy): PayoffPlan = when (strategy) {
        PayoffStrategy.AVALANCHE, PayoffStrategy.CUSTOM -> avalanche
        PayoffStrategy.SNOWBALL -> snowball
        PayoffStrategy.MINIMUM_ONLY -> minimumOnly
    }

    fun interestSaved(plan: PayoffPlan): Long? =
        if (minimumOnly.feasible && plan.feasible) minimumOnly.totalInterest - plan.totalInterest else null

    fun monthsSaved(plan: PayoffPlan): Int? =
        if (minimumOnly.feasible && plan.feasible) minimumOnly.months - plan.months else null
}

object DebtPayoffEngine {

    const val MAX_MONTHS = 600

    fun order(debts: List<Debt>, strategy: PayoffStrategy, customOrder: List<String> = emptyList()): List<Debt> = when (strategy) {
        PayoffStrategy.AVALANCHE, PayoffStrategy.MINIMUM_ONLY -> debts.sortedWith(compareByDescending<Debt> { it.apr }.thenBy { it.balance })
        PayoffStrategy.SNOWBALL -> debts.sortedWith(compareBy<Debt> { it.balance }.thenByDescending { it.apr })
        PayoffStrategy.CUSTOM -> {
            val rank = customOrder.withIndex().associate { it.value to it.index }
            val avalanche = order(debts, PayoffStrategy.AVALANCHE)
            avalanche.sortedBy { rank[it.id] ?: (customOrder.size + avalanche.indexOf(it)) }
        }
    }

    /**
     * Month-by-month simulation. Interest accrues monthly on the balance, every open debt receives
     * its minimum, and the rest of the monthly budget goes to the priority debt. When a debt is paid
     * off its minimum "rolls over" to the next one, because the total monthly payment stays the same
     * (except for [PayoffStrategy.MINIMUM_ONLY], which never rolls payments over).
     */
    fun simulate(
        debts: List<Debt>,
        strategy: PayoffStrategy,
        extraMonthly: Long = 0,
        startMonth: YearMonth = YearMonth.now(),
        customOrder: List<String> = emptyList(),
        lumpSum: Long = 0,
    ): PayoffPlan {
        val ordered = order(debts.filter { it.balance > 0 }, strategy, customOrder)
        val n = ordered.size
        val balances = LongArray(n) { ordered[it].balance }
        val interest = LongArray(n)
        val paid = LongArray(n)
        val payoffMonth = arrayOfNulls<Int>(n)
        val rollover = strategy != PayoffStrategy.MINIMUM_ONLY
        val extra = if (rollover) extraMonthly.coerceAtLeast(0) else 0
        val budget = ordered.sumOf { it.minimumPayment } + extra
        val timeline = mutableListOf(balances.sum())
        val firstMonth = mutableListOf<PaymentAllocation>()
        var month = 0
        var stalled = 0

        // One-time payment applied before the first month, in priority order.
        var lump = if (rollover) lumpSum.coerceAtLeast(0) else 0
        for (i in 0 until n) {
            if (lump <= 0) break
            val pay = minOf(lump, balances[i])
            balances[i] -= pay
            paid[i] += pay
            lump -= pay
            if (pay > 0) firstMonth += PaymentAllocation(ordered[i].id, ordered[i].name, pay, true)
            if (balances[i] == 0L) payoffMonth[i] = 0
        }

        while (balances.any { it > 0 } && month < MAX_MONTHS) {
            month++
            val before = balances.sum()
            for (i in 0 until n) {
                if (balances[i] <= 0) continue
                val charge = (balances[i] * ordered[i].apr / 100.0 / 12.0).roundToLong()
                balances[i] += charge
                interest[i] += charge
            }
            var remaining = budget
            val allocations = LongArray(n)
            for (i in 0 until n) {
                if (balances[i] <= 0) continue
                val pay = minOf(ordered[i].minimumPayment, balances[i], if (rollover) remaining else ordered[i].minimumPayment)
                balances[i] -= pay
                allocations[i] += pay
                if (rollover) remaining -= pay
            }
            if (rollover) {
                for (i in 0 until n) {
                    if (remaining <= 0) break
                    if (balances[i] <= 0) continue
                    val pay = minOf(remaining, balances[i])
                    balances[i] -= pay
                    allocations[i] += pay
                    remaining -= pay
                }
            }
            for (i in 0 until n) {
                paid[i] += allocations[i]
                if (balances[i] <= 0 && payoffMonth[i] == null) payoffMonth[i] = month
            }
            if (month == 1) {
                for (i in 0 until n) {
                    if (allocations[i] > 0) {
                        firstMonth += PaymentAllocation(ordered[i].id, ordered[i].name, allocations[i], allocations[i] > ordered[i].minimumPayment)
                    }
                }
            }
            timeline += balances.sum()
            // Payments that never reduce the balance mean the plan can't finish.
            stalled = if (balances.sum() >= before) stalled + 1 else 0
            if (stalled >= 12) break
        }

        val feasible = balances.all { it <= 0 }
        val results = ordered.mapIndexed { i, debt ->
            DebtPayoffResult(
                debt = debt,
                order = i + 1,
                payoffMonths = payoffMonth[i],
                payoffMonth = payoffMonth[i]?.let { startMonth.plusMonths(maxOf(it, 1).toLong() - 1) },
                interestPaid = interest[i],
                totalPaid = paid[i],
            )
        }
        return PayoffPlan(
            strategy = strategy,
            extraMonthly = extra,
            monthlyPayment = budget,
            startMonth = startMonth,
            months = month,
            totalInterest = interest.sum(),
            totalPaid = paid.sum(),
            feasible = feasible,
            debts = results,
            balanceTimeline = timeline,
            firstMonthPayments = mergeAllocations(firstMonth),
        )
    }

    private fun mergeAllocations(list: List<PaymentAllocation>): List<PaymentAllocation> =
        list.groupBy { it.debtId }.map { (_, items) ->
            items.first().copy(amount = items.sumOf { it.amount }, isExtra = items.any { it.isExtra })
        }

    fun compare(debts: List<Debt>, extraMonthly: Long, startMonth: YearMonth = YearMonth.now()): PayoffComparison =
        PayoffComparison(
            minimumOnly = simulate(debts, PayoffStrategy.MINIMUM_ONLY, 0, startMonth),
            avalanche = simulate(debts, PayoffStrategy.AVALANCHE, extraMonthly, startMonth),
            snowball = simulate(debts, PayoffStrategy.SNOWBALL, extraMonthly, startMonth),
        )

    /** Smallest extra monthly payment (rounded up to $5) that makes the plan finish within [months]. */
    fun extraNeededFor(
        debts: List<Debt>,
        months: Int,
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
        startMonth: YearMonth = YearMonth.now(),
    ): Long? {
        if (debts.none { it.balance > 0 } || months <= 0) return null
        fun finishes(extra: Long): Boolean = simulate(debts, strategy, extra, startMonth).let { it.feasible && it.months <= months }
        if (finishes(0)) return 0
        var high = debts.sumOf { it.balance }
        if (!finishes(high)) return null
        var low = 0L
        while (high - low > 500) {
            val mid = (low + high) / 2
            if (finishes(mid)) high = mid else low = mid
        }
        return ((high + 499) / 500) * 500
    }
}
