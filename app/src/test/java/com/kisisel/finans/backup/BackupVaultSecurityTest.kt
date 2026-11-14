package com.kisisel.finans.backup

import com.kisisel.finans.data.local.DatabaseSnapshot
import com.kisisel.finans.data.local.entity.VaultItemEntity
import com.kisisel.finans.domain.FinanceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kasa şifreleri yedek dosyasında yalnızca şifreli (AES-GCM) bulunur; doğru parola olmadan okunamaz.
 * Robolectric gerekir: BackupCodec org.json kullanır (düz JVM testinde Android'in boş taslağı gelir).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupVaultSecurityTest {
    private val secret = "ÇokGizli-Değer-12345"
    private val snapshot = DatabaseSnapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        vaultItems = listOf(VaultItemEntity(1, "Banka", "ali", secret, "", "", false, 1, 2))
    )

    @Test fun encryptedBackupDoesNotContainSecretInClearAndRoundTrips() {
        val plain = BackupCodec.encode(snapshot, 1)
        val file = BackupCrypto.encrypt(plain, "yedek-parolasi".toCharArray(), 1_000)

        val asText = String(file, Charsets.ISO_8859_1)
        assertFalse(asText.contains("Banka"))
        assertFalse(asText.contains("Gizli"))
        assertFalse(String(file, Charsets.UTF_8).contains(secret))

        val back = BackupCodec.decode(BackupCrypto.decrypt(file, "yedek-parolasi".toCharArray()))
        assertEquals(secret, back.snapshot.vaultItems.single().secret)
    }

    @Test fun wrongPasswordCannotReadVault() {
        val file = BackupCrypto.encrypt(BackupCodec.encode(snapshot, 1), "dogru-parola".toCharArray(), 1_000)
        assertThrows(FinanceException.BackupInvalid::class.java) { BackupCrypto.decrypt(file, "yanlis-parola".toCharArray()) }
    }
}
