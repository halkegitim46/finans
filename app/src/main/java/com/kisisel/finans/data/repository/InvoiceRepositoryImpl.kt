package com.kisisel.finans.data.repository

import androidx.room.withTransaction
import com.kisisel.finans.core.toDb
import com.kisisel.finans.core.toLocalDate
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.dao.OccurrenceWithTemplate
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.data.mapper.formatReminderDays
import com.kisisel.finans.data.mapper.toDomain
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.LedgerCalculator
import com.kisisel.finans.domain.RecurrenceCalculator
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceTemplate
import com.kisisel.finans.domain.model.InvoiceUpdate
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.INVOICE_HORIZON_DAYS
import com.kisisel.finans.domain.repository.InvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepositoryImpl @Inject constructor(
    private val db: KisiselFinansDatabase,
    private val clock: Clock
) : InvoiceRepository {

    private val invoices = db.invoiceDao()
    private val persons = db.personDao()
    private val transactions = db.transactionDao()
    private val payments = db.paymentDao()

    override suspend fun createTemplate(cmd: NewInvoice, today: LocalDate): Long = db.withTransaction {
        cmd.personId?.let { requireActivePerson(it) }
        val now = clock.millis()
        val id = invoices.insertTemplate(
            InvoiceTemplateEntity(
                personId = cmd.personId,
                name = cmd.name,
                type = cmd.type,
                amountKurus = cmd.amountKurus,
                isAmountFixed = cmd.isAmountFixed,
                recurrence = cmd.recurrence,
                customIntervalDays = cmd.customIntervalDays,
                firstDueDate = cmd.firstDueDate.toDb(),
                note = cmd.note,
                remindersEnabled = cmd.remindersEnabled,
                reminderDaysBefore = formatReminderDays(cmd.reminderDays),
                overdueReminderEveryDays = cmd.overdueReminderEveryDays,
                createdAt = now,
                updatedAt = now
            )
        )
        val template = invoices.getTemplate(id) ?: throw FinanceException.TemplateNotFound()
        generateFor(template, today.plusDays(INVOICE_HORIZON_DAYS))
        id
    }

    override suspend fun updateTemplate(update: InvoiceUpdate) {
        db.withTransaction {
            val t = invoices.getTemplate(update.id) ?: throw FinanceException.TemplateNotFound()
            if (update.personId != null && update.personId != t.personId) requireActivePerson(update.personId)
            invoices.updateTemplate(
                t.copy(
                    personId = update.personId,
                    name = update.name,
                    type = update.type,
                    amountKurus = update.amountKurus,
                    isAmountFixed = update.isAmountFixed,
                    note = update.note,
                    remindersEnabled = update.remindersEnabled,
                    reminderDaysBefore = formatReminderDays(update.reminderDays),
                    overdueReminderEveryDays = update.overdueReminderEveryDays,
                    updatedAt = clock.millis()
                )
            )
            Unit
        }
    }

    override suspend fun archiveTemplate(id: Long) {
        db.withTransaction {
            val t = invoices.getTemplate(id) ?: throw FinanceException.TemplateNotFound()
            invoices.updateTemplate(t.copy(isArchived = true, isActive = false, updatedAt = clock.millis()))
            Unit
        }
    }

    override suspend fun getTemplate(id: Long): InvoiceTemplate? {
        val t = invoices.getTemplate(id) ?: return null
        val name = t.personId?.let { persons.getById(it)?.fullName }
        return t.toDomain(name)
    }

    override suspend fun generateOccurrences(until: LocalDate): Int = db.withTransaction {
        invoices.getActiveTemplates().sumOf { generateFor(it, until) }
    }

    override suspend fun setOccurrenceAmount(occurrenceId: Long, amountKurus: Long) {
        val rows = invoices.updateAmount(occurrenceId, amountKurus, clock.millis())
        if (rows == 0) {
            val occ = invoices.getOccurrence(occurrenceId) ?: throw FinanceException.OccurrenceNotFound()
            if (occ.status == OccurrenceStatus.PAID) throw FinanceException.OccurrenceAlreadyPaid()
            throw FinanceException.OccurrenceNotPayable()
        }
    }

    override suspend fun payOccurrence(occurrenceId: Long, paidDate: LocalDate): Long? = db.withTransaction {
        val occ = invoices.getOccurrence(occurrenceId) ?: throw FinanceException.OccurrenceNotFound()
        when (occ.status) {
            OccurrenceStatus.PAID -> throw FinanceException.OccurrenceAlreadyPaid()
            OccurrenceStatus.CANCELLED -> throw FinanceException.OccurrenceNotPayable()
            OccurrenceStatus.UNPAID -> Unit
        }
        val amount = occ.amountKurus ?: throw FinanceException.AmountRequired()
        val template = invoices.getTemplate(occ.templateId) ?: throw FinanceException.TemplateNotFound()
        val now = clock.millis()
        val paidAt = paidDate.atStartOfDay(clock.zone).toInstant().toEpochMilli()

        // Durum koşullu güncelleme: aynı dönem iki kez kapatılamaz.
        if (invoices.markPaid(occ.id, paidAt, now) == 0) throw FinanceException.OccurrenceAlreadyPaid()

        val personId = template.personId
        if (personId == null) {
            null
        } else {
            requireActivePerson(personId)
            val n = transactions.countByOccurrence(occ.id)
            val txId = transactions.insert(
                TransactionEntity(
                    personId = personId,
                    type = TransactionType.PAID_ON_BEHALF,
                    amountKurus = amount,
                    transactionDate = paidDate.toDb(),
                    dueDate = occ.dueDate,
                    description = "${template.name} (${occ.periodKey})",
                    occurrenceId = occ.id,
                    idempotencyKey = "invoice-pay-${occ.id}-$n",
                    createdAt = now,
                    updatedAt = now
                )
            )
            check(txId > 0) { "Ödeme kaydı oluşturulamadı" }
            txId
        }
    }

    override suspend fun undoPayment(occurrenceId: Long) {
        db.withTransaction {
            val occ = invoices.getOccurrence(occurrenceId) ?: throw FinanceException.OccurrenceNotFound()
            if (occ.status != OccurrenceStatus.PAID) throw FinanceException.OccurrenceNotPaid()
            val now = clock.millis()

            val tx = transactions.getActiveByOccurrence(occ.id)
            if (tx != null) {
                if (payments.sumAllocatedForClaim(tx.id) > 0) throw FinanceException.HasActiveAllocations()
                transactions.cancel(tx.id, now)
                val person = persons.getById(tx.personId) ?: throw FinanceException.PersonNotFound()
                val b = LedgerCalculator.balance(person.openingBalanceKurus, transactions.getTotalsByType(person.id))
                if (b.collected > b.totalReceivable || b.paid > b.totalPayable) {
                    throw FinanceException.WouldOverSettle()
                }
            }
            if (invoices.reopen(occ.id, now) == 0) throw FinanceException.OccurrenceNotPaid()
            Unit
        }
    }

    override suspend fun cancelOccurrence(occurrenceId: Long) {
        val rows = invoices.markCancelled(occurrenceId, clock.millis())
        if (rows == 0) {
            val occ = invoices.getOccurrence(occurrenceId) ?: throw FinanceException.OccurrenceNotFound()
            if (occ.status == OccurrenceStatus.PAID) throw FinanceException.OccurrenceAlreadyPaid()
            throw FinanceException.OccurrenceNotPayable()
        }
    }

    override fun observeTemplates(): Flow<List<InvoiceTemplate>> =
        combine(invoices.observeTemplates(), persons.observeAll()) { list, people ->
            val names = people.associate { it.id to it.fullName }
            list.map { it.toDomain(it.personId?.let { id -> names[id] }) }
        }

    override fun observeOccurrences(): Flow<List<InvoiceOccurrence>> =
        combine(invoices.observeAllOccurrences(), persons.observeAll()) { list, people ->
            val names = people.associate { it.id to it.fullName }
            list.map { it.toDomain(names) }
        }

    override fun observeOccurrence(id: Long): Flow<InvoiceOccurrence?> =
        combine(invoices.observeOccurrence(id), persons.observeAll()) { ow, people ->
            ow?.toDomain(people.associate { it.id to it.fullName })
        }

    override fun observeHistory(templateId: Long): Flow<List<InvoiceOccurrence>> =
        combine(invoices.observeOccurrencesOfTemplate(templateId), persons.observeAll()) { list, people ->
            val names = people.associate { it.id to it.fullName }
            list.map { it.toDomain(names) }
        }

    // ---- yardımcılar ----

    /**
     * Eksik dönemleri üretir. İlk dönem (0) her zaman oluşturulur (geçmiş vadeli fatura girilebilsin);
     * sonraki dönemler şablonun oluşturulduğu günden önceyse atlanır (eski aylar toplu gecikmiş görünmesin).
     * (şablon, dönem anahtarı) benzersiz olduğundan tekrar çağrı çift kayıt üretmez.
     */
    private suspend fun generateFor(t: InvoiceTemplateEntity, until: LocalDate): Int {
        val createdDay = Instant.ofEpochMilli(t.createdAt).atZone(clock.zone).toLocalDate()
        val now = clock.millis()
        var created = 0
        val due = RecurrenceCalculator.occurrencesUntil(
            t.firstDueDate.toLocalDate(), t.recurrence, t.customIntervalDays, until
        )
        for ((index, date) in due) {
            if (index > 0 && date.isBefore(createdDay)) continue
            val id = invoices.insertOccurrence(
                InvoiceOccurrenceEntity(
                    templateId = t.id,
                    periodKey = RecurrenceCalculator.periodKey(t.recurrence, date),
                    dueDate = date.toDb(),
                    amountKurus = if (t.isAmountFixed) t.amountKurus else null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (id != -1L) created++
        }
        return created
    }

    private fun OccurrenceWithTemplate.toDomain(names: Map<Long, String>) = InvoiceOccurrence(
        id = occurrence.id,
        templateId = occurrence.templateId,
        name = template.name,
        type = template.type,
        personId = template.personId,
        personName = template.personId?.let { names[it] },
        periodKey = occurrence.periodKey,
        dueDate = occurrence.dueDate.toLocalDate(),
        amountKurus = occurrence.amountKurus,
        status = occurrence.status,
        paidDate = occurrence.paidAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
    )

    private suspend fun requireActivePerson(id: Long): PersonEntity {
        val p = persons.getById(id) ?: throw FinanceException.PersonNotFound()
        if (p.isArchived) throw FinanceException.PersonArchived()
        return p
    }
}
