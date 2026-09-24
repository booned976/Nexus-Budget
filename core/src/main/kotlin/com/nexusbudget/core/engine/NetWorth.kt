package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountGroup
import com.nexusbudget.core.model.NetWorthSnapshot
import java.time.LocalDate

data class NetWorth(
    val assets: Long,
    val liabilities: Long,
    val byGroup: Map<AccountGroup, Long>,
) {
    val total: Long get() = assets - liabilities
}

object NetWorthCalculator {

    fun calculate(accounts: List<Account>): NetWorth {
        val counted = accounts.filter { it.includeInNetWorth && !it.isHidden }
        val assets = counted.filter { !it.type.isLiability }.sumOf { it.balance }
        val liabilities = counted.filter { it.type.isLiability }.sumOf { it.balance }
        val byGroup = counted.groupBy { it.type.group }.mapValues { (_, list) -> list.sumOf { it.balance } }
        return NetWorth(assets, liabilities, byGroup)
    }

    fun snapshot(accounts: List<Account>, date: LocalDate): NetWorthSnapshot {
        val worth = calculate(accounts)
        return NetWorthSnapshot(date, worth.assets, worth.liabilities)
    }

    /** Change in net worth over roughly [days] days, using the closest snapshot on or before that date. */
    fun change(history: List<NetWorthSnapshot>, current: Long, today: LocalDate, days: Long = 30): Long? {
        val cutoff = today.minusDays(days)
        val base = history.filter { !it.date.isAfter(cutoff) }.maxByOrNull { it.date }
            ?: history.minByOrNull { it.date }?.takeIf { it.date.isBefore(today) }
            ?: return null
        return current - base.netWorth
    }
}
