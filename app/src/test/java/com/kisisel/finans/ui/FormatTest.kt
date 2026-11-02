package com.kisisel.finans.ui

import com.kisisel.finans.data.mapper.formatReminderDays
import com.kisisel.finans.data.mapper.parseReminderDays
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.ui.common.dueStatusText
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun dueStatus() {
        assertEquals("3 gün kaldı", dueStatusText(OccurrenceStatus.UNPAID, 3))
        assertEquals("Bugün son gün", dueStatusText(OccurrenceStatus.UNPAID, 0))
        assertEquals("2 gün gecikti", dueStatusText(OccurrenceStatus.UNPAID, -2))
        assertEquals("Ödendi", dueStatusText(OccurrenceStatus.PAID, -10))
        assertEquals("İptal edildi", dueStatusText(OccurrenceStatus.CANCELLED, 5))
    }

    @Test fun reminderDaysRoundTrip() {
        assertEquals("7,3,1,0", formatReminderDays(setOf(0, 1, 3, 7)))
        assertEquals(setOf(7, 3, 1, 0), parseReminderDays("7,3,1,0"))
        assertEquals(emptySet<Int>(), parseReminderDays(""))
        assertEquals(setOf(3), parseReminderDays("x, 3 ,"))
    }
}
