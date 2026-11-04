package com.kisisel.finans.domain.model

import java.time.LocalDate

data class Person(
    val id: Long,
    val fullName: String,
    val phone: String?,
    val note: String,
    /** Pozitif = kişi bana borçlu, negatif = ben kişiye borçluyum. */
    val openingBalanceKurus: Long,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class LedgerEntry(
    val id: Long,
    val personId: Long,
    val type: TransactionType,
    val amountKurus: Long,
    val date: LocalDate,
    val dueDate: LocalDate?,
    val description: String,
    val occurrenceId: Long?,
    val isCancelled: Boolean,
    val createdAt: Long
)

data class PersonSummary(val person: Person, val balance: PersonBalance)

/**
 * Ana sayfa toplamları. monthPaid = bu ay yapılan ödemeler (kişi adına ödeme + borç ödeme),
 * monthCollected = bu ay alınan tahsilatlar. Hiçbir tutar iki kez sayılmaz.
 */
data class DashboardTotals(
    val remainingReceivable: Long,
    val remainingPayable: Long,
    val net: Long,
    val monthPaid: Long,
    val monthCollected: Long
)

data class NewPerson(
    val fullName: String,
    val phone: String?,
    val note: String,
    val openingBalanceKurus: Long
)

data class PersonUpdate(
    val id: Long,
    val fullName: String,
    val phone: String?,
    val note: String
)

data class NewClaim(
    val personId: Long,
    val type: TransactionType,
    val amountKurus: Long,
    val date: LocalDate,
    val dueDate: LocalDate?,
    val description: String,
    /** Form açılırken üretilen benzersiz anahtar; çift dokunuşta çift kayıt oluşmasını önler. */
    val idempotencyKey: String
)

/** amountKurus == null ise kalan tutarın tamamı kapatılır (borç/alacak kapatma). */
data class NewSettlement(
    val personId: Long,
    val type: TransactionType,
    val amountKurus: Long?,
    val date: LocalDate,
    val description: String,
    val idempotencyKey: String
)

data class SettlementResult(
    val transactionId: Long,
    val amountKurus: Long,
    /** Açık alacak/borç kayıtlarına dağıtılan kısım (kalanı başlangıç bakiyesinden düşer). */
    val allocatedKurus: Long
)

/** Defter satırı + kişi adı (İşlemler ve Ana Sayfa listeleri için). */
data class LedgerLine(val entry: LedgerEntry, val personName: String)

/** Alacak/borç doğuran bir işlem ve kendisine dağıtılan tahsilat/ödeme toplamı (vade raporları için). */
data class ClaimLine(
    val entryId: Long,
    val personId: Long,
    val personName: String,
    val type: TransactionType,
    val amountKurus: Long,
    val allocatedKurus: Long,
    val date: LocalDate,
    val dueDate: LocalDate?,
    /** Doluysa işlem bir fatura ödemesinden doğmuştur (vadesi fatura vadesidir, kişinin geri ödeme vadesi değildir). */
    val occurrenceId: Long?,
    val description: String
) {
    val openKurus: Long get() = amountKurus - allocatedKurus
}
