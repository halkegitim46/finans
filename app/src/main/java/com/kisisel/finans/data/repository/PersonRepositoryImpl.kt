package com.kisisel.finans.data.repository

import com.kisisel.finans.data.local.dao.PersonDao
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.mapper.toDomain
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.PersonUpdate
import com.kisisel.finans.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val dao: PersonDao,
    private val clock: Clock
) : PersonRepository {

    override suspend fun create(person: NewPerson): Long {
        val now = clock.millis()
        return dao.insert(
            PersonEntity(
                fullName = person.fullName,
                phone = person.phone,
                note = person.note,
                openingBalanceKurus = person.openingBalanceKurus,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun update(update: PersonUpdate) {
        val existing = dao.getById(update.id) ?: throw FinanceException.PersonNotFound()
        dao.update(
            existing.copy(
                fullName = update.fullName,
                phone = update.phone,
                note = update.note,
                updatedAt = clock.millis()
            )
        )
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        if (dao.setArchived(id, archived, clock.millis()) == 0) throw FinanceException.PersonNotFound()
    }

    override suspend fun getById(id: Long): Person? = dao.getById(id)?.toDomain()

    override fun observeActive(): Flow<List<Person>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<Person>> = dao.observeArchived().map { l -> l.map { it.toDomain() } }

    override fun search(query: String): Flow<List<Person>> =
        dao.search(query.trim()).map { l -> l.map { it.toDomain() } }
}
