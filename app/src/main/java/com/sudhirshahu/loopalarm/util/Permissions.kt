package com.sudhirshahu.loopalarm.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.sudhirshahu.loopalarm.notify.Notifications

/** One reliability permission shown on the permissions screen. */
data class PermissionItem(
    val key: String,
    val title: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean,
    /** Settings screen to open; null when the permission is a runtime prompt (notifications). */
    val intent: Intent?,
)

object Permissions {
    @SuppressLint("BatteryLife")
    fun items(context: Context): List<PermissionItem> {
        val pkg = Uri.parse("package:${context.packageName}")
        val am = context.getSystemService(AlarmManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val list = mutableListOf<PermissionItem>()

        list += PermissionItem(
            "notifications", "Notifications",
            "Needed to show each alarm, the next alarm in the notification bar and the alarm log.",
            Notifications.canPost(context), true,
            if (Build.VERSION.SDK_INT >= 33) null else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += PermissionItem(
                "exact", "Alarms and reminders",
                "Lets alarms ring at the exact second instead of being delayed by the system.",
                am.canScheduleExactAlarms(), true,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
            )
        }
        list += PermissionItem(
            "battery", "Ignore battery optimisation",
            "Stops the system (and aggressive manufacturer battery savers) from killing or delaying alarms.",
            pm.isIgnoringBatteryOptimizations(context.packageName), true,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
        )
        if (Build.VERSION.SDK_INT >= 34) {
            list += PermissionItem(
                "fullscreen", "Full-screen alarm",
                "Shows the post-alarm screen over the lock screen.",
                nm.canUseFullScreenIntent(), false,
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
            )
        }
        list += PermissionItem(
            "overlay", "Display over other apps",
            "Opens the post-alarm screen straight away even while you are using another app.",
            Settings.canDrawOverlays(context), false,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
        )
        list += PermissionItem(
            "dnd", "Do Not Disturb access",
            "Only needed for the 'Ring through Do Not Disturb' setting.",
            nm.isNotificationPolicyAccessGranted, false,
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
        )
        return list
    }

    fun missingRequired(context: Context) = items(context).count { it.required && !it.granted }

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
