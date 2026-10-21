package com.sudhirshahu.loopalarm.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlin.random.Random

/** Someone from the call log: [name] is the saved contact name when the log has one, otherwise the number itself. */
data class CallPick(val name: String, val number: String) {
    val hasName: Boolean get() = name != number
}

/**
 * "Call someone" reminders: when the reminder rings, a few random people from the recent call log are suggested.
 * Reads the call log on the phone only (READ_CALL_LOG, asked when the option is switched on); the picks are kept
 * on the reminder so they can be called from its page after the ring has ended.
 */
object CallPicks {
    const val DEFAULT_COUNT = 5
    val COUNTS = listOf(3, 5, 7, 10)

    /** How many recent log entries are looked at; plenty for a mix of people without reading a huge log. */
    private const val LOOK_BACK_ROWS = 300

    private const val SEP = '\u0009' // tab between name and number
    private const val EOL = '\n'

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Distinct people from the recent log, newest first; empty without permission. Call off the main thread. */
    fun recent(context: Context): List<CallPick> {
        if (!hasPermission(context)) return emptyList()
        val rows = mutableListOf<CallPick>()
        runCatching {
            val query = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
                putInt(ContentResolver.QUERY_ARG_LIMIT, LOOK_BACK_ROWS)
            }
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME), query, null,
            )?.use { c ->
                while (c.moveToNext()) rows += CallPick(name = c.getString(1).orEmpty(), number = c.getString(0).orEmpty())
            }
        }
        return distinctPeople(rows)
    }

    /** Same number written differently (+91 98…, 098…) counts once; hidden or blank numbers are dropped. */
    fun distinctPeople(rows: List<CallPick>): List<CallPick> {
        val byKey = LinkedHashMap<String, CallPick>()
        for (row in rows) {
            val number = row.number.trim()
            val key = number.filter { it.isDigit() }.takeLast(10)
            // Hidden callers are logged as "", "-1" or "-2" on many phones; a real number has at least 3 digits.
            if (key.length < 3) continue
            val name = row.name.trim().ifEmpty { number }
            val known = byKey[key]
            // Keep the newest entry's number, but prefer a saved name from any entry.
            if (known == null) byKey[key] = CallPick(name, number)
            else if (!known.hasName && name != number) byKey[key] = CallPick(name, known.number)
        }
        return byKey.values.toList()
    }

    /** [count] different people, chosen at random. */
    fun pick(people: List<CallPick>, count: Int, random: Random = Random.Default): List<CallPick> =
        people.shuffled(random).take(count.coerceAtLeast(0))

    /** The list as stored on a reminder: one "name<TAB>number" per line. */
    fun encode(people: List<CallPick>): String = people.joinToString(EOL.toString()) { "${it.name.clean()}$SEP${it.number.clean()}" }

    fun decode(text: String): List<CallPick> = text.split(EOL).mapNotNull { line ->
        val parts = line.split(SEP, limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) CallPick(parts[0].ifBlank { parts[1] }, parts[1]) else null
    }

    private fun String.clean() = map { if (it == SEP || it == EOL || it == '\r') ' ' else it }.joinToString("").trim()
}
