package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kisisel.finans.data.local.entity.AgendaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {
    @Insert
    suspend fun insert(item: AgendaItemEntity): Long

    @Update
    suspend fun update(item: AgendaItemEntity)

    @Query("SELECT * FROM agenda_items WHERE id = :id")
    suspend fun getById(id: Long): AgendaItemEntity?

    @Query("SELECT * FROM agenda_items WHERE id = :id")
    fun observeById(id: Long): Flow<AgendaItemEntity?>

    @Query("SELECT * FROM agenda_items WHERE isArchived = 0 ORDER BY date, COALESCE(timeMinutes, -1), id")
    fun observeAll(): Flow<List<AgendaItemEntity>>

    /** Tamamlandı işareti; arşivlenmiş kayıtta 0 döner. */
    @Query("UPDATE agenda_items SET isDone = :done, doneAt = :doneAt, updatedAt = :now WHERE id = :id AND isArchived = 0")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long): Int

    @Query("UPDATE agenda_items SET isArchived = 1, updatedAt = :now WHERE id = :id AND isArchived = 0")
    suspend fun archive(id: Long, now: Long): Int

    @Query("SELECT * FROM agenda_items WHERE isArchived = 0 AND isDone = 0 AND remindMinutesBefore IS NOT NULL")
    suspend fun getReminderCandidates(): List<AgendaItemEntity>
}
