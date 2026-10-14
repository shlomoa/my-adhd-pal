package com.shlomoa.myadhdpal.phone.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An in-memory [AppDatabase] per test, on Robolectric. */
@RunWith(RobolectricTestRunner::class)
abstract class RoomTestBase {
    protected lateinit var db: AppDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }
}
