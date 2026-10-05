package com.sudhirshahu.loopalarm.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), BLACK("AMOLED black") }

/** App accent. [notebook] is the alias suffix for the matching launcher icon page colour ("" = white page). */
enum class Accent(val label: String, val argb: Long) {
    DYNAMIC("Wallpaper", 0xFF6750A4),
    WHITE("White", 0xFFFFFFFF),
    INDIGO("Indigo", 0xFF3F51B5),
    TEAL("Teal", 0xFF00897B),
    GREEN("Green", 0xFF43A047),
    ORANGE("Orange", 0xFFF57C00),
    RED("Red", 0xFFE53935),
    PINK("Pink", 0xFFD81B60),
    PURPLE("Purple", 0xFF8E24AA),
}

enum class TimeFormat(val label: String) { SYSTEM("Follow system"), H12("12-hour"), H24("24-hour") }

/** What to do when an alarm fires while the phone is in a call. */
enum class CallBehavior(val label: String) {
    RING("Ring normally"),
    VIBRATE("Vibrate and notify only"),
    SKIP("Skip silently (log only)"),
}

/** Home-screen icon choices. Each is an activity-alias in the manifest; [alias] is its class name suffix. */
enum class AppIcon(val label: String, val alias: String, val argb: Long) {
    DEFAULT("Black", "LauncherDefault", 0xFF000000),
    ORANGE("Orange", "LauncherOrange", 0xFFF4511E),
    INDIGO("Indigo", "LauncherIndigo", 0xFF3949AB),
    TEAL("Teal", "LauncherTeal", 0xFF00897B),
    PINK("Pink", "LauncherPink", 0xFFD81B60),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.DYNAMIC,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showNextAlarmNotification: Boolean = true,
    val keepNotificationAfterAlarm: Boolean = true,
    val callBehavior: CallBehavior = CallBehavior.VIBRATE,
    val overrideDnd: Boolean = false,
    /** While the phone is in use, show a compact card on top of other apps instead of the full alarm screen. */
    val popupOverApps: Boolean = true,
    val appIcon: AppIcon = AppIcon.DEFAULT,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val timeFormat = stringPreferencesKey("time_format")
        val nextNotif = booleanPreferencesKey("next_notification")
        val keepNotif = booleanPreferencesKey("keep_notification")
        val callBehavior = stringPreferencesKey("call_behavior")
        val overrideDnd = booleanPreferencesKey("override_dnd")
        val popup = booleanPreferencesKey("popup_over_apps")
        val appIcon = stringPreferencesKey("app_icon")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.theme] = s.themeMode.name
            prefs[Keys.accent] = s.accent.name
            prefs[Keys.timeFormat] = s.timeFormat.name
            prefs[Keys.nextNotif] = s.showNextAlarmNotification
            prefs[Keys.keepNotif] = s.keepNotificationAfterAlarm
            prefs[Keys.callBehavior] = s.callBehavior.name
            prefs[Keys.overrideDnd] = s.overrideDnd
            prefs[Keys.popup] = s.popupOverApps
            prefs[Keys.appIcon] = s.appIcon.name
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[Keys.theme], d.themeMode),
            accent = enumOr(this[Keys.accent], d.accent),
            timeFormat = enumOr(this[Keys.timeFormat], d.timeFormat),
            showNextAlarmNotification = this[Keys.nextNotif] ?: d.showNextAlarmNotification,
            keepNotificationAfterAlarm = this[Keys.keepNotif] ?: d.keepNotificationAfterAlarm,
            callBehavior = enumOr(this[Keys.callBehavior], d.callBehavior),
            overrideDnd = this[Keys.overrideDnd] ?: d.overrideDnd,
            popupOverApps = this[Keys.popup] ?: d.popupOverApps,
            appIcon = enumOr(this[Keys.appIcon], d.appIcon),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}

/** Launcher alias suffix for the notebook page colour; Wallpaper and White keep the white page. */
val Accent.notebook: String
    get() = if (this == Accent.DYNAMIC || this == Accent.WHITE) "" else name.lowercase().replaceFirstChar { it.uppercase() }
