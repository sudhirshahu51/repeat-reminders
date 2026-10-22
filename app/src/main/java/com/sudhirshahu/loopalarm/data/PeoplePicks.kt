package com.sudhirshahu.loopalarm.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import kotlin.random.Random

/** Someone picked for a reminder: [name] is the saved name when there is one, otherwise the number itself. */
data class Person(val name: String, val number: String) {
    val hasName: Boolean get() = name != number
}

/**
 * "Remind me of people": when the reminder rings, a few random people from the phone's contacts are shown, as a nudge
 * to get in touch. Reads the contacts on the phone only (the permission is asked when the option is switched on); the
 * picks are kept on the reminder so they can still be seen on its page after the ring has ended.
 */
object PeoplePicks {
    const val DEFAULT_COUNT = 5
    val COUNTS = listOf(3, 5, 7, 10)

    const val PERMISSION = Manifest.permission.READ_CONTACTS

    private const val CONTACT_ROWS = 20_000

    /** A real number has at least 3 digits. */
    private const val MIN_DIGITS = 3

    private const val SEP = '\u0009' // tab between name and number
    private const val EOL = '\n'

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Everyone in the contacts who has a number; empty without permission. Call off the main thread. */
    fun all(context: Context): List<Person> {
        if (!hasPermission(context)) return emptyList()
        return runCatching { fromContacts(context) }.getOrDefault(emptyList())
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

    /**
     * Drops entries without a real number and entries for a number that is already listed (the same number written
     * differently, +91 98…, 098…, counts once); a saved name wins over a bare number.
     */
    fun distinctPeople(rows: List<Person>): List<Person> {
        val byKey = LinkedHashMap<String, Person>()
        for (row in rows) {
            val number = row.number.trim()
            val key = number.filter { it.isDigit() }.takeLast(10)
            if (key.length < MIN_DIGITS) continue
            val name = row.name.trim().ifEmpty { number }
            val known = byKey[key]
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
