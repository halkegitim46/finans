package com.kisisel.finans.domain

import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.PersonBalance
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.model.TypeTotal

/**
 * Hesap hesaplayıcısı (saf fonksiyon). Tahsilatlar yalnızca alacağı, borç ödemeleri yalnızca
 * borcu azaltır. Aynı ödeme hiçbir zaman hem gelir hem tahsilat olarak iki kez sayılmaz.
 */
object LedgerCalculator {

    /** Başlangıç bakiyesi: pozitif = alacak, negatif = borç. */
    fun balance(openingBalanceKurus: Long, totals: List<TypeTotal>): PersonBalance {
        val receivable = if (openingBalanceKurus > 0) openingBalanceKurus else 0L
        val payable = if (openingBalanceKurus < 0) Math.negateExact(openingBalanceKurus) else 0L
        return balance(receivable, payable, totals)
    }

    /** Toplu hesap (ör. ana sayfa) için ayrı başlangıç alacağı/borcu toplamlarıyla. */
    fun balance(openingReceivable: Long, openingPayable: Long, totals: List<TypeTotal>): PersonBalance {
        fun sum(type: TransactionType): Long =
            totals.filter { it.type == type }.fold(0L) { acc, t -> Money.add(acc, t.total) }

        val totalReceivable = Money.add(
            Money.add(openingReceivable, sum(TransactionType.LEND)),
            sum(TransactionType.PAID_ON_BEHALF)
        )
        val totalPayable = Money.add(openingPayable, sum(TransactionType.BORROW))
        val collected = sum(TransactionType.COLLECTION)
        val paid = sum(TransactionType.DEBT_PAYMENT)

        return PersonBalance(
            totalReceivable = totalReceivable,
            totalPayable = totalPayable,
            collected = collected,
            paid = paid,
            remainingReceivable = maxOf(0L, Money.subtract(totalReceivable, collected)),
            remainingPayable = maxOf(0L, Money.subtract(totalPayable, paid))
        )
    }
}
