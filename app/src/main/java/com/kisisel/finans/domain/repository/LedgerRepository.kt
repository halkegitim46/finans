package com.kisisel.finans.domain.repository

import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.SettlementResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Tüm yazma işlemleri tek veritabanı transaction'ında yapılır; hata olursa yarım kayıt kalmaz. */
interface LedgerRepository {
    /** Aynı idempotencyKey ile tekrar çağrılırsa yeni kayıt açmaz, mevcut kaydın id'sini döner. */
    suspend fun recordClaim(cmd: NewClaim): Long

    suspend fun settle(cmd: NewSettlement): SettlementResult

    /** Silme yok, iptal. */
    suspend fun cancel(transactionId: Long)

    /** Yalnızca açıklama, tarih ve vade; tutar/tür değişmez (düzeltme için iptal + yeniden gir). */
    suspend fun updateDetails(transactionId: Long, description: String, date: LocalDate, dueDate: LocalDate?)

    fun observePersonSummary(personId: Long): Flow<PersonSummary?>
    fun observePersonSummaries(): Flow<List<PersonSummary>>
    fun observeEntries(personId: Long): Flow<List<LedgerEntry>>
    fun observeRecentEntries(limit: Int): Flow<List<LedgerEntry>>
    fun observeDashboard(today: LocalDate): Flow<DashboardTotals>

    /** Tüm kişilerin işlemleri (iptaller dahil), yeniden eskiye. */
    fun observeAllLines(): Flow<List<LedgerLine>>

    /** Son girilen işlemler (kayıt zamanına göre). */
    fun observeRecentLines(limit: Int): Flow<List<LedgerLine>>

    /** İptal edilmemiş tüm alacak/borç doğuran işlemler ve kalan (açık) tutarları. */
    fun observeClaims(): Flow<List<ClaimLine>>
}
