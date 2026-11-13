package com.kisisel.finans.backup

import com.kisisel.finans.data.local.DatabaseSnapshot
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.entity.AgendaItemEntity
import com.kisisel.finans.data.local.entity.AppSettingEntity
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import com.kisisel.finans.data.local.entity.PaymentEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** org.json gerçek uygulaması gerektiği için Robolectric ile çalışır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupCodecTest {

    private val snapshot = DatabaseSnapshot(
        persons = listOf(
            PersonEntity(1, "Dayım", "0532 111 22 33", "not \"tırnak\" ve satır\nsonu", 25_000, false, 10, 11),
            PersonEntity(2, "Ayşe", null, "", -10_000, true, 12, 13)
        ),
        invoiceTemplates = listOf(
            InvoiceTemplateEntity(
                1, 1, "Elektrik", InvoiceType.ELECTRICITY, 150_000, true, Recurrence.MONTHLY, null, 20_000,
                "", true, "7,3,1,0", 1, true, false, 14, 15
            ),
            InvoiceTemplateEntity(
                2, null, "Su", InvoiceType.WATER, null, false, Recurrence.CUSTOM_DAYS, 45, 20_100,
                "değişken", false, "", 0, true, true, 16, 17
            )
        ),
        invoiceOccurrences = listOf(
            InvoiceOccurrenceEntity(1, 1, "2026-11", 20_000, 150_000, OccurrenceStatus.PAID, 21_000, 18, 19),
            InvoiceOccurrenceEntity(2, 2, "2026-12-01", 20_300, null, OccurrenceStatus.UNPAID, null, 20, 21)
        ),
        transactions = listOf(
            TransactionEntity(1, 1, TransactionType.PAID_ON_BEHALF, 150_000, 21_000, 20_000, "Elektrik (2026-11)", 1, "k1", false, null, 22, 23),
            TransactionEntity(2, 1, TransactionType.COLLECTION, 50_000, 21_001, null, "kısmi", null, "k2", false, null, 24, 25),
            TransactionEntity(3, 1, TransactionType.LEND, 1_000, 21_002, null, "", null, "k3", true, 26, 27, 28)
        ),
        payments = listOf(PaymentEntity(1, 2, 1, 50_000, 29)),
        settings = listOf(AppSettingEntity("tema", "koyu"))
    )

    private fun gunzipJson(bytes: ByteArray): JSONObject =
        JSONObject(String(GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }, Charsets.UTF_8))

    private fun gzip(json: JSONObject): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun invalid(bytes: ByteArray): String =
        assertThrows(FinanceException.BackupInvalid::class.java) { BackupCodec.decode(bytes) }.message!!

    @Test fun roundTripPreservesEverything() {
        val parsed = BackupCodec.decode(BackupCodec.encode(snapshot, 123_456))
        assertEquals(snapshot, parsed.snapshot)
        assertEquals(123_456L, parsed.meta.createdAt)
        assertEquals(2, parsed.meta.persons)
        assertEquals(3, parsed.meta.transactions)
        assertEquals(2, parsed.meta.invoiceOccurrences)
        assertEquals(KisiselFinansDatabase.VERSION, parsed.meta.dbVersion)
    }

    @Test fun emptySnapshotRoundTrips() {
        val empty = DatabaseSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty, 1)).snapshot)
    }

    @Test fun largeAmountsKeepExactValue() {
        val big = snapshot.copy(
            transactions = listOf(snapshot.transactions[0].copy(amountKurus = 999_999_999_999L))
        ).let { it.copy(payments = emptyList()) }
        val parsed = BackupCodec.decode(BackupCodec.encode(big, 1))
        assertEquals(999_999_999_999L, parsed.snapshot.transactions.single().amountKurus)
    }

    @Test fun outputIsGzip() {
        val bytes = BackupCodec.encode(snapshot, 1)
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
    }

    @Test fun notGzipOrNotJsonIsRejected() {
        invalid("düz metin".toByteArray())
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write("json değil".toByteArray()) }
        invalid(out.toByteArray())
    }

    @Test fun wrongFormatMarkerIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1)).put("format", "başka")
        invalid(gzip(json))
    }

    @Test fun newerDatabaseVersionIsRejectedWithClearMessage() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1)).put("dbVersion", KisiselFinansDatabase.VERSION + 1)
        assertTrue(invalid(gzip(json)).contains("daha yeni"))
    }

    @Test fun unknownFormatVersionIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1)).put("formatVersion", 99)
        assertEquals(BackupCrypto.UNSUPPORTED, invalid(gzip(json)))
    }

    @Test fun countMismatchIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.getJSONObject("counts").put("persons", 99)
        invalid(gzip(json))
    }

    @Test fun missingFieldIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.getJSONArray("persons").getJSONObject(0).remove("fullName")
        invalid(gzip(json))
    }

    @Test fun unknownEnumValueIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.getJSONArray("transactions").getJSONObject(0).put("type", "SAHTE_TUR")
        invalid(gzip(json))
    }

    @Test fun missingTableIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.remove("payments")
        invalid(gzip(json))
    }

    @Test fun agendaItemsRoundTrip() {
        val withAgenda = snapshot.copy(
            agendaItems = listOf(
                AgendaItemEntity(1, "Ahmet'i ara", "not \"x\"", 20_000, 870, 1, 60, false, null, false, 1, 2),
                AgendaItemEntity(2, "Tamamlandı", "", 20_001, null, null, null, true, 5, true, 3, 4)
            )
        )
        val parsed = BackupCodec.decode(BackupCodec.encode(withAgenda, 1))
        assertEquals(withAgenda, parsed.snapshot)
        assertEquals(2, parsed.meta.counts["agendaItems"])
    }

    @Test fun oldVersion1BackupWithoutAgendaStillDecodes() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.put("dbVersion", 1)
        json.remove("agendaItems")
        json.getJSONObject("counts").remove("agendaItems")
        val parsed = BackupCodec.decode(gzip(json))
        assertEquals(snapshot.persons, parsed.snapshot.persons)
        assertEquals(emptyList<AgendaItemEntity>(), parsed.snapshot.agendaItems)
    }

    @Test fun version2BackupMissingAgendaTableIsRejected() {
        val json = gunzipJson(BackupCodec.encode(snapshot, 1))
        json.remove("agendaItems")
        invalid(gzip(json))
    }
}
