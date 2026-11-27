package dev.orb.app

import dev.orb.app.data.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class ReminderTimeTest {
    @Test
    fun parsesIsoWithOffset() {
        val moment = ReminderTime.parse("2026-11-28T08:30:00-03:00")
        assertEquals(2026, moment?.year)
        assertEquals(8, moment?.hour)
        assertEquals(30, moment?.minute)
    }

    @Test
    fun parsesUtc() {
        val moment = ReminderTime.parse("2026-11-28T11:30:00Z")
        assertEquals(11, moment?.withZoneSameInstant(ZoneOffset.UTC)?.hour)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(ReminderTime.parse("amanhã de manhã"))
    }
}
