package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "persons", indices = [Index(value = ["isArchived"])])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val phone: String? = null,
    val note: String = "",
    /** Pozitif = kişi bana borçlu (alacak), negatif = ben kişiye borçluyum. */
    val openingBalanceKurus: Long = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
