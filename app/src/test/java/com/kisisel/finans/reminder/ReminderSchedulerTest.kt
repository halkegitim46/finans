package com.kisisel.finans.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {
    private val hourMs = 3_600_000L

    @Test fun beforeNineWaitsUntilToday() {
        assertEquals(hourMs, ReminderScheduler.initialDelayMillis(LocalDateTime.of(2026, 11, 2, 8, 0), 9))
    }

    @Test fun exactlyNineWaitsFullDay() {
        assertEquals(24 * hourMs, ReminderScheduler.initialDelayMillis(LocalDateTime.of(2026, 11, 2, 9, 0), 9))
    }

    @Test fun afterNineWaitsUntilTomorrow() {
        assertEquals(13 * hourMs, ReminderScheduler.initialDelayMillis(LocalDateTime.of(2026, 11, 2, 20, 0), 9))
    }

    @Test fun midnightEdge() {
        assertEquals(9 * hourMs, ReminderScheduler.initialDelayMillis(LocalDateTime.of(2026, 11, 2, 0, 0), 9))
    }
}
