package com.kisisel.finans.agenda

import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AgendaBuilderTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun d(s: String) = LocalDate.parse(s)

    private fun inv(id: Long, due: String, status: OccurrenceStatus = OccurrenceStatus.UNPAID) =
        InvoiceOccurrence(id, 1, "Fatura$id", InvoiceType.WATER, null, null, "k$id", d(due), 1_000, status, null)

    private fun claim(id: Long, due: String?, open: Long = 5_000, occurrenceId: Long? = null) =
        ClaimLine(id, 1, "Ayşe", TransactionType.LEND, 5_000, 5_000 - open, d("2026-10-01"), due?.let { d(it) }, occurrenceId, "")

    private fun note(id: Long, date: String, time: LocalTime? = null, done: Boolean = false, doneAt: Long? = null) =
        AgendaItem(id, "Not$id", "", d(date), time, null, null, null, done, doneAt)

    private fun ids(entries: List<AgendaEntry>) = entries.map {
        when (it) {
            is AgendaEntry.Invoice -> "I${it.occurrence.id}"
            is AgendaEntry.Claim -> "C${it.claim.entryId}"
            is AgendaEntry.Note -> "N${it.item.id}"
        }
    }

    @Test fun overdueDaysAndLaterAreSeparated() {
        val view = AgendaBuilder.build(
            occurrences = listOf(inv(1, "2026-11-05"), inv(2, "2026-11-10"), inv(3, "2026-11-20"), inv(4, "2026-12-30")),
            claims = listOf(claim(1, "2026-11-08"), claim(2, "2026-11-10")),
            notes = listOf(note(1, "2026-11-09"), note(2, "2026-11-10", LocalTime.of(18, 0)), note(3, "2026-11-10", LocalTime.of(9, 0)), note(4, "2026-11-10")),
            today = today
        )
        assertEquals(listOf("I1", "C1", "N1"), ids(view.overdue))                         // eskiden yeniye
        assertEquals(listOf(d("2026-11-10"), d("2026-11-20")), view.days.map { it.date })
        // Aynı gün: gün boyu (fatura < alacak < not), sonra saate göre
        assertEquals(listOf("I2", "C2", "N4", "N3", "N2"), ids(view.days[0].entries))
        assertEquals(listOf("I3"), ids(view.days[1].entries))
        assertEquals(1, view.laterCount)                                                  // 30 Aralık > bugün + 30 gün
    }

    @Test fun paidCancelledAndIrrelevantClaimsAreExcluded() {
        val view = AgendaBuilder.build(
            occurrences = listOf(inv(1, "2026-11-12", OccurrenceStatus.PAID), inv(2, "2026-11-12", OccurrenceStatus.CANCELLED)),
            claims = listOf(
                claim(1, null),                              // vadesiz
                claim(2, "2026-11-12", occurrenceId = 7),    // faturadan doğan
                claim(3, "2026-11-12", open = 0)             // tamamen kapanmış
            ),
            notes = emptyList(),
            today = today
        )
        assertTrue(view.overdue.isEmpty())
        assertTrue(view.days.isEmpty())
        assertEquals(0, view.laterCount)
    }

    @Test fun doneNotesLeaveTimelineAndAreListedNewestFirst() {
        val view = AgendaBuilder.build(
            occurrences = emptyList(),
            claims = emptyList(),
            notes = listOf(
                note(1, "2026-11-10", done = true, doneAt = 100),
                note(2, "2026-11-10", done = true, doneAt = 300),
                note(3, "2026-11-10", done = true, doneAt = 200),
                note(4, "2026-11-11")
            ),
            today = today
        )
        assertEquals(listOf("N4"), ids(view.days.flatMap { it.entries }))
        assertEquals(listOf(2L, 3L, 1L), view.done.map { it.id })
    }

    @Test fun doneListIsCappedAtTwenty() {
        val many = (1L..30L).map { note(it, "2026-11-10", done = true, doneAt = it) }
        val view = AgendaBuilder.build(emptyList(), emptyList(), many, today)
        assertEquals(20, view.done.size)
        assertEquals(30L, view.done.first().id)
    }

    @Test fun windowBoundariesAreInclusive() {
        val view = AgendaBuilder.build(emptyList(), emptyList(), listOf(note(1, "2026-12-10"), note(2, "2026-12-11")), today, windowDays = 30)
        assertEquals(listOf("N1"), ids(view.days.flatMap { it.entries }))
        assertEquals(1, view.laterCount)
    }

    @Test fun overdueNoteDisappearsWhenDone() {
        val open = AgendaBuilder.build(emptyList(), emptyList(), listOf(note(1, "2026-11-01")), today)
        assertEquals(1, open.overdue.size)
        val done = AgendaBuilder.build(emptyList(), emptyList(), listOf(note(1, "2026-11-01", done = true, doneAt = 1)), today)
        assertTrue(done.overdue.isEmpty())
    }

    @Test fun emptyInputGivesEmptyView() {
        val view = AgendaBuilder.build(emptyList(), emptyList(), emptyList(), today)
        assertTrue(view.overdue.isEmpty() && view.days.isEmpty() && view.done.isEmpty())
        assertEquals(0, view.laterCount)
    }
}
