package com.kisisel.finans.domain

import com.kisisel.finans.domain.model.TransactionType.BORROW
import com.kisisel.finans.domain.model.TransactionType.COLLECTION
import com.kisisel.finans.domain.model.TransactionType.DEBT_PAYMENT
import com.kisisel.finans.domain.model.TransactionType.LEND
import com.kisisel.finans.domain.model.TransactionType.PAID_ON_BEHALF
import com.kisisel.finans.domain.model.TypeTotal
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerCalculatorTest {

    @Test fun dayiElectricityExample() {
        // Fatura 1.500 TL ödendi, 500 TL tahsil edildi -> kalan alacak 1.000 TL
        val b = LedgerCalculator.balance(
            0,
            listOf(TypeTotal(PAID_ON_BEHALF, 150_000), TypeTotal(COLLECTION, 50_000))
        )
        assertEquals(150_000, b.totalReceivable)
        assertEquals(50_000, b.collected)
        assertEquals(100_000, b.remainingReceivable)
        assertEquals(0, b.remainingPayable)
        assertEquals(100_000, b.net)
    }

    @Test fun personCanBeBothDebtorAndCreditor() {
        val b = LedgerCalculator.balance(
            0,
            listOf(
                TypeTotal(LEND, 100_000), TypeTotal(COLLECTION, 20_000),
                TypeTotal(BORROW, 50_000), TypeTotal(DEBT_PAYMENT, 10_000)
            )
        )
        assertEquals(80_000, b.remainingReceivable)
        assertEquals(40_000, b.remainingPayable)
        assertEquals(40_000, b.net)
    }

    @Test fun openingBalancePositiveIsReceivable() {
        val b = LedgerCalculator.balance(25_000, emptyList())
        assertEquals(25_000, b.remainingReceivable)
        assertEquals(0, b.remainingPayable)
    }

    @Test fun openingBalanceNegativeIsPayable() {
        val b = LedgerCalculator.balance(-25_000, listOf(TypeTotal(DEBT_PAYMENT, 5_000)))
        assertEquals(25_000, b.totalPayable)
        assertEquals(20_000, b.remainingPayable)
        assertEquals(-20_000, b.net)
    }

    @Test fun overCollectionClampsToZero() {
        val b = LedgerCalculator.balance(0, listOf(TypeTotal(LEND, 10_000), TypeTotal(COLLECTION, 12_000)))
        assertEquals(0, b.remainingReceivable)
    }

    @Test fun emptyIsZero() {
        val b = LedgerCalculator.balance(0, emptyList())
        assertEquals(0, b.net)
    }
}
