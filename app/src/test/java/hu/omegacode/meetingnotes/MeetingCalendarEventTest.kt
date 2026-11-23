package hu.omegacode.meetingnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MeetingCalendarEventTest {
    private val budapest = ZoneId.of("Europe/Budapest")

    @Test
    fun `JSON budapesti idopontkent dolgozza fel`() {
        val event = MeetingCalendarEvent.fromApi(
            title = "Megbeszélés – 2026. 11. 23.",
            startedAt = "2026-11-23T13:57",
            totalSeconds = 69.7,
            deviceZone = budapest
        )

        assertEquals("Megbeszélés – 2026. 11. 23.", event.title)
        assertEquals(Instant.parse("2026-11-23T12:57:00Z").toEpochMilli(), event.beginTimeMillis)
        assertEquals(Instant.parse("2026-11-23T12:59:00Z").toEpochMilli(), event.endTimeMillis)
        assertEquals("Europe/Budapest", event.zoneId.id)
        assertTrue(event.assumedDeviceZone)
        assertTrue(event.hasRecordingDuration)
        assertTrue(event.description.contains("2 perc"))
    }

    @Test
    fun `idozona eltolassal ugyanazt a pillanatot kapjuk`() {
        val event = MeetingCalendarEvent.fromApi(
            "Próba", "2026-11-23T13:57+01:00", 69.7, ZoneId.of("UTC")
        )

        assertEquals(Instant.parse("2026-11-23T12:57:00Z").toEpochMilli(), event.beginTimeMillis)
        assertFalse(event.assumedDeviceZone)
        assertEquals("UTC", event.zoneId.id)
    }

    @Test
    fun `a befejezes atlephet a kovetkezo napra`() {
        val event = MeetingCalendarEvent.fromApi("Éjféli", "2026-11-23T23:59", 180.0, budapest)

        assertEquals(180_000L, event.endTimeMillis - event.beginTimeMillis)
        assertTrue(event.formatEnd().contains("2026. 11. 24. 00:02"))
    }

    @Test
    fun `hianyzo vagy ervenytelen kezdes hibara fut`() {
        for (badValue in listOf(null, "", "null", "nem-dátum")) {
            assertThrows(IllegalArgumentException::class.java) {
                MeetingCalendarEvent.fromApi("Próba", badValue, 120.0, budapest)
            }
        }
    }

    @Test
    fun `hianyzo felvetelhossznal egy ora az alapertelmezett ido`() {
        val event = MeetingCalendarEvent.fromApi("Próba", "2026-11-23T13:57", null, budapest)

        assertEquals(3_600_000L, event.endTimeMillis - event.beginTimeMillis)
        assertFalse(event.hasRecordingDuration)
        assertTrue(event.description.contains("alapértelmezett"))
    }

    @Test
    fun `ures cimhez alapertelmezett megnevezest ad`() {
        val event = MeetingCalendarEvent.fromApi("   ", "2026-11-23T13:57", 60.0, budapest)

        assertEquals("Megbeszélés", event.title)
        assertEquals(60_000L, event.endTimeMillis - event.beginTimeMillis)
    }
}
