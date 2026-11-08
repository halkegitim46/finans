package com.kisisel.finans.backup

import com.kisisel.finans.core.Money
import com.kisisel.finans.data.local.DatabaseSnapshot
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupSnapshotValidatorTest {
    private val day = LocalDate.of(2026, 11, 4).toEpochDay()

    private fun person(id: Long, name: String = "Kişi$id", opening: Long = 0) =
        PersonEntity(id = id, fullName = name, phone = null, note = "", openingBalanceKurus = opening, isArchived = false, createdAt = 0, updatedAt = 0)

    private fun tx(
        id: Long, personId: Long, type: TransactionType, amount: Long, key: String = "k$id",
        occurrenceId: Long? = null, due: Long? = null, cancelled: Boolean = false, cancelledAt: Long? = null
    ) = TransactionEntity(
        id = id, personId = personId, type = type, amountKurus = amount, transactionDate = day, dueDate = due,
        description = "", occurrenceId = occurrenceId, idempotencyKey = key, isCancelled = cancelled,
        cancelledAt = cancelledAt, createdAt = 0, updatedAt = 0
    )

    /** Tutarlı örnek: Kişi1 faturadan 1.500 TL alacak, 500 TL tahsilat; Kişi2 200 TL borç, 50 TL ödeme. */
    private fun valid() = DatabaseSnapshot(
        persons = listOf(person(1), person(2)),
        invoiceTemplates = listOf(
            InvoiceTemplateEntity(
                id = 1, personId = 1, name = "Elektrik", type = InvoiceType.ELECTRICITY, amountKurus = 150_000,
                isAmountFixed = true, recurrence = Recurrence.MONTHLY, customIntervalDays = null, firstDueDate = day,
                note = "", remindersEnabled = true, reminderDaysBefore = "7,3,1,0", overdueReminderEveryDays = 1,
                isActive = true, isArchived = false, createdAt = 0, updatedAt = 0
            )
        ),
        invoiceOccurrences = listOf(
            InvoiceOccurrenceEntity(
                id = 1, templateId = 1, periodKey = "2026-11", dueDate = day + 1, amountKurus = 150_000,
                status = OccurrenceStatus.PAID, paidAt = 1, createdAt = 0, updatedAt = 0
            )
        ),
        transactions = listOf(
            tx(1, 1, TransactionType.PAID_ON_BEHALF, 150_000, occurrenceId = 1, due = day + 1),
            tx(2, 1, TransactionType.COLLECTION, 50_000),
            tx(3, 2, TransactionType.BORROW, 20_000),
            tx(4, 2, TransactionType.DEBT_PAYMENT, 5_000)
        ),
        payments = listOf(
            PaymentEntity(id = 1, settlementTxId = 2, claimTxId = 1, amountKurus = 50_000, createdAt = 0),
            PaymentEntity(id = 2, settlementTxId = 4, claimTxId = 3, amountKurus = 5_000, createdAt = 0)
        ),
        settings = emptyList()
    )

    private fun DatabaseSnapshot.mapTx(id: Long, f: (TransactionEntity) -> TransactionEntity) =
        copy(transactions = transactions.map { if (it.id == id) f(it) else it })

    private fun DatabaseSnapshot.mapPayment(id: Long, f: (PaymentEntity) -> PaymentEntity) =
        copy(payments = payments.map { if (it.id == id) f(it) else it })

    private fun expect(s: DatabaseSnapshot, text: String) {
        val problems = BackupSnapshotValidator.validate(s)
        assertTrue("Beklenen sorun bulunamadı: '$text'. Bulunanlar: $problems", problems.any { it.contains(text) })
    }

    @Test fun validSnapshotsPass() {
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(valid()))
        val empty = DatabaseSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(empty))
        BackupSnapshotValidator.requireValid(valid())   // fırlatmamalı
    }

    // ---- tutar ve tarih ----
    @Test fun invalidAmounts() {
        expect(valid().mapTx(1) { it.copy(amountKurus = -5) }, "tutarı geçersiz")
        expect(valid().mapTx(1) { it.copy(amountKurus = 0) }, "tutarı geçersiz")
        expect(valid().mapTx(1) { it.copy(amountKurus = Money.MAX_KURUS + 1) }, "tutarı geçersiz")
        expect(valid().mapPayment(1) { it.copy(amountKurus = 0) }, "Dağıtım #1 tutarı geçersiz")
    }

    @Test fun datesOutOfRange() {
        expect(valid().mapTx(1) { it.copy(transactionDate = 1) }, "tarihi geçersiz")
        expect(valid().mapTx(1) { it.copy(dueDate = 99_999_999) }, "vade tarihi geçersiz")
    }

    @Test fun openingBalanceTooLarge() {
        expect(valid().copy(persons = listOf(person(1, opening = Money.MAX_KURUS + 1), person(2))), "başlangıç bakiyesi")
    }

    // ---- kimlik ve ilişkiler ----
    @Test fun duplicateAndOrphanRecords() {
        val v = valid()
        expect(v.copy(persons = v.persons + v.persons[0]), "tekrar ediyor")
        expect(v.mapTx(1) { it.copy(personId = 99) }, "olmayan kişiye bağlı")
        expect(v.mapTx(2) { it.copy(idempotencyKey = "k1") }, "anahtarı başka bir işlemle aynı")
        expect(v.mapTx(2) { it.copy(idempotencyKey = " ") }, "anahtarı boş")
        expect(v.mapTx(1) { it.copy(occurrenceId = 99) }, "olmayan fatura dönemine")
        expect(v.copy(invoiceTemplates = v.invoiceTemplates.map { it.copy(personId = 99) }), "olmayan kişiye bağlı")
        expect(v.copy(invoiceOccurrences = v.invoiceOccurrences.map { it.copy(templateId = 99) }), "olmayan faturaya bağlı")
        expect(v.copy(invoiceOccurrences = v.invoiceOccurrences + v.invoiceOccurrences[0].copy(id = 2)), "aynı dönem için tekrar")
        expect(v.copy(settings = listOf(AppSettingEntity("a", "1"), AppSettingEntity("a", "2"))), "Ayar anahtarı tekrar")
    }

    @Test fun invoiceLinkedTransactionMustBePaidOnBehalf() {
        expect(valid().mapTx(2) { it.copy(type = TransactionType.LEND, occurrenceId = 1) }, "türü uygun değil")
    }

    @Test fun cancelFlagsMustBeConsistent() {
        expect(valid().mapTx(2) { it.copy(isCancelled = true, cancelledAt = null) }, "iptal bilgisi tutarsız")
        expect(valid().mapTx(2) { it.copy(isCancelled = false, cancelledAt = 5) }, "iptal bilgisi tutarsız")
    }

    // ---- fatura dönemleri ----
    @Test fun occurrenceStatusRules() {
        val v = valid()
        expect(v.copy(invoiceOccurrences = v.invoiceOccurrences.map { it.copy(paidAt = null) }), "ödeme zamanı yok")
        expect(v.copy(invoiceOccurrences = v.invoiceOccurrences.map { it.copy(amountKurus = null) }), "tutarsız")
        expect(
            v.copy(invoiceOccurrences = v.invoiceOccurrences.map { it.copy(status = OccurrenceStatus.UNPAID) }),
            "ödeme zamanı içeriyor"
        )
    }

    @Test fun templateRules() {
        val v = valid()
        expect(v.copy(invoiceTemplates = v.invoiceTemplates.map { it.copy(recurrence = Recurrence.CUSTOM_DAYS, customIntervalDays = null) }), "özel tekrar aralığı")
        expect(v.copy(invoiceTemplates = v.invoiceTemplates.map { it.copy(amountKurus = -1) }), "Fatura #1 tutarı geçersiz")
        expect(v.copy(invoiceTemplates = v.invoiceTemplates.map { it.copy(overdueReminderEveryDays = 99) }), "gecikme hatırlatma")
    }

    // ---- dağıtımlar ----
    @Test fun allocationTypeAndOwnershipRules() {
        val v = valid()
        expect(v.mapPayment(1) { it.copy(settlementTxId = 1) }, "tahsilat/ödeme olmayan işleme")   // settlement bir alacak işlemi
        expect(v.mapPayment(1) { it.copy(claimTxId = 2) }, "alacak/borç olmayan işleme")            // claim bir tahsilat
        expect(v.mapPayment(2) { it.copy(claimTxId = 1) }, "farklı kişilerin")                       // kişi 2'nin ödemesi kişi 1'in alacağına
        expect(v.mapPayment(1) { it.copy(claimTxId = 99) }, "olmayan işleme bağlı")
        val withBorrow = v.copy(transactions = v.transactions + tx(5, 1, TransactionType.BORROW, 10_000))
        expect(
            withBorrow.copy(payments = withBorrow.payments + PaymentEntity(3, 2, 5, 1_000, 0)),
            "yönü uyumsuz"
        )
    }

    @Test fun allocationAmountLimits() {
        val v = valid()
        expect(v.mapPayment(1) { it.copy(amountKurus = 160_000) }, "işlem tutarını aşıyor")
        expect(v.mapPayment(1) { it.copy(amountKurus = 60_000) }, "dağıtımları tutarını aşıyor")
        val duplicatePair = v.copy(payments = v.payments + PaymentEntity(3, 2, 1, 1, 0))
        expect(duplicatePair, "aynı işlem çifti için tekrar")
    }

    @Test fun cancelledClaimCannotHaveActiveAllocations() {
        expect(valid().mapTx(1) { it.copy(isCancelled = true, cancelledAt = 1) }, "iptal edilmiş kayda etkin")
    }

    @Test fun cancelledSettlementKeepsHistoricalAllocationValid() {
        // Tahsilat iptal edildi: dağıtım kaydı geçmiş olarak kalabilir; hiçbir kural ihlal edilmez.
        val s = valid().mapTx(2) { it.copy(isCancelled = true, cancelledAt = 1) }
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(s))
    }

    // ---- bakiye değişmezi ----
    @Test fun settlementsCannotExceedClaimsPerPerson() {
        expect(valid().mapTx(2) { it.copy(amountKurus = 200_000) }, "tahsilatları alacağından fazla")
        expect(valid().mapTx(4) { it.copy(amountKurus = 25_000) }, "ödemeleri borcundan fazla")
    }

    @Test fun openingBalanceCountsAsClaim() {
        // Başlangıç alacağı 100 TL olan kişiden, işlem alacağı olmadan 80 TL tahsilat geçerlidir.
        val s = DatabaseSnapshot(
            persons = listOf(person(1, opening = 10_000)), invoiceTemplates = emptyList(), invoiceOccurrences = emptyList(),
            transactions = listOf(tx(1, 1, TransactionType.COLLECTION, 8_000)), payments = emptyList(), settings = emptyList()
        )
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(s))
        expect(s.mapTx(1) { it.copy(amountKurus = 10_001) }, "tahsilatları alacağından fazla")
    }

    // ---- requireValid ----
    @Test fun requireValidThrowsUserFacingMessage() {
        val error = assertThrows(FinanceException.BackupInvalid::class.java) {
            BackupSnapshotValidator.requireValid(valid().mapTx(1) { it.copy(amountKurus = -1) })
        }
        assertTrue(error.message!!.contains("uyumsuz"))
    }

    @Test fun requireValidMentionsAdditionalProblems() {
        val two = valid().mapTx(1) { it.copy(amountKurus = -1) }.mapTx(2) { it.copy(amountKurus = -2) }
        val error = assertThrows(FinanceException.BackupInvalid::class.java) { BackupSnapshotValidator.requireValid(two) }
        assertTrue(error.message!!, error.message!!.contains("sorun daha"))
    }

    @Test fun problemListIsCapped() {
        val many = (1L..200L).map { person(it, name = "") }
        val problems = BackupSnapshotValidator.validate(valid().copy(persons = many))
        assertTrue(problems.size <= 50)
    }

    @Test fun messagesContainNoPersonalData() {
        val s = valid().copy(persons = listOf(person(1, name = "GizliAd Soyad"), person(2))).mapTx(1) { it.copy(amountKurus = -1) }
        assertTrue(BackupSnapshotValidator.validate(s).none { it.contains("GizliAd") })
    }
}
