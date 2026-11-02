package com.kisisel.finans.domain.repository

import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.PersonUpdate
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    suspend fun create(person: NewPerson): Long
    suspend fun update(update: PersonUpdate)
    /** Kişi silinmez, arşivlenir; finansal kayıtlar korunur. */
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun getById(id: Long): Person?
    fun observeActive(): Flow<List<Person>>
    fun observeArchived(): Flow<List<Person>>
    fun search(query: String): Flow<List<Person>>
}
