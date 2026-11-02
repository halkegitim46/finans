package com.kisisel.finans.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class ValidatorsTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun fullName_trimsAndCollapsesSpaces() {
        assertEquals("Ali Veli", Validators.fullName("  Ali   Veli "))
    }

    @Test fun fullName_rejectsBlankAndTooLong() {
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.fullName("   ") }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.fullName("a".repeat(101)) }
    }

    @Test fun phone_optionalAndValidated() {
        assertNull(Validators.phone(null))
        assertNull(Validators.phone("  "))
        assertEquals("0532 123 45 67", Validators.phone("0532 123 45 67"))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.phone("abc") }
    }

    @Test fun amount_mustBePositiveAndBounded() {
        assertEquals(1L, Validators.amount(1))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.amount(0) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.amount(-5) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.amount(Long.MAX_VALUE) }
    }

    @Test fun openingBalance_allowsNegativeButBounded() {
        assertEquals(-100L, Validators.openingBalance(-100))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.openingBalance(Long.MIN_VALUE) }
    }

    @Test fun date_rangeChecked() {
        assertEquals(d("2026-11-02"), Validators.date(d("2026-11-02")))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.date(d("1999-12-31")) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.date(d("2101-01-01")) }
    }

    @Test fun dueDate_notBeforeTransactionDate() {
        assertNull(Validators.dueDate(d("2026-11-02"), null))
        assertEquals(d("2026-11-02"), Validators.dueDate(d("2026-11-02"), d("2026-11-02")))
        assertThrows(FinanceException.InvalidInput::class.java) {
            Validators.dueDate(d("2026-11-02"), d("2026-11-01"))
        }
    }

    @Test fun idempotencyKey_required() {
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.idempotencyKey("") }
        assertEquals("abc", Validators.idempotencyKey("abc"))
    }
}

class InvoiceValidatorsTest {
    @Test fun invoiceName() {
        assertEquals("Dayım elektrik", Validators.invoiceName("  Dayım   elektrik "))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.invoiceName("") }
    }

    @Test fun fixedInvoiceRequiresAmount_variableDoesNot() {
        assertEquals(150_000L, Validators.invoiceAmount(true, 150_000))
        assertNull(Validators.invoiceAmount(false, null))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.invoiceAmount(true, null) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.invoiceAmount(false, 0) }
    }

    @Test fun customIntervalBounds() {
        assertEquals(10, Validators.customInterval(10))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.customInterval(null) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.customInterval(0) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.customInterval(366) }
    }

    @Test fun reminderBounds() {
        assertEquals(setOf(7, 3, 1, 0), Validators.reminderDays(setOf(7, 3, 1, 0)))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.reminderDays(setOf(31)) }
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.reminderDays(setOf(-1)) }
        assertEquals(0, Validators.overdueInterval(0))
        assertThrows(FinanceException.InvalidInput::class.java) { Validators.overdueInterval(31) }
    }
}
