package com.shlomoa.myadhdpal.phone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The phone's database. Migration policy (docs/data-layer.md): the schema is exported to `phone/schemas` and committed;
 * every version bump adds a `Migration(from, to)` to [MIGRATIONS] and a migration test; migrations are never
 * destructive (no `fallbackToDestructiveMigration`), because history and tasks are the user's data.
 */
@Database(
    entities = [TaskEntity::class, ReminderEntity::class, PromptEntity::class, HistoryEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun reminderDao(): ReminderDao

    abstract fun promptDao(): PromptDao

    abstract fun historyDao(): HistoryDao

    companion object {
        const val VERSION = 1
        const val NAME = "my-adhd-pal.db"

        /** One entry per version step, in order. Empty while the schema is at version 1. */
        val MIGRATIONS: List<Migration> = emptyList()

        fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
            .addMigrations(*MIGRATIONS.toTypedArray())
            .build()
    }
}
