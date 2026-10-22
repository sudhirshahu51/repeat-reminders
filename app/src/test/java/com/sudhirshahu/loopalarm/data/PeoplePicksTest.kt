package com.sudhirshahu.loopalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PeoplePicksTest {
    @Test fun sameNumberWrittenDifferentlyCountsOnce() {
        val people = PeoplePicks.distinctPeople(
            listOf(Person("", "+91 98765 43210"), Person("Asha", "098765 43210"), Person("Ravi", "99999 11111")),
        )
        assertEquals(2, people.size)
        // newest number kept, the saved name wins over the bare number
        assertEquals(Person("Asha", "+91 98765 43210"), people[0])
        assertEquals(Person("Ravi", "99999 11111"), people[1])
    }

    @Test fun hiddenAndBlankNumbersAreDropped() {
        val people = PeoplePicks.distinctPeople(listOf(Person("", ""), Person("Unknown", "-1"), Person("", "  "), Person("Mum", "12345")))
        assertEquals(listOf(Person("Mum", "12345")), people)
    }

    @Test fun unnamedPeopleShowTheirNumber() {
        val p = PeoplePicks.distinctPeople(listOf(Person("", "555 0100"))).single()
        assertEquals("555 0100", p.name)
        assertTrue(!p.hasName)
    }

    @Test fun picksDifferentPeopleUpToTheCount() {
        val people = (1..20).map { Person("P$it", "9000000$it".padEnd(10, '0')) }
        val picked = PeoplePicks.pick(people, 5, Random(7))
        assertEquals(5, picked.size)
        assertEquals(5, picked.toSet().size)
        assertTrue(people.containsAll(picked))
        // fewer people than asked for: everyone, no repeats
        assertEquals(3, PeoplePicks.pick(people.take(3), 5, Random(1)).size)
        assertEquals(emptyList<Person>(), PeoplePicks.pick(emptyList(), 5))
    }

    @Test fun storedListRoundTrips() {
        val people = listOf(Person("Asha", "+91 98765 43210"), Person("555 0100", "555 0100"), Person("Bad\tName\nHere", "123"))
        val back = PeoplePicks.decode(PeoplePicks.encode(people))
        assertEquals(listOf(Person("Asha", "+91 98765 43210"), Person("555 0100", "555 0100"), Person("Bad Name Here", "123")), back)
        assertEquals(emptyList<Person>(), PeoplePicks.decode(""))
    }

    @Test fun differentSeedsGiveDifferentPicks() {
        val people = (1..30).map { Person("P$it", "800000${it.toString().padStart(4, '0')}") }
        assertTrue(PeoplePicks.pick(people, 5, Random(1)) != PeoplePicks.pick(people, 5, Random(2)))
    }
}
