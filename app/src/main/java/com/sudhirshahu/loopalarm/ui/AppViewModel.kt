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
import com.sudhirshahu.loopalarm.ring.AlarmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val alarmDao = app.db.alarmDao()
    private val historyDao = app.db.historyDao()

    val alarms: StateFlow<List<Alarm>> = alarmDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<HistoryEntry>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        reschedule()
    }

    /** Rescheduling runs in the app scope so leaving a screen never drops it. */
    private fun reschedule() {
        app.appScope.launch { app.scheduler.rescheduleAll() }
    }

    /** Removes pictures no alarm uses any more (replaced, deleted, or picked in an editor that was not saved). */
    private fun cleanupImages() {
        app.appScope.launch(Dispatchers.IO) { ImageStore.cleanup(app, alarmDao.getAll().map { it.imageFile }.toSet()) }
    }

    suspend fun load(id: Long): Alarm? = alarmDao.get(id)

    fun save(alarm: Alarm, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        // Editing clears any pending snooze/skip so the new schedule applies right away.
        val id = alarmDao.upsert(alarm.copy(snoozeUntil = 0, skipUntil = 0))
        reschedule()
        cleanupImages()
        onSaved(if (alarm.id == 0L) id else alarm.id)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        alarmDao.setEnabled(alarm.id, enabled)
        reschedule()
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        alarmDao.delete(alarm)
        reschedule()
        cleanupImages()
    }

    fun duplicate(alarm: Alarm) = save(alarm.copy(id = 0, name = alarm.name + " (copy)", createdAt = System.currentTimeMillis()))

    fun ringNow(alarm: Alarm) {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, AlarmService.fireIntent(ctx, longArrayOf(alarm.id), System.currentTimeMillis()))
    }

    fun clearHistory() = viewModelScope.launch { historyDao.clear() }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        app.settings.update(transform)
        reschedule() // refresh the next-alarm notification
    }
}
