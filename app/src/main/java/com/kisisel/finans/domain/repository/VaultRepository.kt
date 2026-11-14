package com.kisisel.finans.domain.repository

import com.kisisel.finans.domain.model.NewVaultItem
import com.kisisel.finans.domain.model.VaultItem
import com.kisisel.finans.domain.model.VaultSummary
import com.kisisel.finans.domain.model.VaultUpdate
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    suspend fun create(cmd: NewVaultItem): Long
    suspend fun update(cmd: VaultUpdate)

    /** Silme yok: arşivlenir, listelerden kalkar (kayıt yedekte korunur). */
    suspend fun archive(id: Long)

    /** Tam kayıt (şifre dahil). Arşivlenmişse null. */
    suspend fun getById(id: Long): VaultItem?

    /** Tek kaydın akışı (şifre dahil); arşivlenince veya yoksa null yayar. */
    fun observeById(id: Long): Flow<VaultItem?>

    /** Başlığa göre sıralı özetler; şifre içermez. */
    fun observeSummaries(): Flow<List<VaultSummary>>
}
