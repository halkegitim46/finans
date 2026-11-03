package com.kisisel.finans.ui

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.ui.home.splitInvoices
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeLogicTest {
    private val today = LocalDate.of(2026, 11, 2)

    private fun occ(id: Long, due: String, status: OccurrenceStatus = OccurrenceStatus.UNPAID) =
        InvoiceOccurrence(
            id, 1, "Elektrik", InvoiceType.ELECTRICITY, null, null, "k$id",
            LocalDate.parse(due), 1_000, status, null
        )

    @Test fun splitsOverdueAndUpcoming() {
        val list = listOf(
            occ(1, "2026-10-20"),                              // geciken
            occ(2, "2026-11-02"),                              // bugün -> yaklaşan
            occ(3, "2026-12-02"),                              // tam 30 gün -> yaklaşan
            occ(4, "2026-12-03"),                              // 31 gün -> dışarıda
            occ(5, "2026-10-01", OccurrenceStatus.PAID),       // ödenmiş
            occ(6, "2026-10-05", OccurrenceStatus.CANCELLED)   // iptal
        )
        val s = splitInvoices(list, today)
        assertEquals(listOf(1L), s.overdue.map { it.id })
        assertEquals(listOf(2L, 3L), s.upcoming.map { it.id })
    }

    @Test fun sortedByDueDate() {
        val s = splitInvoices(listOf(occ(1, "2026-11-20"), occ(2, "2026-11-05")), today)
        assertEquals(listOf(2L, 1L), s.upcoming.map { it.id })
    }

    @Test fun emptyInput() {
        val s = splitInvoices(emptyList(), today)
        assertEquals(0, s.overdue.size + s.upcoming.size)
    }
}
