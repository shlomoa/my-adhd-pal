package com.shlomoa.myadhdpal.phone.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaoTest : RoomTestBase() {
    @Test
    fun `TC-S-093 task DAO inserts, updates, deletes, queries by id and lists in order`() = runTest {
        val dao = db.taskDao()
        assertNull(dao.maxPosition())
        dao.upsert(TaskEntity("t1", "Take medication", 0))
        dao.upsert(TaskEntity("t2", "Stretch", 1))
        assertEquals(listOf("t1", "t2"), dao.observeAll().first().map { it.id })
        assertEquals(TaskEntity("t2", "Stretch", 1), dao.get("t2"))
        assertEquals(1, dao.maxPosition())
        dao.upsert(TaskEntity("t1", "Take medication at 8", 0))
        assertEquals("Take medication at 8", dao.get("t1")?.title)
        assertEquals(2, dao.observeAll().first().size)
        dao.delete("t1")
        assertNull(dao.get("t1"))
        assertEquals(listOf("t2"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun `TC-S-094 reminder DAO does CRUD and queries by task and by time`() = runTest {
        db.taskDao().upsert(TaskEntity("t1", "A", 0))
        db.taskDao().upsert(TaskEntity("t2", "B", 1))
        val dao = db.reminderDao()
        dao.upsert(ReminderEntity("r1", "t1", 20 * 60, "1,2,3,4,5"))
        dao.upsert(ReminderEntity("r2", "t1", 8 * 60, "1,2,3,4,5"))
        dao.upsert(ReminderEntity("r3", "t2", 12 * 60, "6,7"))
        assertEquals(listOf("r2", "r3", "r1"), dao.observeAll().first().map { it.id })
        assertEquals(listOf("r2", "r1"), dao.observeFor("t1").first().map { it.id })
        assertEquals(ReminderEntity("r3", "t2", 12 * 60, "6,7"), dao.get("r3"))
        dao.upsert(ReminderEntity("r3", "t2", 13 * 60, "6"))
        assertEquals(13 * 60, dao.get("r3")?.minuteOfDay)
        dao.delete("r3")
        assertNull(dao.get("r3"))
    }

    @Test
    fun `TC-S-094 deleting a task deletes its reminders`() = runTest {
        db.taskDao().upsert(TaskEntity("t1", "A", 0))
        db.reminderDao().upsert(ReminderEntity("r1", "t1", 60, "1"))
        db.taskDao().delete("t1")
        assertEquals(emptyList<ReminderEntity>(), db.reminderDao().observeAll().first())
    }

    @Test
    fun `TC-S-095 history DAO stores acknowledgements, selections and alerts and orders them by time`() = runTest {
        val dao = db.historyDao()
        dao.insertIgnore(HistoryEntity.ack("a1", at = 100, action = "SNOOZE", reminderId = "r1", occurrenceAt = 90))
        dao.insertIgnore(HistoryEntity.selection("s1", at = 300, key = "calm", promptTitle = "Mood"))
        dao.insertIgnore(HistoryEntity.alert("l1", at = 200, metric = "HEART_RATE", value = 163.5, threshold = 150.0))
        val all = dao.observe(null).first()
        assertEquals(listOf("s1", "l1", "a1"), all.map { it.id })
        assertEquals(listOf("a1"), dao.observe(HistoryEntity.TYPE_ACK).first().map { it.id })
        assertEquals("Mood", all[0].promptTitle)
        assertEquals("r1", all[2].reminderId)
        assertEquals(90L, all[2].occurrenceAt)
        assertEquals(163.5, all[1].value!!, 0.0)
    }

    @Test
    fun `TC-S-096 history is idempotent by id`() = runTest {
        val dao = db.historyDao()
        assertEquals(true, dao.insertIgnore(HistoryEntity.ack("a1", 100, "DONE")) != -1L)
        assertEquals(-1L, dao.insertIgnore(HistoryEntity.ack("a1", 100, "DONE")))
        assertEquals(-1L, dao.insertIgnore(HistoryEntity.ack("a1", 999, "DISMISS")))
        assertEquals(1, dao.observe(null).first().size)
        assertEquals(100L, dao.observe(null).first().single().at)
    }

    @Test
    fun `TC-S-094 prompt DAO remembers the title of a published prompt`() = runTest {
        db.promptDao().upsert(PromptEntity("p1", "Mood"))
        assertEquals("Mood", db.promptDao().titleOf("p1"))
        assertNull(db.promptDao().titleOf("missing"))
    }
}
