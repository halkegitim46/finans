package com.kisisel.finans.domain.model

enum class TransactionType {
    /** Borç verme: kişi bana borçlanır (alacak). */
    LEND,
    /** Borç alma: ben kişiye borçlanırım. */
    BORROW,
    /** Kişi adına ödeme yapma: kişi bana borçlanır (alacak). */
    PAID_ON_BEHALF,
    /** Tahsilat: kişiden alınan ödeme (alacağı azaltır). */
    COLLECTION,
    /** Borç ödeme: kişiye yaptığım ödeme (borcumu azaltır). */
    DEBT_PAYMENT;

    val createsReceivable: Boolean get() = this == LEND || this == PAID_ON_BEHALF
    val createsPayable: Boolean get() = this == BORROW
    val isSettlement: Boolean get() = this == COLLECTION || this == DEBT_PAYMENT
}

enum class InvoiceType { ELECTRICITY, WATER, GAS, INTERNET, PHONE, RENT, DUES, LOAN, OTHER }

enum class Recurrence { ONCE, MONTHLY, YEARLY, CUSTOM_DAYS }

enum class OccurrenceStatus { UNPAID, PAID, CANCELLED }
