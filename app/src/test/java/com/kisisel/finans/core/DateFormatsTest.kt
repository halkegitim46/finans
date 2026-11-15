package com.kisisel.finans.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DateFormatsTest {
    private val dt = LocalDateTime.of(2026, 11, 5, 14, 30)

    @Test fun formatsAreStable() {
        assertEquals("05.11.2026", DateFormats.DATE.format(dt.toLocalDate()))
        assertEquals("14:30", DateFormats.TIME.format(dt))
        assertEquals("05.11.2026 14:30", DateFormats.DATE_TIME.format(dt))
        assertEquals("2026-11-05_1430", DateFormats.FILE_STAMP.format(dt))
    }

    @Test fun singleDigitsArePadded() {
        assertEquals("01.02.2026", DateFormats.DATE.format(LocalDate.of(2026, 2, 1)))
        assertEquals("09:05", DateFormats.TIME.format(LocalDateTime.of(2026, 2, 1, 9, 5)))
    }

    @Test fun turkishLocaleHandlesDottedAndDotlessI() {
        assertEquals("tr", TurkishLocale.language)
        assertEquals("İSTANBUL", "istanbul".uppercase(TurkishLocale))
        assertEquals("ısı", "ISI".lowercase(TurkishLocale))
        assertEquals("SİL", "sil".uppercase(TurkishLocale))
    }
}
