package com.kisisel.finans.domain.usecase

import com.kisisel.finans.domain.Validators
import com.kisisel.finans.domain.model.NewVaultItem
import com.kisisel.finans.domain.model.VaultUpdate
import com.kisisel.finans.domain.repository.VaultRepository
import javax.inject.Inject

class CreateVaultItemUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(cmd: NewVaultItem): Long = vault.create(
        NewVaultItem(
            title = Validators.vaultTitle(cmd.title),
            username = Validators.vaultUsername(cmd.username),
            secret = Validators.vaultSecret(cmd.secret),
            url = Validators.vaultUrl(cmd.url),
            note = Validators.vaultNote(cmd.note)
        )
    )
}

class UpdateVaultItemUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(cmd: VaultUpdate) = vault.update(
        VaultUpdate(
            id = cmd.id,
            title = Validators.vaultTitle(cmd.title),
            username = Validators.vaultUsername(cmd.username),
            secret = Validators.vaultSecret(cmd.secret),
            url = Validators.vaultUrl(cmd.url),
            note = Validators.vaultNote(cmd.note)
        )
    )
}

class ArchiveVaultItemUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(id: Long) = vault.archive(id)
}
