package com.kisisel.finans.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupServiceTest {

    private lateinit var db: KisiselFinansDatabase
    private lateinit var service: BackupService
    private lateinit var persons: PersonRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T12:30:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 4)
    private val password = "yedek-parola-1"

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        service = BackupService(db, clock).also { it.iterations = 1_000 }
        persons = PersonRepositoryImpl(db.personDao(), clock)
        ledger = LedgerRepositoryImpl(db, clock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun populate() {
        val a = persons.create(NewPerson("Dayım", "0532", "", 0))
        persons.create(NewPerson("Ayşe", null, "", -5_000))
        ledger.recordClaim(NewClaim(a, TransactionType.PAID_ON_BEHALF, 150_000, day, null, "Elektrik", "k1"))
        ledger.settle(NewSettlement(a, TransactionType.COLLECTION, 50_000, day, "", "k2"))
    }

    @Test fun backupThenRestoreReplacesCurrentData() = runDb {
        populate()
        val original = DatabaseSnapshotter.export(db)
        val backup = service.createBackup(password)

        // Yedekten sonra veri değişir
        persons.create(NewPerson("Sonradan eklenen", null, "", 0))
        assertEquals(3, DatabaseSnapshotter.export(db).persons.size)

        val parsed = service.readBackup(backup.bytes, password)
        assertEquals(2, parsed.meta.persons)
        assertEquals(2, parsed.meta.transactions)
        service.restore(parsed)

        assertEquals(original, DatabaseSnapshotter.export(db))
        val balance = ledger.observePersonSummary(original.persons.first().id).first()!!.balance
        assertEquals(100_000, balance.remainingReceivable)
    }

    @Test fun readBackupDoesNotTouchDatabase() = runDb {
        populate()
        val backup = service.createBackup(password)
        persons.create(NewPerson("Yeni", null, "", 0))
        val before = DatabaseSnapshotter.export(db)

        service.readBackup(backup.bytes, password)
        assertEquals(before, DatabaseSnapshotter.export(db))
    }

    @Test fun wrongPasswordAndCorruptedFilesAreRejectedWithoutChange() = runDb {
        populate()
        val backup = service.createBackup(password)
        val before = DatabaseSnapshotter.export(db)

        val wrong = runCatching { service.readBackup(backup.bytes, "başka-parola-9") }.exceptionOrNull()
        assertTrue(wrong is FinanceException.BackupInvalid)
        assertEquals(BackupCrypto.WRONG_PASSWORD, wrong!!.message)

        val damaged = backup.bytes.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        val corrupted = runCatching { service.readBackup(damaged, password) }.exceptionOrNull()
        assertEquals(BackupCrypto.CORRUPTED, corrupted!!.message)

        assertEquals(before, DatabaseSnapshotter.export(db))
    }

    private fun brokenSnapshot(good: com.kisisel.finans.data.local.DatabaseSnapshot) = good.copy(
        transactions = good.transactions + TransactionEntity(
            id = 99, personId = 12345, type = TransactionType.LEND, amountKurus = 1, transactionDate = 20_000,
            idempotencyKey = "kopuk", createdAt = 0, updatedAt = 0
        )
    )

    @Test fun inconsistentBackupIsRejectedByValidatorBeforeTouchingDatabase() = runDb {
        populate()
        val before = DatabaseSnapshotter.export(db)
        val good = BackupCodec.decode(BackupCodec.encode(before, 1))

        val error = runCatching { service.restore(good.copy(snapshot = brokenSnapshot(good.snapshot))) }.exceptionOrNull()

        assertTrue(error is FinanceException.BackupInvalid)
        assertTrue(error!!.message!!.contains("uyumsuz"))
        assertEquals(before, DatabaseSnapshotter.export(db))
    }

    @Test fun readBackupRejectsInconsistentContentAtPreviewStage() = runDb {
        populate()
        val good = BackupCodec.decode(BackupCodec.encode(DatabaseSnapshotter.export(db), 1))
        val plain = BackupCodec.encode(brokenSnapshot(good.snapshot), 1)
        val file = BackupCrypto.encrypt(plain, password.toCharArray(), 1_000)

        val error = runCatching { service.readBackup(file, password) }.exceptionOrNull()
        assertTrue(error is FinanceException.BackupInvalid)
        assertTrue(error!!.message!!.contains("uyumsuz"))
    }

    @Test fun databaseLevelFailureRollsBackAtomically() = runDb {
        // Doğrulayıcıyı atlayan doğrudan yazma: yabancı anahtar ihlali tüm işlemi geri almalı.
        populate()
        val before = DatabaseSnapshotter.export(db)
        val broken = brokenSnapshot(before)
        val failed = runCatching { DatabaseSnapshotter.replaceAll(db, broken) }.isFailure
        assertTrue(failed)
        assertEquals(before, DatabaseSnapshotter.export(db))
    }

    @Test fun emptyBackupRestoresToEmptyDatabase() = runDb {
        val backup = service.createBackup(password)
        populate()
        assertFalse(DatabaseSnapshotter.isEmpty(db))

        service.restore(service.readBackup(backup.bytes, password))
        assertTrue(DatabaseSnapshotter.isEmpty(db))
    }

    @Test fun weakBackupPasswordIsRejected() = runDb {
        val error = runCatching { service.createBackup("kısa") }.exceptionOrNull()
        assertTrue(error is FinanceException.InvalidInput)
    }

    @Test fun fileNameAndMetaAreFilled() = runDb {
        populate()
        val backup = service.createBackup(password)
        assertEquals("KisiselFinansTakip_yedek_2026-11-04_1530.kftbak", backup.suggestedName)   // İstanbul UTC+3
        assertEquals(clock.millis(), backup.meta.createdAt)
        assertEquals(2, backup.meta.persons)
    }

    @Test fun restoredDataKeepsWorkingAfterwards() = runDb {
        populate()
        val backup = service.createBackup(password)
        service.restore(service.readBackup(backup.bytes, password))

        val id = persons.create(NewPerson("Yeni kişi", null, "", 0))
        val maxOld = DatabaseSnapshotter.export(db).persons.filter { it.fullName != "Yeni kişi" }.maxOf { it.id }
        assertTrue(id > maxOld)
        ledger.recordClaim(NewClaim(id, TransactionType.LEND, 1_000, day, null, "", "yeni-anahtar"))
        assertEquals(1_000, ledger.observePersonSummary(id).first()!!.balance.remainingReceivable)
    }
}
