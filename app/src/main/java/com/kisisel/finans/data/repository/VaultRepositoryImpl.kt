package com.kisisel.finans.data.repository

import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.entity.VaultItemEntity
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.NewVaultItem
import com.kisisel.finans.domain.model.VaultItem
import com.kisisel.finans.domain.model.VaultSummary
import com.kisisel.finans.domain.model.VaultUpdate
import com.kisisel.finans.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    db: KisiselFinansDatabase,
    private val clock: Clock
) : VaultRepository {

    private val dao = db.vaultDao()

    override suspend fun create(cmd: NewVaultItem): Long {
        val now = clock.millis()
        return dao.insert(
            VaultItemEntity(
                title = cmd.title, username = cmd.username, secret = cmd.secret, url = cmd.url, note = cmd.note,
                createdAt = now, updatedAt = now
            )
        )
    }

    override suspend fun update(cmd: VaultUpdate) {
        val existing = dao.getById(cmd.id) ?: throw FinanceException.VaultItemNotFound()
        dao.update(
            existing.copy(
                title = cmd.title, username = cmd.username, secret = cmd.secret, url = cmd.url, note = cmd.note,
                updatedAt = clock.millis()
            )
        )
    }

    override suspend fun archive(id: Long) {
        if (dao.archive(id, clock.millis()) == 0) throw FinanceException.VaultItemNotFound()
    }

    override suspend fun getById(id: Long): VaultItem? = dao.getById(id)?.let {
        VaultItem(it.id, it.title, it.username, it.secret, it.url, it.note, it.updatedAt)
    }

    override fun observeById(id: Long): Flow<VaultItem?> = dao.observeById(id).map { e ->
        e?.let { VaultItem(it.id, it.title, it.username, it.secret, it.url, it.note, it.updatedAt) }
    }

    override fun observeSummaries(): Flow<List<VaultSummary>> =
        dao.observeSummaries().map { rows -> rows.map { VaultSummary(it.id, it.title, it.username, it.url) } }
}
