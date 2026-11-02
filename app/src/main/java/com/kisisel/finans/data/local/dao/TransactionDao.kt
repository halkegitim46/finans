package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.domain.model.PersonTypeTotal
import com.kisisel.finans.domain.model.TypeTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Aynı idempotencyKey ile ikinci kayıt eklenmez; -1 döner. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY transactionDate DESC, id DESC")
    fun observeByPerson(personId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE transactionDate BETWEEN :fromDay AND :toDay " +
            "ORDER BY transactionDate DESC, id DESC"
    )
    suspend fun getBetween(fromDay: Long, toDay: Long): List<TransactionEntity>

    /** Silme yok: iptal. Zaten iptalse 0 döner. */
    @Query("UPDATE transactions SET isCancelled = 1, cancelledAt = :now, updatedAt = :now WHERE id = :id AND isCancelled = 0")
    suspend fun cancel(id: Long, now: Long): Int

    @Query(
        "SELECT type AS type, COALESCE(SUM(amountKurus), 0) AS total FROM transactions " +
            "WHERE personId = :personId AND isCancelled = 0 GROUP BY type"
    )
    fun observeTotalsByType(personId: Long): Flow<List<TypeTotal>>

    @Query(
        "SELECT type AS type, COALESCE(SUM(amountKurus), 0) AS total FROM transactions " +
            "WHERE personId = :personId AND isCancelled = 0 GROUP BY type"
    )
    suspend fun getTotalsByType(personId: Long): List<TypeTotal>

    /** Tüm kişiler (arşivlenenler dahil) için genel toplamlar. */
    @Query(
        "SELECT type AS type, COALESCE(SUM(amountKurus), 0) AS total FROM transactions " +
            "WHERE isCancelled = 0 GROUP BY type"
    )
    fun observeTotalsByType(): Flow<List<TypeTotal>>

    /** Tarih aralığı toplamları (ör. bu ay yapılan ödemeler / alınan tahsilatlar). */
    @Query(
        "SELECT type AS type, COALESCE(SUM(amountKurus), 0) AS total FROM transactions " +
            "WHERE isCancelled = 0 AND transactionDate BETWEEN :fromDay AND :toDay GROUP BY type"
    )
    fun observeTotalsByTypeBetween(fromDay: Long, toDay: Long): Flow<List<TypeTotal>>

    @Query("SELECT COALESCE(SUM(openingBalanceKurus), 0) FROM persons WHERE openingBalanceKurus > 0")
    fun observeOpeningReceivables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(-openingBalanceKurus), 0) FROM persons WHERE openingBalanceKurus < 0")
    fun observeOpeningPayables(): Flow<Long>

    @Query("SELECT * FROM transactions WHERE idempotencyKey = :key")
    suspend fun getByIdempotencyKey(key: String): TransactionEntity?

    /** Yalnızca açıklama/tarih/vade güncellenir; tutar ve tür değişmez. İptal edilmiş kayıt güncellenmez. */
    @Query(
        "UPDATE transactions SET description = :description, transactionDate = :date, dueDate = :dueDate, " +
            "updatedAt = :now WHERE id = :id AND isCancelled = 0"
    )
    suspend fun updateDetails(id: Long, description: String, date: Long, dueDate: Long?, now: Long): Int

    /** Alacak oluşturan işlemler ve aktif tahsilatlarla kapatılan tutarları (eskiden yeniye). */
    @Query(
        "SELECT t.*, COALESCE((SELECT SUM(p.amountKurus) FROM payments p " +
            "INNER JOIN transactions s ON s.id = p.settlementTxId " +
            "WHERE p.claimTxId = t.id AND s.isCancelled = 0), 0) AS allocatedKurus " +
            "FROM transactions t WHERE t.personId = :personId AND t.isCancelled = 0 " +
            "AND t.type IN ('LEND', 'PAID_ON_BEHALF') ORDER BY t.transactionDate, t.id"
    )
    suspend fun getReceivableClaims(personId: Long): List<ClaimWithAllocated>

    @Query(
        "SELECT t.*, COALESCE((SELECT SUM(p.amountKurus) FROM payments p " +
            "INNER JOIN transactions s ON s.id = p.settlementTxId " +
            "WHERE p.claimTxId = t.id AND s.isCancelled = 0), 0) AS allocatedKurus " +
            "FROM transactions t WHERE t.personId = :personId AND t.isCancelled = 0 " +
            "AND t.type = 'BORROW' ORDER BY t.transactionDate, t.id"
    )
    suspend fun getPayableClaims(personId: Long): List<ClaimWithAllocated>

    @Query(
        "SELECT personId AS personId, type AS type, COALESCE(SUM(amountKurus), 0) AS total " +
            "FROM transactions WHERE isCancelled = 0 GROUP BY personId, type"
    )
    fun observeTotalsByPerson(): Flow<List<PersonTypeTotal>>

    /** Faturadan doğan aktif (iptal edilmemiş) ödeme kaydı. */
    @Query("SELECT * FROM transactions WHERE occurrenceId = :occurrenceId AND isCancelled = 0 LIMIT 1")
    suspend fun getActiveByOccurrence(occurrenceId: Long): TransactionEntity?

    /** İptaller dahil; her ödeme için benzersiz idempotency anahtarı üretmekte kullanılır. */
    @Query("SELECT COUNT(*) FROM transactions WHERE occurrenceId = :occurrenceId")
    suspend fun countByOccurrence(occurrenceId: Long): Int
}

/** Bir alacak/borç işlemi ve aktif (iptal edilmemiş) tahsilat/ödemelerle kapatılan toplamı. */
data class ClaimWithAllocated(
    @Embedded val tx: TransactionEntity,
    val allocatedKurus: Long
) {
    val openKurus: Long get() = tx.amountKurus - allocatedKurus
}
