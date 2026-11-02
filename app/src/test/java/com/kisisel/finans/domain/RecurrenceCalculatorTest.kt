package com.kisisel.finans.domain

import com.kisisel.finans.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthlyDoesNotDriftAfterShortMonth() {
        val first = d("2026-01-31")
        assertEquals(d("2026-02-28"), RecurrenceCalculator.dueDateFor(first, Recurrence.MONTHLY, null, 1))
        assertEquals(d("2026-03-31"), RecurrenceCalculator.dueDateFor(first, Recurrence.MONTHLY, null, 2))
    }

    @Test fun yearlyLeapDay() {
        assertEquals(d("2025-02-28"), RecurrenceCalculator.dueDateFor(d("2024-02-29"), Recurrence.YEARLY, null, 1))
    }

    @Test fun customDays() {
        assertEquals(d("2026-01-31"), RecurrenceCalculator.dueDateFor(d("2026-01-01"), Recurrence.CUSTOM_DAYS, 10, 3))
    }

    @Test fun customDaysRequiresPositiveInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceCalculator.occurrencesUntil(d("2026-01-01"), Recurrence.CUSTOM_DAYS, null, d("2026-12-31"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceCalculator.occurrencesUntil(d("2026-01-01"), Recurrence.CUSTOM_DAYS, 0, d("2026-12-31"))
        }
    }

    @Test fun occurrencesUntilIsInclusive() {
        val list = RecurrenceCalculator.occurrencesUntil(d("2026-01-15"), Recurrence.MONTHLY, null, d("2026-03-15"))
        assertEquals(listOf(d("2026-01-15"), d("2026-02-15"), d("2026-03-15")), list.map { it.second })
        assertEquals(listOf(0, 1, 2), list.map { it.first })
    }

    @Test fun occurrencesUntilExcludesFuture() {
        val list = RecurrenceCalculator.occurrencesUntil(d("2026-01-15"), Recurrence.MONTHLY, null, d("2026-03-14"))
        assertEquals(2, list.size)
    }

    @Test fun onceProducesSingleOccurrence() {
        val list = RecurrenceCalculator.occurrencesUntil(d("2026-01-15"), Recurrence.ONCE, null, d("2030-01-01"))
        assertEquals(1, list.size)
    }

    @Test fun firstDueInFutureProducesNothing() {
        val list = RecurrenceCalculator.occurrencesUntil(d("2027-01-15"), Recurrence.MONTHLY, null, d("2026-12-31"))
        assertEquals(0, list.size)
    }

    @Test fun periodKeys() {
        assertEquals("2026-03", RecurrenceCalculator.periodKey(Recurrence.MONTHLY, d("2026-03-31")))
        assertEquals("2026", RecurrenceCalculator.periodKey(Recurrence.YEARLY, d("2026-03-31")))
        assertEquals("2026-03-31", RecurrenceCalculator.periodKey(Recurrence.CUSTOM_DAYS, d("2026-03-31")))
    }
}
