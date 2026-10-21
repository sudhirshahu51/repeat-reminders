package com.sudhirshahu.loopalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CallPicksTest {
    @Test fun sameNumberWrittenDifferentlyCountsOnce() {
        val people = CallPicks.distinctPeople(
            listOf(CallPick("", "+91 98765 43210"), CallPick("Asha", "098765 43210"), CallPick("Ravi", "99999 11111")),
        )
        assertEquals(2, people.size)
        // newest number kept, the saved name wins over the bare number
        assertEquals(CallPick("Asha", "+91 98765 43210"), people[0])
        assertEquals(CallPick("Ravi", "99999 11111"), people[1])
    }

    @Test fun hiddenAndBlankNumbersAreDropped() {
        val people = CallPicks.distinctPeople(listOf(CallPick("", ""), CallPick("Unknown", "-1"), CallPick("", "  "), CallPick("Mum", "12345")))
        assertEquals(listOf(CallPick("Mum", "12345")), people)
    }

    @Test fun unnamedPeopleShowTheirNumber() {
        val p = CallPicks.distinctPeople(listOf(CallPick("", "555 0100"))).single()
        assertEquals("555 0100", p.name)
        assertTrue(!p.hasName)
    }

    @Test fun picksDifferentPeopleUpToTheCount() {
        val people = (1..20).map { CallPick("P$it", "9000000$it".padEnd(10, '0')) }
        val picked = CallPicks.pick(people, 5, Random(7))
        assertEquals(5, picked.size)
        assertEquals(5, picked.toSet().size)
        assertTrue(people.containsAll(picked))
        // fewer people than asked for: everyone, no repeats
        assertEquals(3, CallPicks.pick(people.take(3), 5, Random(1)).size)
        assertEquals(emptyList<CallPick>(), CallPicks.pick(emptyList(), 5))
    }

    @Test fun storedListRoundTrips() {
        val people = listOf(CallPick("Asha", "+91 98765 43210"), CallPick("555 0100", "555 0100"), CallPick("Bad\tName\nHere", "123"))
        val back = CallPicks.decode(CallPicks.encode(people))
        assertEquals(listOf(CallPick("Asha", "+91 98765 43210"), CallPick("555 0100", "555 0100"), CallPick("Bad Name Here", "123")), back)
        assertEquals(emptyList<CallPick>(), CallPicks.decode(""))
    }

    @Test fun differentSeedsGiveDifferentPicks() {
        val people = (1..30).map { CallPick("P$it", "800000${it.toString().padStart(4, '0')}") }
        assertTrue(CallPicks.pick(people, 5, Random(1)) != CallPicks.pick(people, 5, Random(2)))
    }
}
