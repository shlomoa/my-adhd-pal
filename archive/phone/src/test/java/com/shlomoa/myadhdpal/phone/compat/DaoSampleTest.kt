package com.shlomoa.myadhdpal.phone.compat

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Entity(tableName = "compat_item")
data class CompatItem(@PrimaryKey val id: String, val title: String)

@Dao
interface CompatDao {
    @Insert
    fun insert(item: CompatItem)

    @Query("SELECT * FROM compat_item ORDER BY id")
    fun all(): List<CompatItem>
}

@Database(entities = [CompatItem::class], version = 1, exportSchema = false)
abstract class CompatDatabase : RoomDatabase() {
    abstract fun dao(): CompatDao
}

/** Toolchain compatibility sample (step 0b.1): Room + KSP generate a DAO that works on AGP 9.4 and Kotlin 2.4. */
@RunWith(RobolectricTestRunner::class)
class DaoSampleTest {
    private lateinit var db: CompatDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CompatDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `TC-S-120 Room generates a DAO that stores and reads rows`() {
        db.dao().insert(CompatItem("b", "second"))
        db.dao().insert(CompatItem("a", "first"))
        assertEquals(listOf("a", "b"), db.dao().all().map { it.id })
    }
}
