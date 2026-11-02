package com.kisisel.finans.domain.usecase

import com.kisisel.finans.domain.Validators
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.PersonUpdate
import com.kisisel.finans.domain.repository.PersonRepository
import javax.inject.Inject

class CreatePersonUseCase @Inject constructor(private val persons: PersonRepository) {
    suspend operator fun invoke(
        fullName: String,
        phone: String?,
        note: String,
        openingBalanceKurus: Long
    ): Long = persons.create(
        NewPerson(
            fullName = Validators.fullName(fullName),
            phone = Validators.phone(phone),
            note = Validators.note(note),
            openingBalanceKurus = Validators.openingBalance(openingBalanceKurus)
        )
    )
}

class UpdatePersonUseCase @Inject constructor(private val persons: PersonRepository) {
    suspend operator fun invoke(id: Long, fullName: String, phone: String?, note: String) =
        persons.update(
            PersonUpdate(
                id = id,
                fullName = Validators.fullName(fullName),
                phone = Validators.phone(phone),
                note = Validators.note(note)
            )
        )
}

class ArchivePersonUseCase @Inject constructor(private val persons: PersonRepository) {
    suspend operator fun invoke(id: Long, archived: Boolean = true) = persons.setArchived(id, archived)
}
