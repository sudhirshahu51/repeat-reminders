package com.sudhirshahu.loopalarm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IntervalUnit(val seconds: Long, val label: String) {
    SECONDS(1, "sec"), MINUTES(60, "min"), HOURS(3600, "hr")
}

/** How the repeating window of a day ends. */
enum class EndMode { END_OF_DAY, AT_TIME, AFTER_COUNT }

/** Which dates the alarm is active on. Month and year filters apply on top (except for DATES). */
enum class ScheduleMode { DAILY, WEEK_DAYS, MONTH_DAYS, DATES }

enum class SoundType { BEEP, RINGTONE, FILE, SILENT }

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    /** emoji shown next to the name; empty means the default alarm icon */
    @ColumnInfo(defaultValue = "") val icon: String = "",
    /** reminders with the same group name are listed together; empty means ungrouped */
    @ColumnInfo(defaultValue = "") val groupName: String = "",
    /** free-text notes, shown on the card and when the reminder rings */
    @ColumnInfo(defaultValue = "") val notes: String = "",

    /** false = rings once at the start time on each matching day, ignoring the interval and window end */
    @ColumnInfo(defaultValue = "1") val repeating: Boolean = true,

    // Repeat interval
    val intervalValue: Int = 30,
    val intervalUnit: IntervalUnit = IntervalUnit.MINUTES,

    // Daily window: start time and where the repeats stop
    val startMinute: Int = 9 * 60,
    val endMode: EndMode = EndMode.END_OF_DAY,
    val endMinute: Int = 18 * 60,
    val repeatCount: Int = 5,

    // Calendar
    val scheduleMode: ScheduleMode = ScheduleMode.DAILY,
    /** bit 0 = Monday ... bit 6 = Sunday */
    val daysOfWeek: Int = 0b1111111,
    /** bit 0 = day 1 ... bit 30 = day 31 */
    val daysOfMonth: Int = 0,
    /** bit 0 = January ... bit 11 = December; 0 means every month */
    val months: Int = 0,
    /** comma separated years, empty means every year */
    val years: String = "",
    /** comma separated ISO dates (yyyy-MM-dd) for DATES mode */
    val dates: String = "",

    // Ringing behaviour
    val ringDurationSec: Int = 30,
    val gradualVolume: Boolean = false,
    val gradualSec: Int = 20,
    val volumePercent: Int = 80,
    val vibrate: Boolean = true,
    val soundType: SoundType = SoundType.BEEP,
    val soundValue: String = "classic",
    val soundTitle: String = "Classic beep",
    val showPostScreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** picture shown when the alarm rings; a file name in [ImageStore], empty for none */
    @ColumnInfo(defaultValue = "") val imageFile: String = "",

    // Runtime state
    val snoozeUntil: Long = 0,
    /** occurrences at or before this instant are skipped */
    val skipUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val intervalSeconds: Long get() = (intervalValue.coerceAtLeast(1) * intervalUnit.seconds).coerceAtLeast(10)

    val yearSet: Set<Int> get() = years.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    val dateSet: Set<String> get() = dates.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val displayName: String get() = name.ifBlank { "Alarm" }
}
