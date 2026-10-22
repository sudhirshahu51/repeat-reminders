package com.sudhirshahu.loopalarm.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import kotlin.random.Random

/** Someone picked for a reminder: [name] is the saved name when there is one, otherwise the number itself. */
data class Person(val name: String, val number: String) {
    val hasName: Boolean get() = name != number
}

/** Where "remind me of people" takes its people from. The order is stored in [Alarm.peopleFrom]; only append. */
enum class PeopleSource(val label: String, val permission: String) {
    CONTACTS("Contacts", Manifest.permission.READ_CONTACTS),
    CALL_LOG("Call log", Manifest.permission.READ_CALL_LOG),
}

/**
 * "Remind me of people": when the reminder rings, a few random people from the phone's contacts (or recent call log)
 * are shown, as a nudge to get in touch. Reads on the phone only (the permission is asked when the option is switched
 * on); the picks are kept on the reminder so they can still be seen on its page after the ring has ended.
 */
object PeoplePicks {
    const val DEFAULT_COUNT = 5
    val COUNTS = listOf(3, 5, 7, 10)

    /** How many recent call log entries are looked at; plenty for a mix of people without reading a huge log. */
    private const val CALL_LOG_ROWS = 300
    private const val CONTACT_ROWS = 20_000

    private const val SEP = '\u0009' // tab between name and number
    private const val EOL = '\n'

    fun hasPermission(context: Context, source: PeopleSource): Boolean =
        ContextCompat.checkSelfPermission(context, source.permission) == PackageManager.PERMISSION_GRANTED

    /** Everyone available from [source], or empty without permission. Call off the main thread. */
    fun all(context: Context, source: PeopleSource): List<Person> {
        if (!hasPermission(context, source)) return emptyList()
        return runCatching {
            when (source) {
                PeopleSource.CONTACTS -> fromContacts(context)
                PeopleSource.CALL_LOG -> fromCallLog(context)
            }
        }.getOrDefault(emptyList())
    }

    /** One entry per contact that has a phone number, preferring the mobile number. */
    private fun fromContacts(context: Context): List<Person> {
        val byContact = LinkedHashMap<Long, Person>()
        val isMobile = HashSet<Long>()
        context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE), null, null, null,
        )?.use { c ->
            var rows = 0
            while (c.moveToNext() && rows++ < CONTACT_ROWS) {
                val id = c.getLong(0)
                val number = c.getString(2).orEmpty().trim()
                if (number.filter { it.isDigit() }.length < MIN_DIGITS) continue
                val mobile = c.getInt(3) == Phone.TYPE_MOBILE
                if (id !in byContact || (mobile && id !in isMobile)) {
                    byContact[id] = Person(c.getString(1).orEmpty().trim().ifEmpty { number }, number)
                    if (mobile) isMobile += id
                }
            }
        }
        return distinctPeople(byContact.values.toList())
    }

    /** Distinct people from the recent log, newest first. */
    private fun fromCallLog(context: Context): List<Person> {
        val rows = mutableListOf<Person>()
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, CALL_LOG_ROWS)
        }
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME), query, null,
        )?.use { c ->
            while (c.moveToNext()) rows += Person(name = c.getString(1).orEmpty(), number = c.getString(0).orEmpty())
        }
        return distinctPeople(rows)
    }

    /** A real number has at least 3 digits; hidden callers are logged as "", "-1" or "-2" on many phones. */
    private const val MIN_DIGITS = 3

    /** Same number written differently (+91 98…, 098…) counts once; hidden or blank numbers are dropped. */
    fun distinctPeople(rows: List<Person>): List<Person> {
        val byKey = LinkedHashMap<String, Person>()
        for (row in rows) {
            val number = row.number.trim()
            val key = number.filter { it.isDigit() }.takeLast(10)
            if (key.length < MIN_DIGITS) continue
            val name = row.name.trim().ifEmpty { number }
            val known = byKey[key]
            // Keep the newest entry's number, but prefer a saved name from any entry.
            if (known == null) byKey[key] = Person(name, number)
            else if (!known.hasName && name != number) byKey[key] = Person(name, known.number)
        }
        return byKey.values.toList()
    }

    /** [count] different people, chosen at random. */
    fun pick(people: List<Person>, count: Int, random: Random = Random.Default): List<Person> =
        people.shuffled(random).take(count.coerceAtLeast(0))

    /** The list as stored on a reminder: one "name<TAB>number" per line. */
    fun encode(people: List<Person>): String = people.joinToString(EOL.toString()) { "${it.name.clean()}$SEP${it.number.clean()}" }

    fun decode(text: String): List<Person> = text.split(EOL).mapNotNull { line ->
        val parts = line.split(SEP, limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) Person(parts[0].ifBlank { parts[1] }, parts[1]) else null
    }

    private fun String.clean() = map { if (it == SEP || it == EOL || it == '\r') ' ' else it }.joinToString("").trim()
}
