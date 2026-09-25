package com.nexusbudget.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        RuleEntity::class,
        SnapshotEntity::class,
        HoldingEntity::class,
        ConnectionEntity::class,
        ChatMessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao
    abstract fun rules(): RuleDao
    abstract fun snapshots(): SnapshotDao
    abstract fun holdings(): HoldingDao
    abstract fun connections(): ConnectionDao
    abstract fun chat(): ChatDao

    companion object {
        fun build(context: Context): NexusDatabase =
            Room.databaseBuilder(context, NexusDatabase::class.java, "nexus-budget.db").build()
    }
}
