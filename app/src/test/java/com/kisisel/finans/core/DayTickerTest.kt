package com.kisisel.finans.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayTickerTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun millisOf(day: String, hour: Int) =
        LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** MutableClock UTC kullandığından Türkiye saatine göre gün sınırı için sabit bölge ile sarmalanır. */
    private class IstanbulClock(var millis: Long) : java.time.Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Europe/Istanbul")
        override fun withZone(zone: ZoneId): java.time.Clock = this
        override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(millis)
    }

    @Test fun startsWithCurrentDay() {
        val clock = IstanbulClock(millisOf("2026-11-04", 10))
        assertEquals(LocalDate.parse("2026-11-04"), DayTicker(clock).today.value)
    }

    @Test fun refreshPicksUpNewDay() {
        val clock = IstanbulClock(millisOf("2026-11-04", 23))
        val ticker = DayTicker(clock)
        clock.millis = millisOf("2026-11-05", 0) + 60_000
        assertEquals(LocalDate.parse("2026-11-04"), ticker.today.value)   // henüz yenilenmedi
        ticker.refresh()
        assertEquals(LocalDate.parse("2026-11-05"), ticker.today.value)
    }

    @Test fun refreshOnSameDayKeepsValue() {
        val clock = IstanbulClock(millisOf("2026-11-04", 8))
        val ticker = DayTicker(clock)
        clock.millis = millisOf("2026-11-04", 20)
        ticker.refresh()
        assertEquals(LocalDate.parse("2026-11-04"), ticker.today.value)
    }

    @Test fun monthChangeIsVisible() {
        val clock = IstanbulClock(millisOf("2026-11-30", 22))
        val ticker = DayTicker(clock)
        clock.millis = millisOf("2026-12-01", 9)
        ticker.refresh()
        assertEquals(12, ticker.today.value.monthValue)
    }
}
