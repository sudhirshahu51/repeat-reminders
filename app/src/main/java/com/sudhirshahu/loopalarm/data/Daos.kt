package com.sudhirshahu.loopalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY startMinute, id")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Query("SELECT * FROM alarms WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<Alarm>

    @Upsert
    suspend fun upsert(alarm: Alarm): Long

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("UPDATE alarms SET enabled = :enabled, snoozeUntil = 0, skipUntil = 0 WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = :enabled, snoozeUntil = 0, skipUntil = 0 WHERE groupName = :group")
    suspend fun setGroupEnabled(group: String, enabled: Boolean)

    @Query("UPDATE alarms SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroup(oldName: String, newName: String)

    @Query("UPDATE alarms SET snoozeUntil = :until WHERE id = :id")
    suspend fun setSnooze(id: Long, until: Long)

    @Query("UPDATE alarms SET skipUntil = :until WHERE id = :id")
    suspend fun setSkip(id: Long, until: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY firedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("UPDATE history SET outcome = :outcome, endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, outcome: Outcome, endedAt: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE firedAt < :before")
    suspend fun prune(before: Long)
}
