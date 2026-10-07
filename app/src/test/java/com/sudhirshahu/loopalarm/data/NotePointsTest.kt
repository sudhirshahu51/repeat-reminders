package com.sudhirshahu.loopalarm.data

import com.sudhirshahu.loopalarm.data.NotePoints.Notes
import com.sudhirshahu.loopalarm.data.NotePoints.Point
import com.sudhirshahu.loopalarm.data.NotePoints.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class NotePointsTest {
    @Test fun plainLinesFromOlderVersionsReadAsBullets() {
        val n = NotePoints.parse("Take after dinner\n\n  Refill the box  ")
        assertEquals(Style.BULLETS, n.style)
        assertEquals(listOf(Point("Take after dinner"), Point("Refill the box")), n.points)
    }

    @Test fun roundTripsEveryStyle() {
        val points = listOf(Point("One", checked = true), Point("Two"))
        for (style in Style.entries) {
            val text = NotePoints.format(Notes(style, points))
            val back = NotePoints.parse(text)
            assertEquals(style, back.style)
            assertEquals(points.map { it.text }, back.points.map { it.text })
            if (style == Style.CHECKLIST) assertEquals(listOf(true, false), back.points.map { it.checked })
        }
    }

    @Test fun formatsMarkersAndDropsEmptyPoints() {
        assertEquals("1. Wash\n2. Dry", NotePoints.format(Notes(Style.NUMBERS, listOf(Point("Wash"), Point(" "), Point("Dry")))))
        assertEquals("[x] Pills\n[ ] Water", NotePoints.format(Notes(Style.CHECKLIST, listOf(Point("Pills", true), Point("Water")))))
        assertEquals(listOf("☑ Pills", "☐ Water"), NotePoints.displayLines("[x] Pills\n[ ] Water"))
        assertEquals(listOf("• A", "• B"), NotePoints.displayLines("- A\nB"))
    }
}
