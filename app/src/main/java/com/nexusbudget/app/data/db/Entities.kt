package com.nexusbudget.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [Index("connectionId")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val balance: Long,
    val institution: String?,
    val available: Long?,
    val creditLimit: Long?,
    val currency: String,
    val mask: String?,
    val connectionId: String?,
    val externalId: String?,
    val apr: Double?,
    val minimumPayment: Long?,
    val paymentDueDay: Int?,
    val isHidden: Boolean,
    val includeInNetWorth: Boolean,
    val lastUpdated: Long?,
    /** The user picked the type by hand, so syncs must not overwrite it. */
    val userEditedType: Boolean = false,
    /** The user entered debt details by hand; syncs only replace them with values the provider actually reports. */
    val userEditedDebtDetails: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("date"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    /** Epoch day. */
    val date: Long,
    val amount: Long,
    val description: String,
    val merchant: String,
    val categoryId: String?,
    val pending: Boolean,
    val notes: String?,
    val excluded: Boolean,
    val userCategorized: Boolean,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupName: String,
    val kind: String,
    val icon: String,
    val bucket: String,
    val isCustom: Boolean,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: String,
    val monthlyAmount: Long,
    val rollover: Boolean,
    /** YYYY-MM */
    val startMonth: String,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val target: Long,
    val saved: Long,
    val monthlyContribution: Long,
    val targetDate: Long?,
    val linkedAccountId: String?,
    val createdOn: Long,
    val archived: Boolean,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val categoryId: String,
    val renameTo: String?,
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    /** Epoch day. */
    @PrimaryKey val date: Long,
    val assets: Long,
    val liabilities: Long,
)

@Entity(tableName = "holdings", primaryKeys = ["accountId", "securityId"])
data class HoldingEntity(
    val accountId: String,
    val securityId: String,
    val name: String,
    val ticker: String?,
    val quantity: Double,
    val value: Long,
    val costBasis: Long?,
)

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    /** SIMPLEFIN or PLAID */
    val provider: String,
    val displayName: String,
    /** OK, NEEDS_REAUTH or ERROR */
    val status: String,
    val lastSync: Long?,
    val message: String?,
    val cursor: String?,
    /** Comma-separated Plaid products. */
    val products: String?,
    val createdAt: Long,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** USER or ASSISTANT */
    val role: String,
    val text: String,
    val createdAt: Long,
    /** A serialized proposal the user can apply, if the assistant made one. */
    val proposalJson: String? = null,
    /** PENDING, APPLIED or DISMISSED */
    val proposalState: String? = null,
    val isError: Boolean = false,
)
