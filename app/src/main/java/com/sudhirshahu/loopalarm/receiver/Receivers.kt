package com.sudhirshahu.loopalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.ring.AlarmService
import kotlinx.coroutines.launch

/** Fired by AlarmManager at the ring time. Hands off to the foreground ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: return
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        ContextCompat.startForegroundService(context, AlarmService.fireIntent(context, ids, time))
    }

    companion object {
        const val ACTION_FIRE = "com.sudhirshahu.loopalarm.FIRE"
        const val EXTRA_IDS = "ids"
        const val EXTRA_TIME = "time"
    }
}

/** Alarms are cleared on reboot and invalidated by clock changes, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val pending = goAsync()
        context.app.appScope.launch {
            try {
                context.app.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

private val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED,
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
)

/** "Skip this one" action on the next-alarm notification. */
class NextAlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SKIP) return
        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: return
        val time = intent.getLongExtra(EXTRA_TIME, 0)
        val pending = goAsync()
        context.app.appScope.launch {
            try {
                val dao = context.app.db.alarmDao()
                dao.getMany(ids.toList()).forEach { a ->
                    if (a.snoozeUntil == time) dao.setSnooze(a.id, 0) else dao.setSkip(a.id, time)
                }
                context.app.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SKIP = "com.sudhirshahu.loopalarm.SKIP_NEXT"
        const val EXTRA_IDS = "ids"
        const val EXTRA_TIME = "time"
    }
}
