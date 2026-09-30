package com.sudhirshahu.loopalarm.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.notify.Notifications
import com.sudhirshahu.loopalarm.receiver.AlarmReceiver
import com.sudhirshahu.loopalarm.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps exactly one system alarm registered: the earliest upcoming ring across all enabled alarms.
 * Using setAlarmClock makes Android show the alarm icon and next alarm time in the status bar,
 * and exempts the ring from Doze and background start limits.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    data class Next(val time: Long, val alarmIds: LongArray, val names: List<String>)

    /** Reschedules. [notBefore] prevents re-arming an occurrence that is ringing right now. */
    suspend fun rescheduleAll(notBefore: Long = 0): Next? = mutex.withLock {
        val app = context.app
        val after = maxOf(System.currentTimeMillis(), notBefore)
        val candidates = app.db.alarmDao().getAll()
            .filter { it.enabled }
            .mapNotNull { a -> ScheduleCalculator.nextIncludingSnooze(a, after)?.let { a to it } }
        val time = candidates.minOfOrNull { it.second }
        if (time == null) {
            alarmManager.cancel(firePendingIntent(null))
            Notifications.cancelNext(context)
            return@withLock null
        }
        val due = candidates.filter { it.second == time }.map { it.first }
        val next = Next(time, due.map { it.id }.toLongArray(), due.map { it.displayName })
        setSystemAlarm(next)
        Notifications.showNext(context, next, app.settings.current())
        next
    }

    private fun setSystemAlarm(next: Next) {
        val fire = firePendingIntent(next)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next.time, show), fire)
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time, fire)
    }

    /** With [next] null, returns an equivalent intent that is only used for cancelling. */
    private fun firePendingIntent(next: Next?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
        if (next != null) {
            intent.putExtra(AlarmReceiver.EXTRA_IDS, next.alarmIds).putExtra(AlarmReceiver.EXTRA_TIME, next.time)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_FIRE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_FIRE = 100
    }
}
