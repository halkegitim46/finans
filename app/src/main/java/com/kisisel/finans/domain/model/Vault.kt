package com.kisisel.finans.domain.model

/** Liste öğesi: şifre içermez. */
data class VaultSummary(val id: Long, val title: String, val username: String, val url: String)

/** Tam kayıt (şifre dahil): yalnızca kasa açıkken ayrıntı/düzenleme ekranlarında yüklenir. */
data class VaultItem(
    val id: Long,
    val title: String,
    val username: String,
    val secret: String,
    val url: String,
    val note: String,
    val updatedAt: Long
) {
    // Şifre yanlışlıkla log'a/ekrana düşmesin.
    override fun toString(): String = "VaultItem(id=$id, title=$title, secret=***)"
}

data class NewVaultItem(val title: String, val username: String, val secret: String, val url: String, val note: String) {
    override fun toString(): String = "NewVaultItem(title=$title, secret=***)"
}

data class VaultUpdate(
    val id: Long, val title: String, val username: String, val secret: String, val url: String, val note: String
) {
    override fun toString(): String = "VaultUpdate(id=$id, title=$title, secret=***)"
}
