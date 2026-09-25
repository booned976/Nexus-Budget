package com.nexusbudget.app.data

import com.nexusbudget.app.data.db.AccountEntity
import com.nexusbudget.app.data.db.BudgetEntity
import com.nexusbudget.app.data.db.CategoryEntity
import com.nexusbudget.app.data.db.GoalEntity
import com.nexusbudget.app.data.db.HoldingEntity
import com.nexusbudget.app.data.db.RuleEntity
import com.nexusbudget.app.data.db.SnapshotEntity
import com.nexusbudget.app.data.db.TransactionEntity
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.BudgetBucket
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.CategoryRule
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import com.nexusbudget.core.model.Holding
import com.nexusbudget.core.model.NetWorthSnapshot
import com.nexusbudget.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = enumOr(type, AccountType.CHECKING),
    balance = balance,
    institution = institution,
    available = available,
    creditLimit = creditLimit,
    currency = currency,
    mask = mask,
    connectionId = connectionId,
    apr = apr,
    minimumPayment = minimumPayment,
    paymentDueDay = paymentDueDay,
    isHidden = isHidden,
    includeInNetWorth = includeInNetWorth,
    lastUpdated = lastUpdated?.let(Instant::ofEpochMilli),
)

fun Account.toEntity(externalId: String? = null, userEditedType: Boolean = false, userEditedDebtDetails: Boolean = false) = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    balance = balance,
    institution = institution,
    available = available,
    creditLimit = creditLimit,
    currency = currency,
    mask = mask,
    connectionId = connectionId,
    externalId = externalId,
    apr = apr,
    minimumPayment = minimumPayment,
    paymentDueDay = paymentDueDay,
    isHidden = isHidden,
    includeInNetWorth = includeInNetWorth,
    lastUpdated = lastUpdated?.toEpochMilli(),
    userEditedType = userEditedType,
    userEditedDebtDetails = userEditedDebtDetails,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    accountId = accountId,
    date = LocalDate.ofEpochDay(date),
    amount = amount,
    description = description,
    merchant = merchant,
    categoryId = categoryId,
    pending = pending,
    notes = notes,
    excluded = excluded,
    userCategorized = userCategorized,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    accountId = accountId,
    date = date.toEpochDay(),
    amount = amount,
    description = description,
    merchant = merchant,
    categoryId = categoryId,
    pending = pending,
    notes = notes,
    excluded = excluded,
    userCategorized = userCategorized,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    group = groupName,
    kind = enumOr(kind, CategoryKind.EXPENSE),
    icon = icon,
    bucket = enumOr(bucket, BudgetBucket.WANTS),
    isCustom = isCustom,
)

fun Category.toEntity() = CategoryEntity(id, name, group, kind.name, icon, bucket.name, isCustom)

fun BudgetEntity.toDomain() = BudgetTarget(
    categoryId = categoryId,
    monthlyAmount = monthlyAmount,
    rollover = rollover,
    startMonth = runCatching { YearMonth.parse(startMonth) }.getOrDefault(YearMonth.now()),
)

fun BudgetTarget.toEntity() = BudgetEntity(categoryId, monthlyAmount, rollover, startMonth.toString())

fun GoalEntity.toDomain() = Goal(
    id = id,
    name = name,
    type = enumOr(type, GoalType.OTHER),
    target = target,
    saved = saved,
    monthlyContribution = monthlyContribution,
    targetDate = targetDate?.let(LocalDate::ofEpochDay),
    linkedAccountId = linkedAccountId,
    createdOn = LocalDate.ofEpochDay(createdOn),
    archived = archived,
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name,
    type = type.name,
    target = target,
    saved = saved,
    monthlyContribution = monthlyContribution,
    targetDate = targetDate?.toEpochDay(),
    linkedAccountId = linkedAccountId,
    createdOn = createdOn.toEpochDay(),
    archived = archived,
)

fun RuleEntity.toDomain() = CategoryRule(id, pattern, categoryId, renameTo)

fun SnapshotEntity.toDomain() = NetWorthSnapshot(LocalDate.ofEpochDay(date), assets, liabilities)

fun NetWorthSnapshot.toEntity() = SnapshotEntity(date.toEpochDay(), assets, liabilities)

fun HoldingEntity.toDomain() = Holding(accountId, securityId, name, ticker, quantity, value, costBasis)
