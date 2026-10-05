package com.sudhirshahu.loopalarm.data

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A birthday found in a calendar or file. [year] is null when the source doesn't know it. */
data class Birthday(val name: String, val month: Int, val day: Int, val year: Int? = null) {
    val key: String get() = "${name.lowercase()}|$month|$day"

    /** Yearly single-ring reminder at [minuteOfDay]. 29 February rings on the 28th so it isn't skipped in other years. */
    fun toAlarm(minuteOfDay: Int, group: String): Alarm {
        val d = if (month == 2 && day == 29) 28 else day
        return Alarm(
            name = "$name's birthday",
            icon = "🎂",
            groupName = group.trim(),
            repeating = false,
            startMinute = minuteOfDay,
            scheduleMode = ScheduleMode.MONTH_DAYS,
            daysOfMonth = 1 shl (d - 1),
            months = 1 shl (month - 1),
        )
    }
}

/** Reads birthdays from .ics (iCalendar) and .vcf (vCard) text. Pure Kotlin, so it is unit tested. */
object BirthdayParser {
    private val birthdayWords = Regex("""birthday|b'?day|bday|जन्मदिन|🎂""", RegexOption.IGNORE_CASE)

    fun looksLikeBirthday(title: String) = birthdayWords.containsMatchIn(title)

    /** "Asha's birthday" / "Birthday: Asha" / "🎂 Asha" → "Asha". */
    fun cleanName(title: String): String = title
        .replace(Regex("""['’]s\s+(birthday|b'?day|bday)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""(happy\s+)?(birthday|b'?day|bday)(\s+of)?""", RegexOption.IGNORE_CASE), "")
        .replace("🎂", "")
        .trim(' ', ':', '-', '–', '(', ')', '!', ',')
        .ifBlank { title.trim() }

    /** Picks the file format from its content. */
    fun parse(text: String): List<Birthday> = when {
        text.contains("BEGIN:VCALENDAR", ignoreCase = true) -> parseIcs(text)
        text.contains("BEGIN:VCARD", ignoreCase = true) -> parseVcf(text)
        else -> emptyList()
    }

    /** Yearly or birthday-titled events from an iCalendar file (Google Calendar, Outlook and old Facebook exports). */
    fun parseIcs(text: String): List<Birthday> {
        val out = mutableListOf<Birthday>()
        var inEvent = false
        var summary = ""
        var start = ""
        var rrule = ""
        for ((key, value) in lines(text)) {
            when {
                key == "BEGIN" && value.equals("VEVENT", true) -> { inEvent = true; summary = ""; start = ""; rrule = "" }
                key == "END" && value.equals("VEVENT", true) -> {
                    inEvent = false
                    val date = parseDate(start)
                    if (date != null && summary.isNotBlank() && (looksLikeBirthday(summary) || rrule.contains("YEARLY", true))) {
                        out += Birthday(cleanName(summary), date.first, date.second, date.third)
                    }
                }
                inEvent && key == "SUMMARY" -> summary = unescape(value)
                inEvent && key == "DTSTART" -> start = value
                inEvent && key == "RRULE" -> rrule = value
            }
        }
        return out.distinctBy { it.key }
    }

    /** Contacts with a BDAY from a vCard file (Google Contacts, phone contacts and most address book exports). */
    fun parseVcf(text: String): List<Birthday> {
        val out = mutableListOf<Birthday>()
        var fn = ""
        var n = ""
        var bday = ""
        for ((key, value) in lines(text)) {
            when (key) {
                "BEGIN" -> if (value.equals("VCARD", true)) { fn = ""; n = ""; bday = "" }
                "FN" -> fn = unescape(value)
                "N" -> n = unescape(value).split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ") }
                "BDAY" -> bday = value
                "END" -> if (value.equals("VCARD", true)) {
                    val date = parseDate(bday)
                    val name = fn.ifBlank { n }.trim()
                    if (date != null && name.isNotEmpty()) out += Birthday(name, date.first, date.second, date.third)
                }
            }
        }
        return out.distinctBy { it.key }
    }

    /** Unfolded lines as (property name without parameters, value). */
    private fun lines(text: String): Sequence<Pair<String, String>> =
        text.replace("\r\n", "\n").replace(Regex("""\n[ \t]"""), "").lineSequence().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            // "item1.BDAY;VALUE=date" → "BDAY"
            val key = line.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase()
            key to line.substring(colon + 1).trim()
        }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    /**
     * Accepts 1990-03-14, 19900314, 19900314T000000Z, --03-14, --0314 and 1604-03-14 (Apple's "no year").
     * Returns (month, day, year or null).
     */
    fun parseDate(raw: String): Triple<Int, Int, Int?>? {
        val v = raw.trim().substringBefore('T')
        val noYear = v.startsWith("--")
        val digits = v.filter(Char::isDigit)
        val (y, m, d) = when {
            noYear && digits.length == 4 -> Triple(null, digits.substring(0, 2), digits.substring(2, 4))
            digits.length == 8 -> Triple(digits.substring(0, 4), digits.substring(4, 6), digits.substring(6, 8))
            else -> return null
        }
        val month = m.toIntOrNull() ?: return null
        val day = d.toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val year = y?.toIntOrNull()?.takeIf { it in 1800..2200 && it != 1604 }
        return Triple(month, day, year)
    }
}

/** Android sources: calendars synced to the phone, and files picked or shared by the user. */
object BirthdaySources {
    /** Needs READ_CALENDAR. Birthday calendars and birthday-titled events count; other yearly events too, unticked. */
    fun fromCalendars(context: Context): List<Pair<Birthday, Boolean>> {
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE, CalendarContract.Events.CALENDAR_DISPLAY_NAME,
        )
        val selection = "${CalendarContract.Events.DELETED} = 0 AND (" +
            "${CalendarContract.Events.RRULE} LIKE '%YEARLY%' OR " +
            "${CalendarContract.Events.TITLE} LIKE '%birthday%' OR ${CalendarContract.Events.TITLE} LIKE '%bday%' OR " +
            "${CalendarContract.Events.CALENDAR_DISPLAY_NAME} LIKE '%birthday%')"
        val found = LinkedHashMap<String, Pair<Birthday, Boolean>>()
        runCatching {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0) ?: continue
                    val start = c.getLong(1)
                    val allDay = c.getInt(2) == 1
                    val calendar = c.getString(4).orEmpty()
                    // All-day events are stored at UTC midnight.
                    val date = Instant.ofEpochMilli(start).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
                    val likely = BirthdayParser.looksLikeBirthday(title) || BirthdayParser.looksLikeBirthday(calendar)
                    val b = Birthday(BirthdayParser.cleanName(title), date.monthValue, date.dayOfMonth, date.year.takeIf { likely && it > 1900 })
                    val prev = found[b.key]
                    if (prev == null || (!prev.second && likely)) found[b.key] = b to likely
                }
            }
        }
        return found.values.sortedWith(compareBy({ !it.second }, { it.first.month }, { it.first.day }))
    }

    /** Reads an .ics or .vcf file (up to 5 MB). */
    fun fromFile(context: Context, uri: Uri): List<Birthday> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(5 * 1024 * 1024)
            BirthdayParser.parse(String(bytes, Charsets.UTF_8))
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
