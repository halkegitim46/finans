package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kisisel.finans.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    /** (settlement, claim) çifti benzersiz; tekrar eklenirse -1 döner. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: PaymentEntity): Long

    @Query("SELECT COALESCE(SUM(amountKurus), 0) FROM payments WHERE claimTxId = :claimTxId")
    suspend fun sumAllocatedForClaim(claimTxId: Long): Long

    @Query("SELECT COALESCE(SUM(amountKurus), 0) FROM payments WHERE settlementTxId = :settlementTxId")
    suspend fun sumAllocatedForSettlement(settlementTxId: Long): Long

    @Query("SELECT * FROM payments WHERE claimTxId = :claimTxId ORDER BY id")
    fun observeForClaim(claimTxId: Long): Flow<List<PaymentEntity>>
}
