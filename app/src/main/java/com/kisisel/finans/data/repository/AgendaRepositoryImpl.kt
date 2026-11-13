package com.kisisel.finans.data.repository

import com.kisisel.finans.core.toDb
import com.kisisel.finans.core.toLocalDate
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.entity.AgendaItemEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.NewAgendaItem
import com.kisisel.finans.domain.repository.AgendaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaRepositoryImpl @Inject constructor(
    db: KisiselFinansDatabase,
    private val clock: Clock
) : AgendaRepository {

    private val agenda = db.agendaDao()
    private val persons = db.personDao()

    override suspend fun create(cmd: NewAgendaItem): Long {
        cmd.personId?.let { requireActivePerson(it) }
        val now = clock.millis()
        return agenda.insert(
            AgendaItemEntity(
                title = cmd.title,
                note = cmd.note,
                date = cmd.date.toDb(),
                timeMinutes = cmd.time?.let { it.hour * 60 + it.minute },
                personId = cmd.personId,
                remindMinutesBefore = cmd.remindMinutesBefore,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun update(cmd: AgendaUpdate) {
        val existing = agenda.getById(cmd.id)?.takeIf { !it.isArchived } ?: throw FinanceException.AgendaNotFound()
        if (cmd.personId != null && cmd.personId != existing.personId) requireActivePerson(cmd.personId)
        agenda.update(
            existing.copy(
                title = cmd.title,
                note = cmd.note,
                date = cmd.date.toDb(),
                timeMinutes = cmd.time?.let { it.hour * 60 + it.minute },
                personId = cmd.personId,
                remindMinutesBefore = cmd.remindMinutesBefore,
                updatedAt = clock.millis()
            )
        )
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        val now = clock.millis()
        if (agenda.setDone(id, done, if (done) now else null, now) == 0) throw FinanceException.AgendaNotFound()
    }

    override suspend fun archive(id: Long) {
        if (agenda.archive(id, clock.millis()) == 0) throw FinanceException.AgendaNotFound()
    }

    override suspend fun getById(id: Long): AgendaItem? {
        val e = agenda.getById(id)?.takeIf { !it.isArchived } ?: return null
        val name = e.personId?.let { persons.getById(it)?.fullName }
        return e.toDomain(name)
    }

    override fun observeAll(): Flow<List<AgendaItem>> =
        combine(agenda.observeAll(), persons.observeAll()) { items, people ->
            val names = people.associate { it.id to it.fullName }
            items.map { it.toDomain(it.personId?.let { id -> names[id] }) }
        }

    override suspend fun getReminderCandidates(): List<AgendaItem> {
        val names = persons.observeAll().first().associate { it.id to it.fullName }
        return agenda.getReminderCandidates().map { it.toDomain(it.personId?.let { id -> names[id] }) }
    }

    private fun AgendaItemEntity.toDomain(personName: String?) = AgendaItem(
        id = id,
        title = title,
        note = note,
        date = date.toLocalDate(),
        time = timeMinutes?.let { LocalTime.of(it / 60, it % 60) },
        personId = personId,
        personName = personName,
        remindMinutesBefore = remindMinutesBefore,
        isDone = isDone,
        doneAt = doneAt
    )

    private suspend fun requireActivePerson(id: Long): PersonEntity {
        val p = persons.getById(id) ?: throw FinanceException.PersonNotFound()
        if (p.isArchived) throw FinanceException.PersonArchived()
        return p
    }
}
