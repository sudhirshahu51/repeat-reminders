package com.sudhirshahu.loopalarm.schedule

import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.EndMode
import com.sudhirshahu.loopalarm.data.IntervalUnit
import com.sudhirshahu.loopalarm.data.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun ms(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun str(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()
    private fun next(a: Alarm, from: String) = ScheduleCalculator.nextTrigger(a, ms(from), zone)?.let(::str)

    private val base = Alarm(id = 1, startMinute = 9 * 60, intervalValue = 30, intervalUnit = IntervalUnit.MINUTES)

    @Test fun beforeStartRingsAtStart() = assertEquals("2026-09-30T09:00", next(base, "2026-09-30T07:15"))

    @Test fun duringWindowRingsAtNextStep() = assertEquals("2026-09-30T10:30", next(base, "2026-09-30T10:05"))

    @Test fun exactlyOnAStepMovesToTheNextOne() = assertEquals("2026-09-30T10:30", next(base, "2026-09-30T10:00"))

    @Test fun endOfDayRollsToTomorrow() = assertEquals("2026-10-01T09:00", next(base, "2026-09-30T23:45"))

    @Test fun endTimeIsInclusive() {
        val a = base.copy(endMode = EndMode.AT_TIME, endMinute = 11 * 60)
        assertEquals("2026-09-30T11:00", next(a, "2026-09-30T10:45"))
        assertEquals("2026-10-01T09:00", next(a, "2026-09-30T11:00"))
    }

    @Test fun overnightWindowContinuesAfterMidnight() {
        val a = base.copy(startMinute = 22 * 60, endMode = EndMode.AT_TIME, endMinute = 2 * 60, intervalValue = 1, intervalUnit = IntervalUnit.HOURS)
        assertEquals("2026-10-01T01:00", next(a, "2026-10-01T00:10"))
        assertEquals("2026-10-01T22:00", next(a, "2026-10-01T02:00"))
    }

    @Test fun ringCountLimitsRepeats() {
        val a = base.copy(endMode = EndMode.AFTER_COUNT, repeatCount = 3)
        assertEquals("2026-09-30T10:00", next(a, "2026-09-30T09:45"))
        assertEquals("2026-10-01T09:00", next(a, "2026-09-30T10:00"))
    }

    @Test fun weekDaysOnly() {
        // 2026-10-03 is a Saturday
        val a = base.copy(scheduleMode = ScheduleMode.WEEK_DAYS, daysOfWeek = 0b0011111)
        assertEquals("2026-10-05T09:00", next(a, "2026-10-02T23:45"))
    }

    @Test fun daysOfMonthWithMonthFilter() {
        // 15th of January and July only
        val a = base.copy(scheduleMode = ScheduleMode.MONTH_DAYS, daysOfMonth = 1 shl 14, months = (1 shl 0) or (1 shl 6))
        assertEquals("2027-01-15T09:00", next(a, "2026-09-30T12:00"))
    }

    @Test fun yearFilter() {
        val a = base.copy(years = "2028")
        assertEquals("2028-01-01T09:00", next(a, "2026-09-30T12:00"))
    }

    @Test fun specificDates() {
        val a = base.copy(scheduleMode = ScheduleMode.DATES, dates = "2026-12-25,2026-10-10")
        assertEquals("2026-10-10T09:00", next(a, "2026-09-30T12:00"))
        assertNull(next(a, "2026-12-26T00:00"))
    }

    @Test fun skipUntilSkipsOneRing() {
        val a = base.copy(skipUntil = ms("2026-09-30T09:30"))
        assertEquals("2026-09-30T10:00", next(a, "2026-09-30T09:10"))
    }

    @Test fun snoozeWinsWhenEarlier() {
        val a = base.copy(snoozeUntil = ms("2026-09-30T09:05"))
        assertEquals("2026-09-30T09:05", ScheduleCalculator.nextIncludingSnooze(a, ms("2026-09-30T09:01"), zone)?.let(::str))
    }

    @Test fun secondsInterval() {
        val a = base.copy(intervalValue = 45, intervalUnit = IntervalUnit.SECONDS)
        assertEquals("2026-09-30T09:01:30", next(a, "2026-09-30T09:01:00"))
    }
}
