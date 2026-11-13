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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class BackupMeta(
    val formatVersion: Int,
    val dbVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Int>
) {
    val persons: Int get() = counts["persons"] ?: 0
    val transactions: Int get() = counts["transactions"] ?: 0
    val invoiceOccurrences: Int get() = counts["invoiceOccurrences"] ?: 0
}

data class ParsedBackup(val meta: BackupMeta, val snapshot: DatabaseSnapshot)

/**
 * Snapshot ⇄ JSON (gzip). Tutarlar kuruş (Long) olarak yazılır; ondalık sayı kullanılmaz.
 * Okurken biçim, sürüm, alan ve kayıt sayısı doğrulanır; geçersizse [FinanceException.BackupInvalid].
 */
object BackupCodec {
    const val FORMAT = "kft-backup"
    const val FORMAT_VERSION = 1
    private const val MAX_PLAIN_BYTES = 64 * 1024 * 1024
    private const val INVALID = "Yedek içeriği geçersiz veya eksik."

    fun metaOf(s: DatabaseSnapshot, createdAt: Long) = BackupMeta(
        formatVersion = FORMAT_VERSION,
        dbVersion = KisiselFinansDatabase.VERSION,
        createdAt = createdAt,
        counts = mapOf(
            "persons" to s.persons.size,
            "invoiceTemplates" to s.invoiceTemplates.size,
            "invoiceOccurrences" to s.invoiceOccurrences.size,
            "transactions" to s.transactions.size,
            "payments" to s.payments.size,
            "settings" to s.settings.size,
            "agendaItems" to s.agendaItems.size
        )
    )

