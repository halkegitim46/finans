package com.kisisel.finans.reminder

import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.ReminderCandidate
import com.kisisel.finans.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun cand(
        due: String,
        days: Set<Int> = setOf(7, 3, 1, 0),
        every: Int = 1,
        amount: Long? = 150_000,
        status: OccurrenceStatus = OccurrenceStatus.UNPAID,
        id: Long = 1
    ) = ReminderCandidate(
        InvoiceOccurrence(id, 1, "Dayım elektrik", InvoiceType.ELECTRICITY, null, null, "k", LocalDate.parse(due), amount, status, null),
        days, every
    )

    private fun plan(c: ReminderCandidate, sent: Set<String> = emptySet()) = ReminderPlanner.plan(listOf(c), today, sent)

    @Test fun beforeDueOnlyOnSelectedDays() {
        assertEquals(1, plan(cand("2026-11-17")).size)   // 7 gün
        assertEquals(1, plan(cand("2026-11-13")).size)   // 3 gün
        assertEquals(1, plan(cand("2026-11-11")).size)   // 1 gün
        assertEquals(1, plan(cand("2026-11-10")).size)   // vade günü
        assertEquals(0, plan(cand("2026-11-15")).size)   // 5 gün: seçili değil
        assertEquals(0, plan(cand("2026-11-30")).size)
    }

    @Test fun contentHasNameAmountDueDate() {
        val p = plan(cand("2026-11-13")).single()
        assertEquals("Fatura hatırlatma: Dayım elektrik", p.title)
        assertTrue(p.text.contains("₺1.500,00"))
        assertTrue(p.text.contains("13.11.2026"))
        assertTrue(p.text.contains("3 gün sonra"))
        assertEquals("1:d3", p.key)
    }

    @Test fun dueDayText() {
        assertTrue(plan(cand("2026-11-10")).single().text.contains("Bugün son ödeme günü"))
    }

    @Test fun missingAmountText() {
        assertTrue(plan(cand("2026-11-10", amount = null)).single().text.contains("Tutar girilmedi"))
    }

    @Test fun overdueDailyByDefault() {
        val p = plan(cand("2026-11-07")).single()      // 3 gün gecikti
        assertEquals("1:o3", p.key)
        assertTrue(p.text.contains("3 gün gecikti"))
    }

    @Test fun overdueEveryThreeDays() {
        // Gecikme günleri 1, 4, 7 ... bildirilir
        assertEquals(1, plan(cand("2026-11-09", every = 3)).size)   // 1. gün
        assertEquals(0, plan(cand("2026-11-08", every = 3)).size)   // 2. gün
        assertEquals(0, plan(cand("2026-11-07", every = 3)).size)   // 3. gün
        assertEquals(1, plan(cand("2026-11-06", every = 3)).size)   // 4. gün
    }

    @Test fun overdueRemindersCanBeDisabled() {
        assertEquals(0, plan(cand("2026-11-01", every = 0)).size)
    }

    @Test fun alreadySentKeysAreSkipped() {
        assertEquals(0, plan(cand("2026-11-13"), sent = setOf("1:d3")).size)
        assertEquals(1, plan(cand("2026-11-13"), sent = setOf("1:d7")).size)
    }

    @Test fun paidAndCancelledAreSkipped() {
        assertEquals(0, plan(cand("2026-11-10", status = OccurrenceStatus.PAID)).size)
        assertEquals(0, plan(cand("2026-11-10", status = OccurrenceStatus.CANCELLED)).size)
    }

    @Test fun keyParsing() {
        assertEquals(12L, occurrenceIdOfKey("12:d3"))
        assertEquals(7L, occurrenceIdOfKey("7:o2"))
        assertNull(occurrenceIdOfKey("x:d3"))
    }

    // ---------------- alacak / borç vadesi ----------------
    private fun claim(
        id: Long, due: String?, type: TransactionType = TransactionType.LEND, amount: Long = 100_000, allocated: Long = 30_000,
        occurrenceId: Long? = null, desc: String = ""
    ) = ClaimLine(id, 5, "Ayşe", type, amount, allocated, LocalDate.of(2026, 10, 1), due?.let { LocalDate.parse(it) }, occurrenceId, desc)

    private fun planClaim(c: ClaimLine, sent: Set<String> = emptySet()) = ReminderPlanner.planClaims(listOf(c), today, sent)

    @Test fun claimRemindersOnThreeOneAndDueDay() {
        assertEquals(1, planClaim(claim(1, "2026-11-13")).size)      // 3 gün
        assertEquals(1, planClaim(claim(1, "2026-11-11")).size)      // 1 gün
        assertEquals(1, planClaim(claim(1, "2026-11-10")).size)      // vade günü
        assertEquals(0, planClaim(claim(1, "2026-11-12")).size)      // 2 gün: yok
        assertEquals(0, planClaim(claim(1, "2026-11-20")).size)      // 10 gün: yok
    }

    @Test fun overdueClaimRemindersEveryThreeDaysStartingFirstDay() {
        assertEquals(1, planClaim(claim(1, "2026-11-09")).size)      // 1. gün
        assertEquals(0, planClaim(claim(1, "2026-11-08")).size)      // 2. gün
        assertEquals(0, planClaim(claim(1, "2026-11-07")).size)      // 3. gün
        assertEquals(1, planClaim(claim(1, "2026-11-06")).size)      // 4. gün
    }

    @Test fun claimsWithoutDueInvoiceDerivedOrSettledAreSkipped() {
        assertEquals(0, planClaim(claim(1, null)).size)
        assertEquals(0, planClaim(claim(1, "2026-11-13", occurrenceId = 9)).size)
        assertEquals(0, planClaim(claim(1, "2026-11-13", amount = 100_000, allocated = 100_000)).size)
    }

    @Test fun claimReminderContentAndTarget() {
        val r = planClaim(claim(7, "2026-11-13", desc = "harçlık")).single()
        assertEquals("c7:d3", r.key)
        assertEquals("Alacak vadesi: Ayşe", r.title)
        assertTrue(r.text.contains("harçlık"))
        assertTrue(r.text.contains("₺700,00"))                         // açık tutar 100.000 - 30.000
        assertTrue(r.text.contains("Vade: 13.11.2026"))
        assertTrue(r.text.contains("3 gün sonra vade"))
        assertEquals(5L, r.personId)
        assertEquals(null, r.agendaId)

        val payable = planClaim(claim(8, "2026-11-10", type = TransactionType.BORROW)).single()
        assertEquals("Borç vadesi: Ayşe", payable.title)
        assertTrue(payable.text.contains("Bugün vade günü"))
        assertEquals("c8:o4", planClaim(claim(8, "2026-11-06")).single().key)      // gecikmenin 4. günü
    }

    @Test fun sentClaimKeysAreNotRepeated() {
        assertEquals(0, planClaim(claim(1, "2026-11-13"), sent = setOf("c1:d3")).size)
        assertEquals(1, planClaim(claim(1, "2026-11-13"), sent = setOf("c1:d1")).size)
    }

    // ---------------- ajanda notları ----------------
    private val now = LocalDateTime.of(2026, 11, 10, 13, 30)

    private fun note(
        id: Long = 1, date: String = "2026-11-10", time: LocalTime? = LocalTime.of(14, 30), remind: Int? = 60,
        done: Boolean = false, person: String? = "Ahmet", text: String = "borcu hatırlat"
    ) = AgendaItem(id, "Ara", text, LocalDate.parse(date), time, if (person != null) 3 else null, person, remind, done, if (done) 1 else null)

    private fun planNote(n: AgendaItem, at: LocalDateTime = now, sent: Set<String> = emptySet()) =
        ReminderPlanner.planAgenda(listOf(n), at, sent)

    @Test fun agendaReminderIsDueAtReminderTime() {
        val r = planNote(note()).single()                              // 14:30 - 1 saat = 13:30 = şimdi
        assertEquals("Ajanda: Ara", r.title)
        assertTrue(r.text.contains("10.11.2026 14:30"))
        assertTrue(r.text.contains("Ahmet"))
        assertTrue(r.text.contains("borcu hatırlat"))
        assertEquals(1L, r.agendaId)
        assertTrue(r.key.startsWith("a1:t"))
        assertEquals(0, planNote(note(), at = now.minusMinutes(1)).size)   // henüz erken
    }

    @Test fun doneOrReminderlessNotesAreSkipped() {
        assertEquals(0, planNote(note(done = true)).size)
        assertEquals(0, planNote(note(remind = null)).size)
    }

    @Test fun staleAgendaRemindersAreDropped() {
        val remindAt = now                                              // hatırlatma zamanı 13:30
        assertEquals(1, planNote(note(), at = remindAt.plusHours(23).plusMinutes(59)).size)
        assertEquals(0, planNote(note(), at = remindAt.plusHours(24)).size)
    }

    @Test fun allDayNoteRemindsAtNineAm() {
        val n = note(time = null, remind = 0, date = "2026-11-10")
        assertEquals(0, planNote(n, at = LocalDateTime.of(2026, 11, 10, 8, 59)).size)
        val r = planNote(n, at = LocalDateTime.of(2026, 11, 10, 9, 0)).single()
        assertTrue(r.text.contains("(gün boyu)"))
    }

    @Test fun oneDayBeforeReminder() {
        val n = note(date = "2026-11-11", time = LocalTime.of(10, 0), remind = 1440)   // 10 Kas 10:00
        assertEquals(0, planNote(n, at = LocalDateTime.of(2026, 11, 10, 9, 59)).size)
        assertEquals(1, planNote(n, at = LocalDateTime.of(2026, 11, 10, 10, 0)).size)
    }

    @Test fun editingTimeChangesKeyAndSentKeyBlocksOnlyTheSame() {
        val first = planNote(note()).single()
        assertEquals(0, planNote(note(), sent = setOf(first.key)).size)
        val moved = note(time = LocalTime.of(15, 0), remind = 60)
        val later = planNote(moved, at = LocalDateTime.of(2026, 11, 10, 14, 0), sent = setOf(first.key)).single()
        assertTrue(later.key != first.key)
    }

    @Test fun nextAgendaReminderPicksEarliestFutureOne() {
        val items = listOf(
            note(1, date = "2026-11-12", time = LocalTime.of(10, 0), remind = 0),
            note(2, date = "2026-11-11", time = LocalTime.of(8, 0), remind = 60),    // 07:00 -> en yakın gelecek
            note(3, date = "2026-11-10", time = LocalTime.of(9, 0), remind = 0),     // geçmiş
            note(4, date = "2026-11-11", time = LocalTime.of(6, 0), remind = 0, done = true),
            note(5, date = "2026-11-11", time = LocalTime.of(6, 0), remind = null)
        )
        assertEquals(LocalDateTime.of(2026, 11, 11, 7, 0), ReminderPlanner.nextAgendaReminder(items, now))
        assertEquals(null, ReminderPlanner.nextAgendaReminder(emptyList(), now))
        assertEquals(null, ReminderPlanner.nextAgendaReminder(listOf(note(3, date = "2026-11-10", time = LocalTime.of(9, 0), remind = 0)), now))
    }

    @Test fun ownerOfKeyIdentifiesKinds() {
        assertEquals("12", ownerOfKey("12:d3"))
        assertEquals("c7", ownerOfKey("c7:o3"))
        assertEquals("a5", ownerOfKey("a5:t123"))
        assertNull(occurrenceIdOfKey("c7:o3"))
        assertNull(occurrenceIdOfKey("a5:t123"))
    }
}
