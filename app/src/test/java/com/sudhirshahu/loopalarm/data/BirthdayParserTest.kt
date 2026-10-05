package com.sudhirshahu.loopalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthdayParserTest {
    @Test fun vcardFromGoogleContacts() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Asha Verma
            N:Verma;Asha;;;
            BDAY:1990-03-14
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:No Birthday
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            N:Rao;Kiran;;;
            item1.BDAY;VALUE=date:--0702
            END:VCARD
        """.trimIndent()
        assertEquals(
            listOf(Birthday("Asha Verma", 3, 14, 1990), Birthday("Kiran Rao", 7, 2, null)),
            BirthdayParser.parse(vcf),
        )
    }

    @Test fun icsKeepsYearlyAndBirthdayEventsOnly() {
        val ics = "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\nSUMMARY:Rahul's Birthday\r\nDTSTART;VALUE=DATE:19950521\r\nRRULE:FREQ=YEARLY\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nSUMMARY:Team meeting\r\nDTSTART:20261005T093000Z\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nSUMMARY:Wedding anniversa\r\n ry\r\nDTSTART;VALUE=DATE:20101112\r\nRRULE:FREQ=YEARLY;INTERVAL=1\r\nEND:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        assertEquals(
            listOf(Birthday("Rahul", 5, 21, 1995), Birthday("Wedding anniversary", 11, 12, 2010)),
            BirthdayParser.parse(ics),
        )
    }

    @Test fun cleanNames() {
        assertEquals("Asha", BirthdayParser.cleanName("Asha's birthday"))
        assertEquals("Asha", BirthdayParser.cleanName("Asha’s Birthday"))
        assertEquals("Asha", BirthdayParser.cleanName("Birthday: Asha"))
        assertEquals("Rahul", BirthdayParser.cleanName("Happy Birthday Rahul!"))
        assertEquals("Mum", BirthdayParser.cleanName("🎂 Mum"))
    }

    @Test fun dates() {
        assertEquals(Triple(3, 14, 1990), BirthdayParser.parseDate("19900314"))
        assertEquals(Triple(3, 14, 1990), BirthdayParser.parseDate("1990-03-14T00:00:00Z"))
        assertEquals(Triple(3, 14, null), BirthdayParser.parseDate("--03-14"))
        assertEquals(Triple(3, 14, null), BirthdayParser.parseDate("1604-03-14"))
        assertNull(BirthdayParser.parseDate("not a date"))
        assertNull(BirthdayParser.parseDate("19901314"))
    }

    @Test fun leapDayRingsOn28th() {
        val a = Birthday("Leap", 2, 29).toAlarm(9 * 60, "Birthdays")
        assertEquals(1 shl 27, a.daysOfMonth)
        assertEquals(1 shl 1, a.months)
        assertEquals(false, a.repeating)
    }
}
