package com.kisisel.finans.domain.repository

import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.NewAgendaItem
import kotlinx.coroutines.flow.Flow

interface AgendaRepository {
    suspend fun create(cmd: NewAgendaItem): Long
    suspend fun update(cmd: AgendaUpdate)

    /** Tamamlandı işareti (geri alınabilir). */
    suspend fun setDone(id: Long, done: Boolean)

    /** Silme yok: arşivlenir, listelerden kalkar. */
    suspend fun archive(id: Long)

    suspend fun getById(id: Long): AgendaItem?

    /** Arşivlenmemiş tüm kayıtlar (tamamlananlar dahil), tarihe göre sıralı. */
    fun observeAll(): Flow<List<AgendaItem>>

    /** Hatırlatması açık, tamamlanmamış ve arşivlenmemiş kayıtlar. */
    suspend fun getReminderCandidates(): List<AgendaItem>
}
