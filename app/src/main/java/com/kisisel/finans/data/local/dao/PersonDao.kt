package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kisisel.finans.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observeById(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE isArchived = 0 ORDER BY fullName COLLATE NOCASE")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE isArchived = 1 ORDER BY fullName COLLATE NOCASE")
    fun observeArchived(): Flow<List<PersonEntity>>

    @Query(
        "SELECT * FROM persons WHERE isArchived = 0 AND fullName LIKE '%' || :query || '%' " +
            "ORDER BY fullName COLLATE NOCASE"
    )
    fun search(query: String): Flow<List<PersonEntity>>

    /** Kişi fiziksel olarak silinmez; arşivlenir, finansal kayıtlar korunur. */
    @Query("UPDATE persons SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long): Int

    @Query("SELECT * FROM persons")
    fun observeAll(): Flow<List<PersonEntity>>
}