    fun encode(s: DatabaseSnapshot, createdAt: Long): ByteArray {
        val meta = metaOf(s, createdAt)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", meta.formatVersion)
            .put("dbVersion", meta.dbVersion)
            .put("createdAt", createdAt)
            .put("counts", JSONObject(meta.counts))
            .put("persons", array(s.persons) { it.toJson() })
            .put("invoiceTemplates", array(s.invoiceTemplates) { it.toJson() })
            .put("invoiceOccurrences", array(s.invoiceOccurrences) { it.toJson() })
            .put("transactions", array(s.transactions) { it.toJson() })
            .put("payments", array(s.payments) { it.toJson() })
            .put("settings", array(s.settings) { it.toJson() })
            .put("agendaItems", array(s.agendaItems) { it.toJson() })
        return gzip(root.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(plain: ByteArray): ParsedBackup {
        val root = try {
            JSONObject(String(gunzip(plain), Charsets.UTF_8))
        } catch (e: JSONException) {
            throw FinanceException.BackupInvalid(INVALID)
        } catch (e: IOException) {
            throw FinanceException.BackupInvalid(INVALID)
        }
        try {
            if (root.getString("format") != FORMAT) throw FinanceException.BackupInvalid(INVALID)
            val formatVersion = root.getInt("formatVersion")
            if (formatVersion != FORMAT_VERSION) throw FinanceException.BackupInvalid(BackupCrypto.UNSUPPORTED)
            val dbVersion = root.getInt("dbVersion")
            if (dbVersion > KisiselFinansDatabase.VERSION) {
                throw FinanceException.BackupInvalid("Bu yedek uygulamanın daha yeni bir sürümüne ait. Önce uygulamayı güncelleyin.")
            }
            val createdAt = root.getLong("createdAt")

            val snapshot = DatabaseSnapshot(
                persons = root.getJSONArray("persons").mapObjects(::personFrom),
                invoiceTemplates = root.getJSONArray("invoiceTemplates").mapObjects(::templateFrom),
                invoiceOccurrences = root.getJSONArray("invoiceOccurrences").mapObjects(::occurrenceFrom),
                transactions = root.getJSONArray("transactions").mapObjects(::transactionFrom),
                payments = root.getJSONArray("payments").mapObjects(::paymentFrom),
                settings = root.getJSONArray("settings").mapObjects(::settingFrom),
                // Sürüm 1 yedeklerinde ajanda yoktur.
                agendaItems = if (dbVersion >= 2) root.getJSONArray("agendaItems").mapObjects(::agendaFrom) else emptyList()
            )

            val expected = metaOf(snapshot, createdAt).counts
            val declared = root.getJSONObject("counts")
            for ((key, count) in expected) {
                if (key == "agendaItems" && dbVersion < 2) continue
                if (declared.getInt(key) != count) throw FinanceException.BackupInvalid(INVALID)
            }
            return ParsedBackup(BackupMeta(formatVersion, dbVersion, createdAt, expected), snapshot)
        } catch (e: JSONException) {
            throw FinanceException.BackupInvalid(INVALID)
        } catch (e: IllegalArgumentException) {
            throw FinanceException.BackupInvalid(INVALID)
        }
    }

    // ---- gzip ----
    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { readLimited(it, MAX_PLAIN_BYTES) }

    // ---- JSON yardımcıları ----
    private fun <T> array(items: List<T>, f: (T) -> JSONObject): JSONArray =
        JSONArray().also { a -> items.forEach { a.put(f(it)) } }

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    // ---- varlık ⇄ JSON ----
    private fun PersonEntity.toJson() = JSONObject()
        .put("id", id).put("fullName", fullName).putNullable("phone", phone).put("note", note)
        .put("openingBalanceKurus", openingBalanceKurus).put("isArchived", isArchived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun personFrom(o: JSONObject) = PersonEntity(
        id = o.getLong("id"), fullName = o.getString("fullName"), phone = o.stringOrNull("phone"),
        note = o.getString("note"), openingBalanceKurus = o.getLong("openingBalanceKurus"),
        isArchived = o.getBoolean("isArchived"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
    )

    private fun InvoiceTemplateEntity.toJson() = JSONObject()
        .put("id", id).putNullable("personId", personId).put("name", name).put("type", type.name)
        .putNullable("amountKurus", amountKurus).put("isAmountFixed", isAmountFixed)
        .put("recurrence", recurrence.name).putNullable("customIntervalDays", customIntervalDays)
        .put("firstDueDate", firstDueDate).put("note", note).put("remindersEnabled", remindersEnabled)
        .put("reminderDaysBefore", reminderDaysBefore).put("overdueReminderEveryDays", overdueReminderEveryDays)
        .put("isActive", isActive).put("isArchived", isArchived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun templateFrom(o: JSONObject) = InvoiceTemplateEntity(
        id = o.getLong("id"), personId = o.longOrNull("personId"), name = o.getString("name"),
        type = InvoiceType.valueOf(o.getString("type")), amountKurus = o.longOrNull("amountKurus"),
        isAmountFixed = o.getBoolean("isAmountFixed"), recurrence = Recurrence.valueOf(o.getString("recurrence")),
        customIntervalDays = o.intOrNull("customIntervalDays"), firstDueDate = o.getLong("firstDueDate"),
        note = o.getString("note"), remindersEnabled = o.getBoolean("remindersEnabled"),
        reminderDaysBefore = o.getString("reminderDaysBefore"),
        overdueReminderEveryDays = o.getInt("overdueReminderEveryDays"),
        isActive = o.getBoolean("isActive"), isArchived = o.getBoolean("isArchived"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
    )

    private fun InvoiceOccurrenceEntity.toJson() = JSONObject()
        .put("id", id).put("templateId", templateId).put("periodKey", periodKey).put("dueDate", dueDate)
        .putNullable("amountKurus", amountKurus).put("status", status.name).putNullable("paidAt", paidAt)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun occurrenceFrom(o: JSONObject) = InvoiceOccurrenceEntity(
        id = o.getLong("id"), templateId = o.getLong("templateId"), periodKey = o.getString("periodKey"),
        dueDate = o.getLong("dueDate"), amountKurus = o.longOrNull("amountKurus"),
        status = OccurrenceStatus.valueOf(o.getString("status")), paidAt = o.longOrNull("paidAt"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
    )

    private fun TransactionEntity.toJson() = JSONObject()
        .put("id", id).put("personId", personId).put("type", type.name).put("amountKurus", amountKurus)
        .put("transactionDate", transactionDate).putNullable("dueDate", dueDate).put("description", description)
        .putNullable("occurrenceId", occurrenceId).put("idempotencyKey", idempotencyKey)
        .put("isCancelled", isCancelled).putNullable("cancelledAt", cancelledAt)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun transactionFrom(o: JSONObject) = TransactionEntity(
        id = o.getLong("id"), personId = o.getLong("personId"), type = TransactionType.valueOf(o.getString("type")),
        amountKurus = o.getLong("amountKurus"), transactionDate = o.getLong("transactionDate"),
        dueDate = o.longOrNull("dueDate"), description = o.getString("description"),
        occurrenceId = o.longOrNull("occurrenceId"), idempotencyKey = o.getString("idempotencyKey"),
        isCancelled = o.getBoolean("isCancelled"), cancelledAt = o.longOrNull("cancelledAt"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
    )

    private fun PaymentEntity.toJson() = JSONObject()
        .put("id", id).put("settlementTxId", settlementTxId).put("claimTxId", claimTxId)
        .put("amountKurus", amountKurus).put("createdAt", createdAt)

    private fun paymentFrom(o: JSONObject) = PaymentEntity(
        id = o.getLong("id"), settlementTxId = o.getLong("settlementTxId"), claimTxId = o.getLong("claimTxId"),
        amountKurus = o.getLong("amountKurus"), createdAt = o.getLong("createdAt")
    )

    private fun AppSettingEntity.toJson() = JSONObject().put("settingKey", settingKey).put("settingValue", settingValue)

    private fun settingFrom(o: JSONObject) =
        AppSettingEntity(settingKey = o.getString("settingKey"), settingValue = o.getString("settingValue"))

    private fun AgendaItemEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("note", note).put("date", date)
        .putNullable("timeMinutes", timeMinutes).putNullable("personId", personId)
        .putNullable("remindMinutesBefore", remindMinutesBefore).put("isDone", isDone)
        .putNullable("doneAt", doneAt).put("isArchived", isArchived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun agendaFrom(o: JSONObject) = AgendaItemEntity(
        id = o.getLong("id"), title = o.getString("title"), note = o.getString("note"), date = o.getLong("date"),
        timeMinutes = o.intOrNull("timeMinutes"), personId = o.longOrNull("personId"),
        remindMinutesBefore = o.intOrNull("remindMinutesBefore"), isDone = o.getBoolean("isDone"),
        doneAt = o.longOrNull("doneAt"), isArchived = o.getBoolean("isArchived"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
    )
}
