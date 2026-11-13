package com.kisisel.finans.domain.usecase

import com.kisisel.finans.domain.Validators
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.NewAgendaItem
import com.kisisel.finans.domain.repository.AgendaRepository
import javax.inject.Inject

class CreateAgendaItemUseCase @Inject constructor(private val agenda: AgendaRepository) {
    suspend operator fun invoke(cmd: NewAgendaItem): Long = agenda.create(
        cmd.copy(
            title = Validators.agendaTitle(cmd.title),
            note = Validators.note(cmd.note),
            date = Validators.date(cmd.date),
            remindMinutesBefore = Validators.agendaRemind(cmd.remindMinutesBefore)
        )
    )
}

class UpdateAgendaItemUseCase @Inject constructor(private val agenda: AgendaRepository) {
    suspend operator fun invoke(cmd: AgendaUpdate) = agenda.update(
        cmd.copy(
            title = Validators.agendaTitle(cmd.title),
            note = Validators.note(cmd.note),
            date = Validators.date(cmd.date),
            remindMinutesBefore = Validators.agendaRemind(cmd.remindMinutesBefore)
        )
    )
}

class SetAgendaDoneUseCase @Inject constructor(private val agenda: AgendaRepository) {
    suspend operator fun invoke(id: Long, done: Boolean) = agenda.setDone(id, done)
}

class ArchiveAgendaItemUseCase @Inject constructor(private val agenda: AgendaRepository) {
    suspend operator fun invoke(id: Long) = agenda.archive(id)
}
