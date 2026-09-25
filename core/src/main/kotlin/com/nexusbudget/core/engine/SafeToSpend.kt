package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.Goal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class UpcomingBill(
    val date: LocalDate,
    val name: String,
    /** Positive amount due. */
    val amount: Long,
    val series: RecurringSeries? = null,
    /** Set for debt payments that come from an account's due date rather than a detected series. */
    val accountId: String? = null,
)

data class SafeToSpend(
    /** Cash left after upcoming bills and goal savings until [until]. May be negative. */
    val amount: Long,
    val perDay: Long,
    val until: LocalDate,
    val untilPayday: Boolean,
    val spendableCash: Long,
    val upcomingBills: List<UpcomingBill>,
    val billsTotal: Long,
    val goalSetAside: Long,
    /** Days from today through [until], inclusive. */
    val daysRemaining: Int,
)

object SafeToSpendCalculator {

    /**
     * Safe to spend = spendable cash (checking + cash) − bills due before the next paycheck
     * (or month end) − the share of monthly goal contributions for that period.
     */
    fun calculate(
        accounts: List<Account>,
        recurring: List<RecurringSeries>,
        goals: List<Goal>,
        today: LocalDate,
    ): SafeToSpend {
        val spendable = accounts.filter { it.type.isSpendable && !it.isHidden }
        val spendableIds = spendable.map { it.id }.toSet()
        val spendableCash = spendable.sumOf { it.spendableBalance }

        val nextPayday = recurring
            .filter { it.isIncome && it.amount > 0 && it.accountId in spendableIds }
            .flatMap { it.occurrencesBetween(today.plusDays(1), today.plusDays(45)) }
            .minOrNull()
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val until = when {
            nextPayday != null -> nextPayday.minusDays(1)
            monthEnd.isAfter(today) -> monthEnd
            else -> today.plusMonths(1).withDayOfMonth(today.plusMonths(1).lengthOfMonth())
        }.let { if (it.isBefore(today)) today else it }

        val bills = billsBetween(accounts, recurring, today, until, spendableIds)
        val billsTotal = bills.sumOf { it.amount }

        val days = (ChronoUnit.DAYS.between(today, until) + 1).toInt().coerceAtLeast(1)
        val monthlyGoals = goals.filter { !it.archived }.sumOf { it.monthlyContribution }
        val goalSetAside = (monthlyGoals * days / 30.44).toLong().coerceAtMost(monthlyGoals)

        val amount = spendableCash - billsTotal - goalSetAside
        return SafeToSpend(
            amount = amount,
            perDay = (amount / days).coerceAtLeast(0),
            until = until,
            untilPayday = nextPayday != null,
            spendableCash = spendableCash,
            upcomingBills = bills,
            billsTotal = billsTotal,
            goalSetAside = goalSetAside,
            daysRemaining = days,
        )
    }

    /**
     * Bills due between [from] and [to]: detected recurring charges plus debt payments from
     * account due dates that weren't already detected as a recurring charge.
     *
     * @param payingAccountIds when set, only charges paid from these accounts are included
     *   (card purchases reach checking later through the card payment).
     */
    fun billsBetween(
        accounts: List<Account>,
        recurring: List<RecurringSeries>,
        from: LocalDate,
        to: LocalDate,
        payingAccountIds: Set<String>? = null,
    ): List<UpcomingBill> {
        val detected = recurring
            .filter { it.amount < 0 && (payingAccountIds == null || it.accountId in payingAccountIds) }
            .flatMap { series -> series.occurrencesBetween(from, to).map { UpcomingBill(it, series.merchant, -series.amount, series) } }
        val outgoing = recurring.filter { it.amount < 0 }

        val debtPayments = accounts
            .filter { it.type.isLiability && it.balance > 0 && it.paymentDueDay != null && !it.isHidden }
            .mapNotNull { account ->
                val minimum = account.minimumPayment?.takeIf { it > 0 }
                    ?: Debt.estimateMinimum(account.balance, account.apr ?: Debt.defaultApr(account.type), account.type)
                // Skip debts already paid by a detected recurring payment: same amount, or a
                // payment at least as large whose name matches the account or lender.
                val accountWords = significantWords(account.name) + significantWords(account.institution.orEmpty())
                val covered = outgoing.any { series ->
                    val paid = -series.amount
                    abs(paid - minimum) <= minimum / 20 ||
                        (paid >= minimum * 95 / 100 && significantWords(series.merchant).any { it in accountWords })
                }
                if (covered) return@mapNotNull null
                val due = nextDueDate(account.paymentDueDay!!, from)
                if (due.isAfter(to)) null else UpcomingBill(due, "${account.name} payment", minimum, accountId = account.id)
            }
        return (detected + debtPayments).sortedBy { it.date }
    }

    private val genericWords = setOf(
        "card", "credit", "loan", "loans", "payment", "pmt", "autopay", "epayment", "student", "auto", "the", "bank",
        "of", "and", "online", "bill", "servicing", "services", "lending", "financial", "federal", "private", "store",
    )

    private fun significantWords(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 && it !in genericWords }.toSet()

    fun nextDueDate(dayOfMonth: Int, from: LocalDate): LocalDate {
        val thisMonth = from.withDayOfMonth(minOf(dayOfMonth, from.lengthOfMonth()))
        if (!thisMonth.isBefore(from)) return thisMonth
        val next = from.plusMonths(1)
        return next.withDayOfMonth(minOf(dayOfMonth, next.lengthOfMonth()))
    }
}
