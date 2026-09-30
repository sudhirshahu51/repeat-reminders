package com.sudhirshahu.loopalarm

import android.app.Application
import android.content.Context
import com.sudhirshahu.loopalarm.data.AppDatabase
import com.sudhirshahu.loopalarm.data.SettingsRepository
import com.sudhirshahu.loopalarm.notify.Notifications
import com.sudhirshahu.loopalarm.schedule.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LoopAlarmApp : Application() {
    /** Scope for work that must outlive a screen, e.g. rescheduling after an edit. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { scheduler.rescheduleAll() }
    }
}

val Context.app: LoopAlarmApp get() = applicationContext as LoopAlarmApp
