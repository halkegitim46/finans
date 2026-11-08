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

    @Test fun occurrencesBetweenSkipsStraightToWindowForOldFirstDue() {
        val list = RecurrenceCalculator.occurrencesBetween(
            d("2021-01-01"), Recurrence.CUSTOM_DAYS, 1, d("2026-11-04"), d("2026-11-10")
        )
        assertEquals(7, list.size)
        assertEquals(d("2026-11-04"), list.first().second)
        assertEquals(d("2026-11-10"), list.last().second)
        // indeks, ilk vadeden gün sayısıyla uyumlu
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(d("2021-01-01"), d("2026-11-04")).toInt(), list.first().first)
    }

    @Test fun occurrencesBetweenMonthlyKeepsEndOfMonthAnchor() {
        val list = RecurrenceCalculator.occurrencesBetween(
            d("2020-01-31"), Recurrence.MONTHLY, null, d("2026-02-01"), d("2026-04-30")
        )
        assertEquals(listOf(d("2026-02-28"), d("2026-03-31"), d("2026-04-30")), list.map { it.second })
    }

    @Test fun occurrencesBetweenYearly() {
        val list = RecurrenceCalculator.occurrencesBetween(
            d("2020-02-29"), Recurrence.YEARLY, null, d("2024-01-01"), d("2026-12-31")
        )
        assertEquals(listOf(d("2024-02-29"), d("2025-02-28"), d("2026-02-28")), list.map { it.second })
    }

    @Test fun occurrencesBetweenWindowBoundariesAreInclusive() {
        val list = RecurrenceCalculator.occurrencesBetween(
            d("2026-01-15"), Recurrence.MONTHLY, null, d("2026-03-15"), d("2026-03-15")
        )
        assertEquals(listOf(d("2026-03-15")), list.map { it.second })
        assertEquals(0, RecurrenceCalculator.occurrencesBetween(
            d("2026-01-15"), Recurrence.MONTHLY, null, d("2026-03-16"), d("2026-04-14")
        ).size)
    }

    @Test fun occurrencesBetweenKeepsNewestWhenCapped() {
        val list = RecurrenceCalculator.occurrencesBetween(
            d("2026-01-01"), Recurrence.CUSTOM_DAYS, 1, d("2026-01-01"), d("2026-01-31"), maxResults = 5
        )
        assertEquals(5, list.size)
        assertEquals(d("2026-01-31"), list.last().second)
        assertEquals(d("2026-01-27"), list.first().second)
    }

    @Test fun occurrencesBetweenEmptyAndOnce() {
        assertEquals(0, RecurrenceCalculator.occurrencesBetween(
            d("2026-01-01"), Recurrence.MONTHLY, null, d("2026-02-01"), d("2026-01-01")
        ).size)
        assertEquals(1, RecurrenceCalculator.occurrencesBetween(
            d("2026-05-05"), Recurrence.ONCE, null, d("2026-01-01"), d("2026-12-31")
        ).size)
        assertEquals(0, RecurrenceCalculator.occurrencesBetween(
            d("2026-05-05"), Recurrence.ONCE, null, d("2026-06-01"), d("2026-12-31")
        ).size)
    }

    @Test fun occurrencesBetweenRequiresValidCustomDays() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceCalculator.occurrencesBetween(d("2026-01-01"), Recurrence.CUSTOM_DAYS, 0, d("2026-01-01"), d("2026-02-01"))
        }
    }
}
