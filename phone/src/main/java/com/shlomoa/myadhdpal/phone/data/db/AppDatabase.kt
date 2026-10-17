package com.shlomoa.myadhdpal.phone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
        const val VERSION = 2
        const val NAME = "my-adhd-pal.db"

        /** 1 to 2 (protocol 1.2): alert level and episode in the history; old alerts are level 1, no episode. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE history ADD COLUMN level INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE history ADD COLUMN episodeId TEXT")
            }
        }

        /** One entry per version step, in order. */
        val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2)

        fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
            .addMigrations(*MIGRATIONS.toTypedArray())
            .build()
    }
}
