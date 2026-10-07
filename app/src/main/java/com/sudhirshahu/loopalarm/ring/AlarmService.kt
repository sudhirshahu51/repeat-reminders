package com.sudhirshahu.loopalarm.ring

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sudhirshahu.loopalarm.R
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.data.CallBehavior
import com.sudhirshahu.loopalarm.data.HistoryEntry
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.data.Outcome
import com.sudhirshahu.loopalarm.notify.Notifications
import com.sudhirshahu.loopalarm.ui.RingActivity
import com.sudhirshahu.loopalarm.util.AppIcons
import com.sudhirshahu.loopalarm.util.Fmt
import com.sudhirshahu.loopalarm.util.ReminderIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is ringing right now; observed by [RingActivity]. */
data class Ringing(
    val names: String,
    val firedAt: Long,
    val snoozeMinutes: Int,
    val subtitle: String,
    val imageFile: String = "",
    val icon: String = "",
    val notes: String = "",
    /** the reminders ringing; the alarm screen loads them to show their full details */
    val alarmIds: List<Long> = emptyList(),
)

object RingingState {
    internal val mutable = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = mutable
}

/** Foreground service that rings an alarm: sound, vibration, notification and optional post-alarm screen. */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player by lazy { SoundPlayer(this) }
    private var vibrator: Vibrator? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var session: Session? = null
    private var restoreFilter: Int? = null
    private val popup by lazy { PopupOverlay(this) }

    private class Session(
        val alarms: List<Alarm>,
        val historyIds: List<Long>,
        val firedAt: Long,
        val vibrateOnly: Boolean,
        val settings: AppSettings,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FIRE -> {
                goForeground(placeholderNotification())
                val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: longArrayOf()
                val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                scope.launch { fire(ids.toList(), time) }
            }
            ACTION_DISMISS -> scope.launch { finish(Outcome.DISMISSED) }
            ACTION_SNOOZE -> scope.launch { finish(Outcome.SNOOZED) }
            else -> if (session == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(n: android.app.Notification) {
        // systemExempted is the type Android reserves for alarm apps holding USE_EXACT_ALARM (enforced from Android 14).
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(this, Notifications.ID_RINGING, n, type)
    }

    private fun placeholderNotification() = NotificationCompat.Builder(this, Notifications.CHANNEL_RINGING)
        .setSmallIcon(R.drawable.ic_stat_alarm)
        .setContentTitle("Alarm")
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setSilent(true)
        .build()

    private suspend fun fire(ids: List<Long>, scheduledAt: Long) {
        val app = app
        val dao = app.db.alarmDao()
        val alarms = dao.getMany(ids).filter { it.enabled }
        val settings = app.settings.current()
        alarms.filter { it.snoozeUntil in 1..(scheduledAt + 1000) }.forEach { dao.setSnooze(it.id, 0) }
        app.scheduler.rescheduleAll(notBefore = scheduledAt)

        if (alarms.isEmpty()) {
            if (session == null) stopService()
            return
        }
        if (session != null) finishSession(Outcome.REPLACED, stop = false)

        val now = System.currentTimeMillis()
        val inCall = isInCall()
        val behavior = if (inCall) settings.callBehavior else CallBehavior.RING
        val use24 = Fmt.is24h(this, settings.timeFormat)
        val names = alarms.joinToString(", ") { it.displayName }
        val history = app.db.historyDao()
        history.prune(now - 90L * 86_400_000)

        if (behavior == CallBehavior.SKIP) {
            alarms.forEach {
                history.insert(HistoryEntry(alarmId = it.id, alarmName = it.displayName, scheduledAt = scheduledAt, firedAt = now, endedAt = now, outcome = Outcome.SKIPPED_IN_CALL))
                Notifications.showLog(this, it.id, it.displayName, now, Outcome.SKIPPED_IN_CALL, use24)
            }
            stopService()
            return
        }

        // With several alarms ringing together, the notification shows the first picture of the first one that has any.
        // Loaded before the session starts so a Dismiss can't arrive between the session and the sound.
        val imageFile = alarms.firstNotNullOfOrNull { it.images.firstOrNull() }.orEmpty()
        val picture = withContext(Dispatchers.IO) { ImageStore.load(this@AlarmService, imageFile) }

        val historyIds = alarms.map {
            history.insert(HistoryEntry(alarmId = it.id, alarmName = it.displayName, scheduledAt = scheduledAt, firedAt = now))
        }
        val primary = alarms.first()
        val s = Session(alarms, historyIds, now, behavior == CallBehavior.VIBRATE, settings)
        session = s

        val subtitle = Fmt.summary(primary, use24)
        val icon = primary.icon.ifBlank { alarms.firstOrNull { it.icon.isNotBlank() }?.icon.orEmpty() }
        val notes = alarms.map { it.notes.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        val ringing = Ringing(names, now, primary.snoozeMinutes, subtitle, imageFile, icon, notes, alarms.map { it.id })
        RingingState.mutable.value = ringing

        val showScreen = primary.showPostScreen && !s.vibrateOnly
        // While the phone is in use, a compact card over the current app replaces the full alarm screen.
        val usePopup = showScreen && settings.popupOverApps && isPhoneInUse() && popup.canShow()
        goForeground(ringingNotification(primary, names, icon, notes, now, use24, fullScreen = showScreen && !usePopup, quiet = usePopup, picture))
        if (usePopup) {
            popup.show(
                ringing, picture?.asImageBitmap(), settings, Fmt.time(now, use24),
                onOpen = { popup.hide(); openRingScreen() },
                onSnooze = { scope.launch { finish(Outcome.SNOOZED) } },
                onDismiss = { scope.launch { finish(Outcome.DISMISSED) } },
            )
        } else if (showScreen && Settings.canDrawOverlays(this)) {
            // Overlay permission lets us open the screen directly even when the phone is unlocked.
            openRingScreen()
        }

        if (!s.vibrateOnly) {
            overrideDndIfNeeded(settings)
            startSound(primary)
        }
        if (primary.vibrate || s.vibrateOnly) startVibration()

        timeoutJob = scope.launch {
            delay(primary.ringDurationSec.coerceAtLeast(5) * 1000L)
            timeoutJob = null // so finishing does not cancel this coroutine mid-way
            finish(if (s.vibrateOnly) Outcome.VIBRATED_IN_CALL else Outcome.TIMED_OUT)
        }
    }

    private fun startSound(a: Alarm) {
        val target = a.volumePercent.coerceIn(1, 100) / 100f
        if (!a.gradualVolume) {
            player.start(a.soundType, a.soundValue, target)
            return
        }
        player.start(a.soundType, a.soundValue, target * 0.02f)
        rampJob = scope.launch {
            val steps = (a.gradualSec.coerceAtLeast(1) * 4)
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                player.setVolume(target * f * f) // squared curve sounds more even to the ear
                delay(250)
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attrs)
        vibrator = v
    }

    private fun openRingScreen() {
        runCatching { startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Screen on and unlocked: someone is using the phone right now. */
    private fun isPhoneInUse(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun isInCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /** With DND access, lift a "total silence" or alarms-blocked DND to "alarms only" while ringing. */
    private fun overrideDndIfNeeded(settings: AppSettings) {
        if (!settings.overrideDnd) return
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val filter = nm.currentInterruptionFilter
        val alarmsBlocked = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
            else -> false
        }
        if (alarmsBlocked) {
            restoreFilter = filter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        }
    }

    private fun restoreDnd() {
        val f = restoreFilter ?: return
        restoreFilter = null
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) runCatching { nm.setInterruptionFilter(f) }
    }

    /** [quiet] posts on a low-importance channel so no heads-up appears on top of the pop-up card. */
    private fun ringingNotification(
        a: Alarm, names: String, icon: String, notes: String, firedAt: Long, use24: Boolean, fullScreen: Boolean, quiet: Boolean, picture: Bitmap?,
    ): android.app.Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val dismiss = PendingIntent.getService(this, 10, Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS), flags)
        val snooze = PendingIntent.getService(this, 11, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), flags)
        val screen = PendingIntent.getActivity(this, 12, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        return NotificationCompat.Builder(this, if (quiet) Notifications.CHANNEL_RINGING_QUIET else Notifications.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(if (icon.isBlank()) names else "$icon $names")
            .setContentText(notes.ifEmpty { "${Fmt.time(firedAt, use24)} · ${Fmt.interval(a)}" })
            .apply {
                if (picture == null && notes.isNotEmpty()) {
                    setStyle(NotificationCompat.BigTextStyle().bigText("${Fmt.time(firedAt, use24)} · ${Fmt.interval(a)}\n$notes"))
                }
            }
            // Large icon: the reminder picture (set below), else its emoji, else the app icon.
            .apply { if (picture == null) setLargeIcon(if (icon.isNotBlank()) ReminderIcons.bitmap(icon) else AppIcons.bitmap(this@AlarmService)) }
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setWhen(firedAt)
            .setShowWhen(true)
            .setContentIntent(if (fullScreen) screen else Notifications.mainActivityIntent(this))
            .apply { if (fullScreen) setFullScreenIntent(screen, true) }
            .apply {
                if (picture != null) {
                    setLargeIcon(picture)
                    val style = NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?)
                    // Android 12+ can show the picture even while the notification is collapsed.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) style.showBigPictureWhenCollapsed(true)
                    setStyle(style)
                }
            }
            .addAction(0, "Snooze ${a.snoozeMinutes} min", snooze)
            .addAction(0, "Dismiss", dismiss)
            .build()
    }

    private suspend fun finish(outcome: Outcome) {
        if (session == null) {
            stopService()
            return
        }
        finishSession(outcome, stop = true)
    }

    private suspend fun finishSession(outcome: Outcome, stop: Boolean) {
        val s = session ?: return
        session = null
        rampJob?.cancel()
        timeoutJob?.cancel()
        timeoutJob = null
        player.stop()
        vibrator?.cancel()
        vibrator = null
        restoreDnd()
        popup.hide()

        val app = app
        val now = System.currentTimeMillis()
        s.historyIds.forEach { app.db.historyDao().finish(it, outcome, now) }
        if (outcome == Outcome.SNOOZED) {
            s.alarms.forEach { app.db.alarmDao().setSnooze(it.id, now + it.snoozeMinutes.coerceAtLeast(1) * 60_000L) }
            app.scheduler.rescheduleAll()
        }
        if (s.settings.keepNotificationAfterAlarm) {
            val use24 = Fmt.is24h(this, s.settings.timeFormat)
            s.alarms.forEach { Notifications.showLog(this, it.id, it.displayName, s.firedAt, outcome, use24) }
        }
        RingingState.mutable.value = null
        if (stop) stopService()
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player.stop()
        vibrator?.cancel()
        restoreDnd()
        popup.hide()
        RingingState.mutable.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FIRE = "com.sudhirshahu.loopalarm.RING"
        const val ACTION_DISMISS = "com.sudhirshahu.loopalarm.DISMISS"
        const val ACTION_SNOOZE = "com.sudhirshahu.loopalarm.SNOOZE"
        const val EXTRA_IDS = "ids"
        const val EXTRA_TIME = "time"

        fun fireIntent(context: Context, ids: LongArray, time: Long) =
            Intent(context, AlarmService::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_IDS, ids).putExtra(EXTRA_TIME, time)

        fun actionIntent(context: Context, action: String) = Intent(context, AlarmService::class.java).setAction(action)
    }
}
