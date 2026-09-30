package com.sudhirshahu.loopalarm.util

import android.content.Context
import android.text.format.DateFormat
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.EndMode
import com.sudhirshahu.loopalarm.data.ScheduleMode
import com.sudhirshahu.loopalarm.data.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
    val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val MONTH_SHORT = (1..12).map { java.time.Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    fun is24h(context: Context, tf: TimeFormat) = when (tf) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    private fun timePattern(use24: Boolean) = if (use24) "HH:mm" else "h:mm a"

    fun time(millis: Long, use24: Boolean): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(timePattern(use24)))

    fun timeWithSeconds(millis: Long, use24: Boolean): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (use24) "HH:mm:ss" else "h:mm:ss a"))

    fun minuteOfDay(m: Int, use24: Boolean): String =
        LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofPattern(timePattern(use24)))

    fun dateTime(millis: Long, use24: Boolean): String {
        val z = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val today = LocalDate.now()
        val day = when (z.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> z.format(DateTimeFormatter.ofPattern(if (z.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"))
        }
        return "$day, ${time(millis, use24)}"
    }

    fun relative(target: Long, now: Long = System.currentTimeMillis()): String {
        var s = ((target - now) / 1000).coerceAtLeast(0)
        val d = s / 86400; s %= 86400
        val h = s / 3600; s %= 3600
        val m = s / 60
        return when {
            d > 0 -> "in ${d}d ${h}h"
            h > 0 -> "in ${h}h ${m}m"
            m > 0 -> "in ${m}m"
            else -> "in <1m"
        }
    }

    fun interval(a: Alarm): String {
        val unit = when (a.intervalUnit) {
            com.sudhirshahu.loopalarm.data.IntervalUnit.SECONDS -> "sec"
            com.sudhirshahu.loopalarm.data.IntervalUnit.MINUTES -> "min"
            com.sudhirshahu.loopalarm.data.IntervalUnit.HOURS -> if (a.intervalValue == 1) "hour" else "hours"
        }
        return "Every ${a.intervalValue} $unit"
    }

    fun window(a: Alarm, use24: Boolean): String {
        val start = minuteOfDay(a.startMinute, use24)
        return when (a.endMode) {
            EndMode.END_OF_DAY -> "from $start until midnight"
            EndMode.AT_TIME -> "$start to ${minuteOfDay(a.endMinute, use24)}"
            EndMode.AFTER_COUNT -> if (a.repeatCount == 1) "once at $start" else "from $start, ${a.repeatCount} times"
        }
    }

    fun schedule(a: Alarm): String {
        val base = when (a.scheduleMode) {
            ScheduleMode.DAILY -> "Every day"
            ScheduleMode.WEEK_DAYS -> when (a.daysOfWeek) {
                0b1111111 -> "Every day"
                0b0011111 -> "Weekdays"
                0b1100000 -> "Weekends"
                0 -> "No days selected"
                else -> (0..6).filter { (a.daysOfWeek shr it) and 1 == 1 }.joinToString(" ") { DAY_SHORT[it] }
            }
            ScheduleMode.MONTH_DAYS -> {
                val days = (0..30).filter { (a.daysOfMonth shr it) and 1 == 1 }.map { it + 1 }
                if (days.isEmpty()) "No days of month selected" else "On day " + days.joinToString(", ")
            }
            ScheduleMode.DATES -> {
                val n = a.dateSet.size
                if (n == 0) "No dates selected" else "$n specific date" + if (n == 1) "" else "s"
            }
        }
        if (a.scheduleMode == ScheduleMode.DATES) return base
        val parts = mutableListOf(base)
        if (a.months != 0) parts += "in " + (0..11).filter { (a.months shr it) and 1 == 1 }.joinToString(", ") { MONTH_SHORT[it] }
        if (a.yearSet.isNotEmpty()) parts += a.yearSet.sorted().joinToString(", ")
        return parts.joinToString(" · ")
    }

    fun duration(sec: Int): String = when {
        sec < 60 -> "$sec sec"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> "${sec / 60} min ${sec % 60} sec"
    }
}
