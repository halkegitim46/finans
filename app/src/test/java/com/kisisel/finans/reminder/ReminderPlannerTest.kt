package com.kisisel.finans.reminder

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.ReminderCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
}
