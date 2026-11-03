package com.kisisel.finans.ui

import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.containsSearch
import com.kisisel.finans.ui.transactions.filterLines
import com.kisisel.finans.ui.transactions.totalForType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionFilterTest {
    private fun line(
        id: Long, name: String, type: TransactionType, amount: Long,
        desc: String = "", cancelled: Boolean = false
    ) = LedgerLine(
        LedgerEntry(id, 1, type, amount, LocalDate.of(2026, 11, 2), null, desc, null, cancelled, 0),
        name
    )

    private val lines = listOf(
        line(1, "Ahmet Dayı", TransactionType.PAID_ON_BEHALF, 150_000, "Elektrik"),
        line(2, "Ahmet Dayı", TransactionType.COLLECTION, 50_000),
        line(3, "Işıl Hanım", TransactionType.LEND, 20_000, "Harçlık"),
        line(4, "Işıl Hanım", TransactionType.LEND, 10_000, cancelled = true)
    )

    @Test fun cancelledHiddenByDefault() {
        assertEquals(listOf(1L, 2L, 3L), filterLines(lines, "", null, false).map { it.entry.id })
        assertEquals(4, filterLines(lines, "", null, true).size)
    }

    @Test fun filtersByTypeAndQuery() {
        assertEquals(listOf(3L), filterLines(lines, "", TransactionType.LEND, false).map { it.entry.id })
        assertEquals(listOf(1L, 2L), filterLines(lines, "ahmet", null, false).map { it.entry.id })
        assertEquals(listOf(1L), filterLines(lines, "elektrik", null, false).map { it.entry.id })
        assertEquals(listOf(3L), filterLines(lines, "ışıl", null, false).map { it.entry.id })
    }

    @Test fun totalOnlyForSingleTypeAndSkipsCancelled() {
        assertNull(totalForType(lines, null))
        val lend = filterLines(lines, "", TransactionType.LEND, true)
        assertEquals(20_000L, totalForType(lend, TransactionType.LEND))
    }

    @Test fun containsSearchTurkishCase() {
        assertTrue("Işıl".containsSearch("ışıl"))
        assertTrue("ISPARTA".containsSearch("isparta"))
        assertTrue("Ankara".containsSearch(""))
        assertFalse("Ankara".containsSearch("izmir"))
    }
}
