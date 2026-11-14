package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Şifre kasası kaydı. Veritabanı SQLCipher ile (AES-256) şifrelidir; kayıtlar ayrıca uygulama kilidi ve
 * kasa açılırken yeniden doğrulama arkasındadır. Silinmez, arşivlenir (arşivlenen kayıt yedekte kalır).
 */
@Entity(tableName = "vault_items", indices = [Index(value = ["isArchived"])])
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val username: String = "",
    val secret: String,
    val url: String = "",
    val note: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/** Liste görünümü için: şifre alanı YOK (gizli veri gereksiz yere belleğe/arayüze taşınmaz). */
data class VaultSummaryRow(val id: Long, val title: String, val username: String, val url: String)
