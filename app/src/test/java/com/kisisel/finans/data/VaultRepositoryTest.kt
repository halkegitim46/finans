package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.backup.BackupCodec
import com.kisisel.finans.backup.BackupSnapshotValidator
import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.VaultRepositoryImpl
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.NewVaultItem
import com.kisisel.finans.domain.model.VaultUpdate
import com.kisisel.finans.domain.usecase.ArchiveVaultItemUseCase
import com.kisisel.finans.domain.usecase.CreateVaultItemUseCase
import com.kisisel.finans.domain.usecase.UpdateVaultItemUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VaultRepositoryTest {
    private lateinit var db: KisiselFinansDatabase
    private lateinit var vault: VaultRepositoryImpl
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneId.of("Europe/Istanbul"))

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        vault = VaultRepositoryImpl(db, clock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private fun item(title: String = "Banka", user: String = "ali", secret: String = "p@ss w0rd!") =
        NewVaultItem(title, user, secret, "https://banka.example", "not")

    @Test fun createAndReadBackKeepsSecretExactly() = runDb {
        val id = vault.create(item(secret = " p@ss \"w\\ord\" ü "))
        val v = vault.getById(id)!!
        assertEquals("Banka", v.title)
        assertEquals("ali", v.username)
        assertEquals(" p@ss \"w\\ord\" ü ", v.secret)        // kırpılmaz, kaçışlar bozulmaz
        assertEquals("https://banka.example", v.url)
        assertEquals("not", v.note)
    }

    @Test fun updateChangesFields() = runDb {
        val id = vault.create(item())
        vault.update(VaultUpdate(id, "Yeni", "veli", "yeniSifre1", "", ""))
        val v = vault.getById(id)!!
        assertEquals("Yeni", v.title)
        assertEquals("veli", v.username)
        assertEquals("yeniSifre1", v.secret)
        assertEquals("", v.url)
    }

    @Test fun summariesHaveNoSecretAndAreSortedIgnoringCase() = runDb {
        vault.create(item("zeta"))
        vault.create(item("Alfa"))
        vault.create(item("beta"))
        val list = vault.observeSummaries().first()
        assertEquals(listOf("Alfa", "beta", "zeta"), list.map { it.title })
        assertTrue(list.none { it.toString().contains("p@ss") })    // özet türünde şifre alanı yok
    }

    @Test fun archiveHidesButKeepsRecord() = runDb {
        val id = vault.create(item())
        vault.archive(id)
        assertNull(vault.getById(id))
        assertEquals(0, vault.observeSummaries().first().size)
        assertEquals(1, DatabaseSnapshotter.export(db).vaultItems.size)          // fiziksel silme yok
        assertTrue(runCatching { vault.archive(id) }.exceptionOrNull() is FinanceException.VaultItemNotFound)
        assertTrue(runCatching { vault.update(VaultUpdate(id, "x", "", "y", "", "")) }.exceptionOrNull() is FinanceException.VaultItemNotFound)
    }

    @Test fun missingRecordErrors() = runDb {
        assertNull(vault.getById(999))
        assertTrue(runCatching { vault.archive(999) }.exceptionOrNull() is FinanceException.VaultItemNotFound)
    }

    @Test fun useCasesValidateInput() = runDb {
        val create = CreateVaultItemUseCase(vault)
        val update = UpdateVaultItemUseCase(vault)
        val archive = ArchiveVaultItemUseCase(vault)

        assertTrue(runCatching { create(NewVaultItem("  ", "", "x", "", "")) }.exceptionOrNull() is FinanceException.InvalidInput)
        assertTrue(runCatching { create(NewVaultItem("Banka", "", "", "", "")) }.exceptionOrNull() is FinanceException.InvalidInput)
        assertTrue(runCatching { create(NewVaultItem("Banka", "", "x".repeat(1001), "", "")) }.exceptionOrNull() is FinanceException.InvalidInput)

        val id = create(NewVaultItem("  Banka   hesabı ", " ali ", " bosluklu sifre ", " https://x ", " not "))
        val v = vault.getById(id)!!
        assertEquals("Banka hesabı", v.title)                  // başlık/kullanıcı adı düzenlenir
        assertEquals("ali", v.username)
        assertEquals(" bosluklu sifre ", v.secret)              // şifre asla kırpılmaz
        assertEquals("https://x", v.url)
        assertEquals("not", v.note)

        update(VaultUpdate(id, "Banka", "ali", "yeni", "", ""))
        assertEquals("yeni", vault.getById(id)!!.secret)
        archive(id)
        assertNull(vault.getById(id))
    }

    @Test fun toStringNeverLeaksTheSecret() = runDb {
        val id = vault.create(item(secret = "SuperGizliDeger123"))
        assertFalse(vault.getById(id)!!.toString().contains("SuperGizliDeger123"))
        assertFalse(item(secret = "SuperGizliDeger123").toString().contains("SuperGizliDeger123"))
        assertFalse(VaultUpdate(id, "a", "", "SuperGizliDeger123", "", "").toString().contains("SuperGizliDeger123"))
    }

    @Test fun vaultSurvivesSnapshotBackupCodecAndValidation() = runDb {
        val a = vault.create(item("Banka", secret = "pa\"ss\\1"))
        vault.create(item("E-posta", secret = "ünlü şifre ğ"))
        vault.archive(a)

        val snapshot = DatabaseSnapshotter.export(db)
        assertEquals(2, snapshot.vaultItems.size)
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(snapshot))

        val parsed = BackupCodec.decode(BackupCodec.encode(snapshot, 1))
        assertEquals(snapshot, parsed.snapshot)
        assertEquals(2, parsed.meta.counts["vaultItems"])

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val other = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        try {
            DatabaseSnapshotter.import(other, parsed.snapshot)
            assertEquals(snapshot, DatabaseSnapshotter.export(other))
            DatabaseSnapshotter.replaceAll(other, parsed.snapshot)
            assertEquals(snapshot, DatabaseSnapshotter.export(other))
        } finally {
            other.close()
        }
    }
}
