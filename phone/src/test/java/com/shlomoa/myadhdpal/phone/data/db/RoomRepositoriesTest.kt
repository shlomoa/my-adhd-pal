package com.shlomoa.myadhdpal.phone.data.db

import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.contract.HistoryRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.TaskRepositoryContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// TC-S-092: the Room-backed repositories pass the same contract tests as the in-memory fakes.

@RunWith(RobolectricTestRunner::class)
class RoomTaskRepositoryTest : TaskRepositoryContract() {
    private var database: AppDatabase? = null

    override fun create(): TaskRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val created = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        database = created
        return RoomTaskRepository(created)
    }

    @After
    fun close() {
        database?.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomHistoryRepositoryTest : HistoryRepositoryContract() {
    private var database: AppDatabase? = null

    override fun create(): HistoryRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val created = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        database = created
        return RoomHistoryRepository(created)
    }

    @After
    fun close() {
        database?.close()
    }
}
