package com.nexusbudget.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE connectionId = :connectionId")
    suspend fun forConnection(connectionId: String): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts WHERE connectionId = :connectionId")
    suspend fun deleteForConnection(connectionId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date >= :sinceEpochDay ORDER BY date DESC, id")
    fun observeSince(sinceEpochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :sinceEpochDay")
    suspend fun since(sinceEpochDay: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userCategorized = 1")
    suspend fun userCategorized(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id AND userCategorized = 0")
    suspend fun setAutoCategory(id: String, categoryId: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("SELECT id FROM transactions WHERE accountId IN (:accountIds) AND pending = 1 AND date >= :sinceEpochDay")
    suspend fun pendingIds(accountIds: List<String>, sinceEpochDay: Long): List<String>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(category: CategoryEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Upsert
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdOn")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY pattern")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY date")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Upsert
    suspend fun upsert(snapshot: SnapshotEntity)

    @Upsert
    suspend fun upsertAll(snapshots: List<SnapshotEntity>)
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings WHERE accountId = :accountId ORDER BY value DESC")
    fun observeForAccount(accountId: String): Flow<List<HoldingEntity>>

    @Query("DELETE FROM holdings WHERE accountId IN (:accountIds)")
    suspend fun deleteForAccounts(accountIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holdings: List<HoldingEntity>)
}

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY createdAt")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections ORDER BY createdAt")
    suspend fun all(): List<ConnectionEntity>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun get(id: String): ConnectionEntity?

    @Upsert
    suspend fun upsert(connection: ConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY id")
    suspend fun all(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET proposalState = :state WHERE id = :id")
    suspend fun setProposalState(id: Long, state: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
