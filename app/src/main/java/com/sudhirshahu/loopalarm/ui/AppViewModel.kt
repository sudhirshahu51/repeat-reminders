package com.sudhirshahu.loopalarm.ui

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.data.HistoryEntry
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.data.notebook
import com.sudhirshahu.loopalarm.ring.AlarmService
import com.sudhirshahu.loopalarm.util.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val alarmDao = app.db.alarmDao()
    private val historyDao = app.db.historyDao()

    val alarms: StateFlow<List<Alarm>> = alarmDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groups: StateFlow<List<String>> = alarmDao.observeAll()
        .map { list -> list.map { it.groupName.trim() }.filter { it.isNotEmpty() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<HistoryEntry>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        reschedule()
        app.appScope.launch { syncPeopleAlarm(applySettings = false) }
    }

    /** Rescheduling runs in the app scope so leaving a screen never drops it. */
    private fun reschedule() {
        app.appScope.launch { app.scheduler.rescheduleAll() }
    }

    /** Removes pictures no alarm uses any more (replaced, deleted, or picked in an editor that was not saved). */
    private fun cleanupImages() {
        app.appScope.launch(Dispatchers.IO) { ImageStore.cleanup(app, alarmDao.getAll().flatMap { it.images }.toSet()) }
    }

    suspend fun load(id: Long): Alarm? = alarmDao.get(id)

    fun save(alarm: Alarm, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        // Editing clears any pending snooze/skip so the new schedule applies right away.
        val id = alarmDao.upsert(alarm.copy(groupName = alarm.groupName.trim(), snoozeUntil = 0, skipUntil = 0))
        reschedule()
        cleanupImages()
        onSaved(if (alarm.id == 0L) id else alarm.id)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        alarmDao.setEnabled(alarm.id, enabled)
        reschedule()
    }

    fun setGroupEnabled(group: String, enabled: Boolean) = viewModelScope.launch {
        alarmDao.setGroupEnabled(group, enabled)
        reschedule()
    }

    /** Adds many reminders at once (birthday import). */
    fun addAll(list: List<Alarm>, onDone: () -> Unit = {}) = viewModelScope.launch {
        val settings = app.settings.current()
        list.forEach { alarmDao.upsert(settings.newAlarm(it)) } // birthdays ring the way the user prefers
        reschedule()
        onDone()
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        alarmDao.delete(alarm)
        reschedule()
        cleanupImages()
    }

    /** Ticking a checklist point on the details page: only the notes change, the schedule stays as it is. */
    fun setNotes(alarm: Alarm, notes: String) = viewModelScope.launch { alarmDao.upsert(alarm.copy(notes = notes)) }

    fun duplicate(alarm: Alarm) = save(alarm.copy(id = 0, name = alarm.name + " (copy)", createdAt = System.currentTimeMillis()))

    fun ringNow(alarm: Alarm) {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, AlarmService.fireIntent(ctx, longArrayOf(alarm.id), System.currentTimeMillis()))
    }

    fun clearHistory() = viewModelScope.launch { historyDao.clear() }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        val before = app.settings.current()
        app.settings.update(transform)
        val after = app.settings.current()
        // The launcher icon shows the background choice with a notebook page in the accent colour.
        if (after.appIcon != before.appIcon || after.accent.notebook != before.accent.notebook) AppIcons.apply(app, after.appIcon, after.accent)
        if (after.peopleReminders != before.peopleReminders || after.peopleCount != before.peopleCount ||
            after.peopleMinute != before.peopleMinute
        ) syncPeopleAlarm(applySettings = true)
        reschedule() // refresh the next-alarm notification
    }

    /**
     * "Remind me of random people" is one reminder of its own: made when the Settings switch is turned on (6 PM, 5 people
     * unless changed), kept in step with the time and number chosen there, and removed when the switch is turned off.
     * No other reminder shows people. With [applySettings] false (app start) nothing is created or changed, so a reminder
     * the user deleted stays deleted.
     */
    private suspend fun syncPeopleAlarm(applySettings: Boolean) {
        var s = app.settings.current()
        val existing = if (s.peopleAlarmId != 0L) alarmDao.get(s.peopleAlarmId) else null
        if (applySettings) {
            if (!s.peopleReminders) {
                existing?.let { alarmDao.delete(it) }
                if (s.peopleAlarmId != 0L) app.settings.update { it.copy(peopleAlarmId = 0) }
            } else if (existing == null) {
                val id = alarmDao.upsert(
                    s.newAlarm(
                        Alarm(
                            name = "People to remember", icon = "\uD83D\uDC65", repeating = false,
                            startMinute = s.peopleMinute, peopleCount = s.peopleCount,
                        ),
                    ),
                )
                app.settings.update { it.copy(peopleAlarmId = id) }
            } else if (existing.startMinute != s.peopleMinute || existing.peopleCount != s.peopleCount) {
                alarmDao.upsert(existing.copy(startMinute = s.peopleMinute, peopleCount = s.peopleCount, snoozeUntil = 0, skipUntil = 0))
            }
            s = app.settings.current()
        }
        // From 1.8.2: reminders that had the people switch of their own lose it.
        alarmDao.getAll().filter { it.peopleCount > 0 && it.id != s.peopleAlarmId }
            .forEach { alarmDao.upsert(it.copy(peopleCount = 0, peopleList = "")) }
    }
}
