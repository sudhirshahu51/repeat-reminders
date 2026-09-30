package com.sudhirshahu.loopalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Outcome(val label: String) {
    RINGING("Ringing"),
    DISMISSED("Dismissed"),
    SNOOZED("Snoozed"),
    TIMED_OUT("Stopped after duration"),
    VIBRATED_IN_CALL("Vibrated (in a call)"),
    SKIPPED_IN_CALL("Skipped (in a call)"),
    REPLACED("Interrupted by another alarm"),
}

@Entity(tableName = "history", indices = [Index("firedAt")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val alarmName: String,
    val scheduledAt: Long,
    val firedAt: Long,
    val endedAt: Long = 0,
    val outcome: Outcome = Outcome.RINGING,
)
