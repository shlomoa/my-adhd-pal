package com.shlomoa.myadhdpal.phone.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(@PrimaryKey val id: String, val title: String, val position: Int)

/** [days] is the comma-separated weekdays (1 = Monday to 7 = Sunday). A task's reminders are deleted with it. */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ReminderEntity(@PrimaryKey val id: String, val taskId: String, val minuteOfDay: Int, val days: String)

/** The prompts the phone published, so a selection can show the prompt's title. */
@Entity(tableName = "prompts")
data class PromptEntity(@PrimaryKey val id: String, val title: String)

/** One row per event from the watch; the item id is the primary key, which makes storing idempotent. */
@Entity(tableName = "history", indices = [Index("at")])
data class HistoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val at: Long,
    val action: String? = null,
    val key: String? = null,
    val promptTitle: String? = null,
    val metric: String? = null,
    val value: Double? = null,
    val threshold: Double? = null,
    val reminderId: String? = null,
    val occurrenceAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val level: Int = 1,
    val episodeId: String? = null
) {
    companion object {
        const val TYPE_ACK = "ACK"
        const val TYPE_SELECTION = "SELECTION"
        const val TYPE_ALERT = "ALERT"

        fun ack(id: String, at: Long, action: String, reminderId: String? = null, occurrenceAt: Long? = null) =
            HistoryEntity(id, TYPE_ACK, at, action = action, reminderId = reminderId, occurrenceAt = occurrenceAt)

        fun selection(id: String, at: Long, key: String, promptTitle: String? = null) =
            HistoryEntity(id, TYPE_SELECTION, at, key = key, promptTitle = promptTitle)

        fun alert(
            id: String,
            at: Long,
            metric: String,
            value: Double,
            threshold: Double,
            level: Int = 1,
            episodeId: String? = null
        ) = HistoryEntity(
            id,
            TYPE_ALERT,
            at,
            metric = metric,
            value = value,
            threshold = threshold,
            level = level,
            episodeId = episodeId
        )
    }
}
