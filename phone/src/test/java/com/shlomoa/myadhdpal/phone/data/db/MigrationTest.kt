package com.shlomoa.myadhdpal.phone.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TC-S-161: migration 1 to 2 adds the alert level and episode to the history and keeps every existing row. The old
 * database is built from the committed schema file `1.json` (its `createSql` statements and setup queries, which is
 * what Room's `MigrationTestHelper` does), then opened through Room, which runs the migration and validates the result
 * against the version 2 schema compiled into the app.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `TC-S-161 migrating 1 to 2 keeps the history and gives old alerts level 1 without an episode`() {
        context.deleteDatabase(DB)
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB), null)
        createFromSchema(old, 1)
        old.execSQL(
            "INSERT INTO history (id, type, at, metric, value, threshold) " +
                "VALUES ('a1', 'ALERT', 100, 'HEART_RATE', 150.0, 140.0)"
        )
        old.execSQL("INSERT INTO history (id, type, at, action) VALUES ('k1', 'ACK', 50, 'DONE')")
        old.version = 1
        old.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(*AppDatabase.MIGRATIONS.toTypedArray())
            .allowMainThreadQueries()
            .build()
        try {
            val history = runBlocking { db.historyDao().observe(null).first() }
            assertEquals(setOf("k1", "a1"), history.map { it.id }.toSet())
            val alert = history.single { it.id == "a1" }
            assertEquals(1, alert.level)
            assertEquals(null, alert.episodeId)
            assertEquals(100L, alert.at)
            assertEquals(1, history.single { it.id == "k1" }.level)
        } finally {
            db.close()
        }
    }

    @Test
    fun `TC-S-161 the current version is 2 and there is exactly the 1 to 2 step`() {
        assertEquals(2, AppDatabase.VERSION)
        assertEquals(listOf(1 to 2), AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion })
        assertTrue(schemaFile(2).isFile)
    }

    private fun schemaFile(version: Int) = File("schemas/${AppDatabase::class.java.name}/$version.json")

    private fun createFromSchema(target: SQLiteDatabase, version: Int) {
        val database = Json.parseToJsonElement(
            schemaFile(version).readText()
        ).jsonObject.getValue("database").jsonObject
        for (entity in database.getValue("entities").jsonArray) {
            val entityObject: JsonObject = entity.jsonObject
            val table = entityObject.getValue("tableName").jsonPrimitive.content
            target.execSQL(entityObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            val indices: JsonArray = entityObject["indices"]?.jsonArray ?: JsonArray(emptyList())
            for (index in indices) {
                target.execSQL(
                    index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
                )
            }
        }
        for (query in database.getValue("setupQueries").jsonArray) target.execSQL(query.jsonPrimitive.content)
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
