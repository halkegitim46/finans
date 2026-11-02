package com.kisisel.finans.domain.repository

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceTemplate
import com.kisisel.finans.domain.model.InvoiceUpdate
import com.kisisel.finans.domain.model.NewInvoice
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Dönem üretiminde bugünden kaç gün ilerisine bakılır. */
const val INVOICE_HORIZON_DAYS: Long = 45

interface InvoiceRepository {
    /** Şablonu, ilk dönemi ve ufuk içindeki dönemleri tek transaction'da oluşturur. */
    suspend fun createTemplate(cmd: NewInvoice, today: LocalDate): Long

    /** Yalnızca sonraki dönemleri etkiler; mevcut dönem kayıtları değişmez. */
    suspend fun updateTemplate(update: InvoiceUpdate)

    /** Silinmez; arşivlenir, yeni dönem üretilmez, geçmiş korunur. */
    suspend fun archiveTemplate(id: Long)

    suspend fun getTemplate(id: Long): InvoiceTemplate?

    /** Aktif şablonlar için `until` tarihine kadar eksik dönemleri üretir (idempotent). Yeni kayıt sayısını döner. */
    suspend fun generateOccurrences(until: LocalDate): Int

    suspend fun setOccurrenceAmount(occurrenceId: Long, amountKurus: Long)

    /**
     * Dönemi öder: dönem kapanır; faturada kişi varsa o kişi adına ödeme (alacak) kaydı oluşur.
     * Aynı dönem iki kez ödenemez. Dönen değer oluşan işlem id'si (kişisiz faturada null).
     */
    suspend fun payOccurrence(occurrenceId: Long, paidDate: LocalDate): Long?

    /** Ödemeyi geri alır: dönem tekrar açılır, bağlı alacak kaydı iptal edilir (üzerine tahsilat varsa engellenir). */
    suspend fun undoPayment(occurrenceId: Long)

    suspend fun cancelOccurrence(occurrenceId: Long)

    fun observeTemplates(): Flow<List<InvoiceTemplate>>
    fun observeOccurrences(): Flow<List<InvoiceOccurrence>>
    fun observeOccurrence(id: Long): Flow<InvoiceOccurrence?>
    fun observeHistory(templateId: Long): Flow<List<InvoiceOccurrence>>
}
