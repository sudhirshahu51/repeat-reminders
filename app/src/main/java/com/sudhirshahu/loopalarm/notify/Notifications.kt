package com.sudhirshahu.loopalarm.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sudhirshahu.loopalarm.R
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.data.Outcome
import com.sudhirshahu.loopalarm.receiver.NextAlarmActionReceiver
import com.sudhirshahu.loopalarm.schedule.AlarmScheduler
import com.sudhirshahu.loopalarm.ui.MainActivity
import com.sudhirshahu.loopalarm.util.Fmt

object Notifications {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_RINGING_QUIET = "ringing_quiet"
    const val CHANNEL_NEXT = "next_alarm"
    const val CHANNEL_LOG = "alarm_log"

    const val ID_NEXT = 1
    const val ID_RINGING = 2
    private const val ID_LOG_BASE = 10_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm rings. Sound and vibration are played by the app."
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RINGING_QUIET, "Ringing alarms (pop-up shown)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Used while the pop-up card is on screen, so the notification doesn't cover it."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEXT, "Next alarm", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the next upcoming alarm in the notification bar"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LOG, "Alarm log", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A silent note after each alarm has rung"
                setSound(null, null)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @Suppress("MissingPermission")
    private fun post(context: Context, id: Int, n: android.app.Notification) {
        if (canPost(context)) runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun mainActivityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun showNext(context: Context, next: AlarmScheduler.Next, settings: AppSettings) {
        if (!settings.showNextAlarmNotification) {
            cancelNext(context)
            return
        }
        val use24 = Fmt.is24h(context, settings.timeFormat)
        val skip = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NextAlarmActionReceiver::class.java)
                .setAction(NextAlarmActionReceiver.ACTION_SKIP)
                .putExtra(NextAlarmActionReceiver.EXTRA_IDS, next.alarmIds)
                .putExtra(NextAlarmActionReceiver.EXTRA_TIME, next.time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_NEXT)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Next: ${next.names.joinToString(", ")}")
            .setContentText(Fmt.dateTime(next.time, use24))
            .setWhen(next.time)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainActivityIntent(context))
            .addAction(0, "Skip this one", skip)
            .build()
        post(context, ID_NEXT, n)
    }

    fun cancelNext(context: Context) = NotificationManagerCompat.from(context).cancel(ID_NEXT)

    /** Silent record left in the shade after an alarm rang. */
    fun showLog(context: Context, alarmId: Long, name: String, firedAt: Long, outcome: Outcome, use24: Boolean) {
        val n = NotificationCompat.Builder(context, CHANNEL_LOG)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(name)
            .setContentText("Rang at ${Fmt.time(firedAt, use24)} · ${outcome.label}")
            .setWhen(firedAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(true)
            .setGroup("alarm_log")
            .setContentIntent(mainActivityIntent(context))
            .build()
        post(context, ID_LOG_BASE + (alarmId % 10_000).toInt(), n)
    }

    fun postRaw(context: Context, id: Int, n: android.app.Notification) = post(context, id, n)
}
