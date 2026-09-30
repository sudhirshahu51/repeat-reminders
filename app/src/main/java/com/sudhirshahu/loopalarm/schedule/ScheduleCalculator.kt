package com.sudhirshahu.loopalarm.schedule

import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.EndMode
import com.sudhirshahu.loopalarm.data.ScheduleMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure calculation of when a repeat alarm rings next.
 *
 * On every matching date the alarm rings at the start time and then every interval until the
 * window ends (end of day, a fixed end time which may be after midnight, or after N rings).
 */
object ScheduleCalculator {
    /** How far ahead we search. Covers year filters and specific dates up to five years out. */
    private const val LOOKAHEAD_DAYS = 366L * 5 + 2
    private const val DAY_MS = 86_400_000L

    fun matcher(a: Alarm): (LocalDate) -> Boolean {
        val dates = a.dateSet
        val years = a.yearSet
        return { date ->
            if (a.scheduleMode == ScheduleMode.DATES) {
                date.toString() in dates
            } else if (a.months != 0 && (a.months shr (date.monthValue - 1)) and 1 == 0) {
                false
            } else if (years.isNotEmpty() && date.year !in years) {
                false
            } else when (a.scheduleMode) {
                ScheduleMode.DAILY -> true
                ScheduleMode.WEEK_DAYS -> (a.daysOfWeek shr (date.dayOfWeek.value - 1)) and 1 == 1
                ScheduleMode.MONTH_DAYS -> (a.daysOfMonth shr (date.dayOfMonth - 1)) and 1 == 1
                ScheduleMode.DATES -> true
            }
        }
    }

    private fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone).toInstant().toEpochMilli()

    /** Inclusive end of the ringing window that starts on [date]. */
    private fun windowEnd(a: Alarm, date: LocalDate, start: Long, intervalMs: Long, zone: ZoneId): Long =
        when (a.endMode) {
            EndMode.END_OF_DAY -> date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            EndMode.AT_TIME -> {
                val endDate = if (a.endMinute >= a.startMinute) date else date.plusDays(1)
                at(endDate, a.endMinute, zone)
            }
            EndMode.AFTER_COUNT -> start + (a.repeatCount.coerceIn(1, 1000) - 1) * intervalMs
        }

    /** First scheduled ring strictly after [afterMs], ignoring snooze. Null if none within the look-ahead. */
    fun nextTrigger(a: Alarm, afterMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val after = maxOf(afterMs, a.skipUntil)
        val intervalMs = a.intervalSeconds * 1000
        val matches = matcher(a)
        // Windows that started on earlier days can still be running (overnight or long count windows).
        val lookBack = when (a.endMode) {
            EndMode.AFTER_COUNT -> ((a.repeatCount.coerceIn(1, 1000) * intervalMs) / DAY_MS + 1).coerceAtMost(31)
            else -> 1
        }
        var date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate().minusDays(lookBack)
        val last = date.plusDays(LOOKAHEAD_DAYS + lookBack)
        var best: Long? = null
        while (!date.isAfter(last)) {
            if (matches(date)) {
                val start = at(date, a.startMinute, zone)
                // Later dates start later, so nothing after this can beat the best found so far.
                if (best != null && start > best) break
                val end = windowEnd(a, date, start, intervalMs, zone)
                if (end > after) {
                    val k = if (after >= start) (after - start) / intervalMs + 1 else 0
                    val t = start + k * intervalMs
                    if (t <= end && t > after && (best == null || t < best)) best = t
                }
            }
            date = date.plusDays(1)
        }
        return best
    }

    /** Next ring including a pending snooze. */
    fun nextIncludingSnooze(a: Alarm, afterMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val regular = nextTrigger(a, afterMs, zone)
        val snooze = a.snoozeUntil.takeIf { it > afterMs }
        return listOfNotNull(regular, snooze).minOrNull()
    }

    /** Up to [count] upcoming rings, for previews. */
    fun upcoming(a: Alarm, fromMs: Long, count: Int, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val out = ArrayList<Long>(count)
        var t = fromMs
        repeat(count) {
            val n = nextTrigger(a.copy(skipUntil = 0), t, zone) ?: return out
            out += n
            t = n
        }
        return out
    }
}
