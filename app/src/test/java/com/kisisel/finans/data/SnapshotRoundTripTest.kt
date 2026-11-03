package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.InvoiceRepositoryImpl
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Şifreli veritabanına geçiş ve yedekleme için kullanılan tam kopya mekanizmasının testi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapshotRoundTripTest {

    private lateinit var source: KisiselFinansDatabase
    private lateinit var target: KisiselFinansDatabase
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 2)

    private fun newDb(): KisiselFinansDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
    }

    @Before fun setUp() {
        source = newDb()
        target = newDb()
    }

    @After fun tearDown() {
        source.close()
        target.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun populate() {
        val persons = PersonRepositoryImpl(source.personDao(), clock)
        val ledger = LedgerRepositoryImpl(source, clock)
        val invoices = InvoiceRepositoryImpl(source, clock)

        val dayi = persons.create(NewPerson("Dayım", "0532 111 22 33", "not", 25_000))
        val ayse = persons.create(NewPerson("Ayşe", null, "", -10_000))
        persons.setArchived(ayse, true)

        val tid = invoices.createTemplate(
            NewInvoice(
                dayi, "Elektrik", InvoiceType.ELECTRICITY, 150_000, true, Recurrence.MONTHLY, null,
                LocalDate.of(2026, 11, 5), "", true, setOf(7, 3, 1, 0), 1
            ),
            day
        )
        invoices.payOccurrence(invoices.observeOccurrences().first().minByOrNull { it.dueDate }!!.id, day)

        ledger.recordClaim(NewClaim(dayi, TransactionType.LEND, 40_000, day, day.plusDays(10), "borç", "k1"))
        ledger.settle(NewSettlement(dayi, TransactionType.COLLECTION, 50_000, day, "kısmi", "k2"))
        val cancelled = ledger.recordClaim(NewClaim(dayi, TransactionType.LEND, 1_000, day, null, "yanlış", "k3"))
        ledger.cancel(cancelled)
        assertTrue(tid > 0)
    }

    @Test fun exportImportReproducesEverything() = runDb {
        populate()
        val snapshot = DatabaseSnapshotter.export(source)
        assertTrue(snapshot.totalRows > 10)
        assertTrue(snapshot.payments.isNotEmpty())
        assertTrue(snapshot.transactions.any { it.isCancelled })
        assertTrue(snapshot.persons.any { it.isArchived })

        assertTrue(DatabaseSnapshotter.isEmpty(target))
        DatabaseSnapshotter.import(target, snapshot)
        assertFalse(DatabaseSnapshotter.isEmpty(target))

        assertEquals(snapshot, DatabaseSnapshotter.export(target))
    }

    @Test fun balancesAndInvoicesMatchAfterImport() = runDb {
        populate()
        DatabaseSnapshotter.import(target, DatabaseSnapshotter.export(source))

        val a = LedgerRepositoryImpl(source, clock).observeDashboard(day).first()
        val b = LedgerRepositoryImpl(target, clock).observeDashboard(day).first()
        assertEquals(a, b)

        val occA = InvoiceRepositoryImpl(source, clock).observeOccurrences().first()
        val occB = InvoiceRepositoryImpl(target, clock).observeOccurrences().first()
        assertEquals(occA, occB)
    }

    @Test fun newRecordsContinueAfterImportedIds() = runDb {
        populate()
        val maxId = DatabaseSnapshotter.export(source).persons.maxOf { it.id }
        DatabaseSnapshotter.import(target, DatabaseSnapshotter.export(source))

        val id = PersonRepositoryImpl(target.personDao(), clock).create(NewPerson("Yeni", null, "", 0))
        assertTrue(id > maxId)
    }

    @Test fun conflictingImportRollsBackCompletely() = runDb {
        populate()
        val snapshot = DatabaseSnapshotter.export(source)
        DatabaseSnapshotter.import(target, snapshot)
        val before = DatabaseSnapshotter.export(target)

        try {
            DatabaseSnapshotter.import(target, snapshot)   // aynı kimlikler: çakışma
            fail("Çakışma hatası bekleniyordu")
        } catch (e: Exception) {
            // beklenen
        }
        assertEquals(before, DatabaseSnapshotter.export(target))
    }
}
