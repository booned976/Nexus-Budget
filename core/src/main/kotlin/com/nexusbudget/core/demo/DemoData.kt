package com.nexusbudget.core.demo

import com.nexusbudget.core.engine.Categorizer
import com.nexusbudget.core.engine.TransactionPipeline
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import com.nexusbudget.core.model.NetWorthSnapshot
import com.nexusbudget.core.model.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * A realistic, fictional household used for "Try the demo" so people can explore every screen
 * before connecting real accounts. All names are made up.
 */
object DemoData {

    data class Bundle(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val budgets: List<BudgetTarget>,
        val goals: List<Goal>,
        val snapshots: List<NetWorthSnapshot>,
    )

    const val CHECKING = "demo-checking"
    const val SAVINGS = "demo-savings"
    const val CARD = "demo-card"
    const val STORE_CARD = "demo-store-card"
    const val BROKERAGE = "demo-brokerage"
    const val RETIREMENT = "demo-401k"
    const val FEDERAL_LOAN = "demo-federal-loan"
    const val PRIVATE_LOAN = "demo-private-loan"
    const val AUTO_LOAN = "demo-auto-loan"

    fun generate(today: LocalDate = LocalDate.now(), seed: Int = 42): Bundle {
        val random = Random(seed)
        val accounts = listOf(
            Account(CHECKING, "Everyday Checking", AccountType.CHECKING, 3_184_27, institution = "Demo Bank", mask = "4821"),
            Account(SAVINGS, "High-Yield Savings", AccountType.SAVINGS, 6_240_00, institution = "Demo Bank", mask = "9134"),
            Account(CARD, "Rewards Card", AccountType.CREDIT_CARD, 2_386_45, institution = "Demo Bank", mask = "7702", creditLimit = 6_000_00, apr = 24.99, minimumPayment = 85_00, paymentDueDay = 27),
            Account(STORE_CARD, "Store Card", AccountType.CREDIT_CARD, 642_18, institution = "Northwind Outlet", mask = "0551", creditLimit = 1_500_00, apr = 29.99, minimumPayment = 35_00, paymentDueDay = 12),
            Account(BROKERAGE, "Individual Brokerage", AccountType.BROKERAGE, 18_452_91, institution = "Demo Investing", mask = "3310"),
            Account(RETIREMENT, "401(k)", AccountType.RETIREMENT, 32_118_40, institution = "Demo Investing"),
            Account(FEDERAL_LOAN, "Federal Student Loan", AccountType.STUDENT_LOAN, 21_450_00, institution = "Demo Loan Servicing", apr = 5.5, minimumPayment = 235_00, paymentDueDay = 15),
            Account(PRIVATE_LOAN, "Private Student Loan", AccountType.STUDENT_LOAN, 8_912_33, institution = "Summit Lending", apr = 8.9, minimumPayment = 120_00, paymentDueDay = 20),
            Account(AUTO_LOAN, "Auto Loan", AccountType.AUTO_LOAN, 12_304_76, institution = "Riverside Credit Union", apr = 6.9, minimumPayment = 365_00, paymentDueDay = 25),
        )

        val txns = mutableListOf<Transaction>()
        var counter = 0
        fun add(account: String, date: LocalDate, amount: Long, description: String, pending: Boolean = false) {
            if (date.isAfter(today)) return
            txns += Transaction("demo-txn-${counter++}", account, date, amount, description, pending = pending && date >= today.minusDays(2))
        }
        fun between(min: Long, max: Long) = min + random.nextLong(max - min + 1)

        val start = today.minusDays(160)
        val firstMonth = YearMonth.from(start)
        val months = generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(today)) }.toList()

        // Paychecks every other Friday.
        var payday = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        while (!payday.isAfter(today)) {
            add(CHECKING, payday, 2_150_00, "ACME CORP PAYROLL DIRECT DEP")
            payday = payday.plusWeeks(2)
        }

        for ((index, month) in months.withIndex()) {
            fun day(d: Int) = month.atDay(minOf(d, month.lengthOfMonth()))
            val isLatest = index == months.lastIndex
            add(CHECKING, day(1), -1_450_00, "OAKWOOD APARTMENTS RENT PMT")
            add(CHECKING, day(2), -200_00, "ONLINE TRANSFER TO SAVINGS 9134")
            add(SAVINGS, day(2), 200_00, "ONLINE TRANSFER FROM CHECKING 4821")
            add(CHECKING, day(3), -40_00, "IRONWORKS GYM MEMBERSHIP")
            add(CHECKING, day(8), -128_00, "SHIELD AUTO INSURANCE PREMIUM")
            add(CHECKING, day(12), -between(96_00, 148_00), "CITY POWER & LIGHT UTILITY")
            add(CHECKING, day(15), -235_00, "DEPT OF ED STUDENT LOAN PMT")
            add(FEDERAL_LOAN, day(15), 235_00, "PAYMENT RECEIVED")
            add(CHECKING, day(18), -65_00, "SKYLINE INTERNET SERVICE")
            add(CHECKING, day(20), -120_00, "SUMMIT LENDING STUDENT LN PMT")
            add(PRIVATE_LOAN, day(20), 120_00, "PAYMENT RECEIVED")
            add(CHECKING, day(22), -55_00, "METRO MOBILE WIRELESS BILL")
            add(CHECKING, day(25), -365_00, "RIVERSIDE CU AUTO LOAN PMT")
            add(AUTO_LOAN, day(25), 365_00, "PAYMENT RECEIVED")
            val cardPayment = between(420_00, 880_00)
            add(CHECKING, day(27), -cardPayment, "REWARDS CARD AUTOPAY PAYMENT")
            add(CARD, day(27), cardPayment, "PAYMENT THANK YOU")
            add(CHECKING, day(12), -60_00, "NORTHWIND STORE CARD EPAYMENT")
            add(STORE_CARD, day(12), 60_00, "PAYMENT THANK YOU")
            add(SAVINGS, day(month.lengthOfMonth()), between(17_00, 21_00), "INTEREST PAID")

            // Subscriptions on the rewards card. One of them raised its price two months ago.
            val streamingPrice = if (index >= months.size - 2) -15_49L else -13_99L
            add(CARD, day(5), streamingPrice, "STREAMFLIX SUBSCRIPTION")
            add(CARD, day(9), -2_99, "CLOUDBOX CLOUD STORAGE")
            add(CARD, day(11), -8_00, "DAILY LEDGER DIGITAL SUBSCRIPTION")
            add(CARD, day(14), -10_99, "TUNEWAVE MUSIC SUBSCRIPTION")
            add(STORE_CARD, day(between(3, 26).toInt()), -between(35_00, 115_00), "NORTHWIND OUTLET STORE #212")

            // Everyday spending. The current month runs hotter on restaurants to show pacing alerts.
            var date = month.atDay(1)
            while (!date.isAfter(month.atEndOfMonth()) && !date.isAfter(today)) {
                val dow = date.dayOfWeek
                if (random.nextInt(100) < 16) add(CHECKING, date, -between(58_00, 138_00), "GREENLEAF GROCERY #0412", pending = true)
                val restaurantOdds = if (isLatest) 48 else 30
                if (random.nextInt(100) < restaurantOdds) {
                    val place = listOf("CORNER BISTRO", "LUCKY NOODLE HOUSE", "SLICE PIZZERIA", "TACO GRANDE", "HARBOR GRILL").random(random)
                    add(CARD, date, -between(12_00, if (isLatest) 78_00 else 58_00), place, pending = true)
                }
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && random.nextInt(100) < 40) {
                    add(CARD, date, -between(4_50, 7_25), "BEAN THERE COFFEE")
                }
                if (random.nextInt(100) < 11) add(CARD, date, -between(38_00, 58_00), "QUICKFUEL GAS STATION")
                if (random.nextInt(100) < 12) {
                    val store = listOf("HOMEWARD HOME GOODS STORE", "BRIGHTLINE ELECTRONICS", "PAGE TURNER BOOKS", "THREADS APPAREL").random(random)
                    add(CARD, date, -between(18_00, 145_00), store)
                }
                if (random.nextInt(100) < 4) add(CARD, date, -between(22_00, 48_00), "GALAXY CINEMA TICKETS")
                if (random.nextInt(100) < 3) add(CHECKING, date, -between(15_00, 60_00), "WELLSPRING PHARMACY")
                if (random.nextInt(100) < 2) add(CARD, date, -between(25_00, 70_00), "GOLDEN SHEARS SALON")
                date = date.plusDays(1)
            }
        }
        add(CARD, today.minusDays(9), 34_99, "BRIGHTLINE ELECTRONICS REFUND")

        val sorted = txns.sortedByDescending { it.date }
        val processed = TransactionPipeline.process(sorted, emptyList(), accounts, Categorizer()).transactions

        val thisMonth = YearMonth.from(today)
        val budgetStart = thisMonth.minusMonths(3)
        val budgets = listOf(
            Categories.RENT to 1_450_00, Categories.GROCERIES to 460_00, Categories.RESTAURANTS to 260_00,
            Categories.COFFEE to 45_00, Categories.GAS to 180_00, Categories.UTILITIES to 150_00,
            Categories.INTERNET_PHONE to 120_00, Categories.INSURANCE to 130_00, Categories.SHOPPING to 220_00,
            Categories.SUBSCRIPTIONS to 40_00, Categories.ENTERTAINMENT to 50_00, Categories.FITNESS to 40_00,
            Categories.LOAN_PAYMENT to 720_00,
        ).map { (id, amount) -> BudgetTarget(id, amount.toLong(), rollover = id == Categories.SHOPPING, startMonth = budgetStart) }

        val goals = listOf(
            Goal("demo-goal-efund", "Emergency fund", GoalType.EMERGENCY_FUND, 12_000_00, 0, 300_00, linkedAccountId = SAVINGS, createdOn = today.minusMonths(4)),
            Goal("demo-goal-trip", "Summer trip", GoalType.TRAVEL, 2_500_00, 650_00, 150_00, targetDate = today.plusMonths(8).withDayOfMonth(1), createdOn = today.minusMonths(2)),
        )

        val assets = accounts.filter { !it.type.isLiability }.sumOf { it.balance }
        val liabilities = accounts.filter { it.type.isLiability }.sumOf { it.balance }
        val snapshots = (0..12).map { weeksAgo ->
            val drift = weeksAgo * 180_00L
            NetWorthSnapshot(today.minusWeeks(weeksAgo.toLong()), assets - drift + random.nextLong(-400_00, 400_00), liabilities + weeksAgo * 95_00L)
        }.sortedBy { it.date }

        return Bundle(accounts, processed, budgets, goals, snapshots)
    }
}
