package com.kisisel.finans.backup

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
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Yanlış-pozitif koruması: uygulamanın kendi ürettiği HER veri (iptaller, ödeme geri alma, kısmi/toplu kapatma,
 * arşiv, değişken tutar, başlangıç bakiyesi...) doğrulayıcıdan geçmeli; aksi halde geçerli yedekler reddedilirdi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupValidatorRealDataTest {
    private lateinit var db: KisiselFinansDatabase
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun richRealWorldDataPassesValidation() = runBlocking {
        val persons = PersonRepositoryImpl(db.personDao(), clock)
        val ledger = LedgerRepositoryImpl(db, clock)
        val invoices = InvoiceRepositoryImpl(db, clock)

        val a = persons.create(NewPerson("Dayım", "0532 111 22 33", "not", 25_000))     // başlangıç alacağı
        val b = persons.create(NewPerson("Ayşe", null, "", -5_000))                      // başlangıç borcu
        val c = persons.create(NewPerson("Eski kişi", null, "", 0))
        persons.setArchived(c, true)

        // Sabit tutarlı fatura: öde, geri al, tekrar öde; ikinci dönemi iptal et
        invoices.createTemplate(
            NewInvoice(a, "Elektrik", InvoiceType.ELECTRICITY, 150_000, true, Recurrence.MONTHLY, null,
                LocalDate.of(2026, 11, 5), "", true, setOf(7, 3, 1, 0), 1),
            day
        )
        val elektrik = invoices.observeOccurrences().first().filter { it.name == "Elektrik" }.sortedBy { it.dueDate }
        invoices.payOccurrence(elektrik[0].id, day)
        invoices.undoPayment(elektrik[0].id)
        invoices.payOccurrence(elektrik[0].id, day)
        invoices.cancelOccurrence(elektrik[1].id)

        // Değişken tutarlı, kendi faturam
        invoices.createTemplate(
            NewInvoice(null, "Su", InvoiceType.WATER, null, false, Recurrence.MONTHLY, null,
                LocalDate.of(2026, 11, 10), "", true, setOf(3), 1),
            day
        )
        val su = invoices.observeOccurrences().first().first { it.name == "Su" && it.status == OccurrenceStatus.UNPAID }
        invoices.setOccurrenceAmount(su.id, 90_000)
        invoices.payOccurrence(su.id, day)

        // Defter: borç verme, borç alma, kısmi tahsilat, iptal edilen tahsilat, kapatma, iptal edilen alacak
        ledger.recordClaim(NewClaim(a, TransactionType.LEND, 40_000, day, day.plusDays(10), "borç", "k1"))
        ledger.recordClaim(NewClaim(b, TransactionType.BORROW, 20_000, day, null, "", "k2"))
        ledger.settle(NewSettlement(a, TransactionType.COLLECTION, 50_000, day, "kısmi", "k3"))
        val extra = ledger.settle(NewSettlement(a, TransactionType.COLLECTION, 10_000, day, "", "k4"))
        ledger.cancel(extra.transactionId)
        ledger.settle(NewSettlement(b, TransactionType.DEBT_PAYMENT, 5_000, day, "", "k5"))
        ledger.settle(NewSettlement(b, TransactionType.DEBT_PAYMENT, null, day, "kapat", "k6"))
        val wrong = ledger.recordClaim(NewClaim(a, TransactionType.LEND, 1_000, day, null, "yanlış", "k7"))
        ledger.cancel(wrong)

        val snapshot = DatabaseSnapshotter.export(db)
        assertTrue("örnek veri zengin olmalı", snapshot.totalRows > 15)
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(snapshot))

        // Şifreli yedek gidiş-dönüşünden sonra da geçerli kalmalı
        val parsed = BackupCodec.decode(BackupCodec.encode(snapshot, 1))
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(parsed.snapshot))
        Unit
    }
}
