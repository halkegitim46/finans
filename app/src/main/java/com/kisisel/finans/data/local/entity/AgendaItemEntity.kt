package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ajanda notu / özel hatırlatıcı. Silinmez, arşivlenir (diğer finansal kayıtlarla aynı ilke).
 * date: epochDay; timeMinutes: gece yarısından dakika (null = gün boyu); remindMinutesBefore: null = hatırlatma yok.
 */
@Entity(
    tableName = "agenda_items",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["personId"]), Index(value = ["date"])]
)
data class AgendaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val date: Long,
    val timeMinutes: Int? = null,
    val personId: Long? = null,
    val remindMinutesBefore: Int? = null,
    val isDone: Boolean = false,
    val doneAt: Long? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
