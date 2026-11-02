package com.kisisel.finans.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test fun parse_turkishFormats() {
        assertEquals(150_050L, Money.parse("1.500,50"))
        assertEquals(150_000L, Money.parse("1500"))
        assertEquals(150_000L, Money.parse("1.500"))
        assertEquals(1_250L, Money.parse("12,5"))
        assertEquals(1L, Money.parse("0,01"))
        assertEquals(150L, Money.parse("1.5"))
        assertEquals(200_000L, Money.parse("₺ 2.000,00 TL"))
    }

    @Test fun parse_rejectsInvalid() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1,505"))
        assertNull(Money.parse("1,234.56"))
        assertNull(Money.parse("12,3,4"))
        assertNull(Money.parse(",50"))
        assertNull(Money.parse("99999999999999"))
    }

    @Test fun parsePositive_rejectsZero() {
        assertNull(Money.parsePositive("0"))
        assertNull(Money.parsePositive("0,00"))
        assertEquals(1L, Money.parsePositive("0,01"))
    }

    @Test fun format_isDeterministic() {
        assertEquals("₺1.500,50", Money.format(150_050))
        assertEquals("₺0,05", Money.format(5))
        assertEquals("₺0,00", Money.format(0))
        assertEquals("-₺1.234,56", Money.format(-123_456))
        assertEquals("₺1.000.000,00", Money.format(100_000_000))
        assertEquals("1.500,00", Money.format(150_000, withSymbol = false))
    }

    @Test fun add_overflowThrows() {
        assertThrows(ArithmeticException::class.java) { Money.add(Long.MAX_VALUE, 1) }
    }

    @Test fun roundTrip() {
        for (k in listOf(1L, 99L, 100L, 123_456L, 99_999_999L)) {
            assertEquals(k, Money.parse(Money.format(k, withSymbol = false)))
        }
    }
}
