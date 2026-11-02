package com.kisisel.finans.domain.usecase

import com.kisisel.finans.domain.Validators
import com.kisisel.finans.domain.model.InvoiceUpdate
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.repository.INVOICE_HORIZON_DAYS
import com.kisisel.finans.domain.repository.InvoiceRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CreateInvoiceUseCase @Inject constructor(
    private val invoices: InvoiceRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(cmd: NewInvoice): Long {
        val validated = cmd.copy(
            name = Validators.invoiceName(cmd.name),
            amountKurus = Validators.invoiceAmount(cmd.isAmountFixed, cmd.amountKurus),
            customIntervalDays = if (cmd.recurrence == Recurrence.CUSTOM_DAYS) {
                Validators.customInterval(cmd.customIntervalDays)
            } else {
                null
            },
            firstDueDate = Validators.date(cmd.firstDueDate),
            note = Validators.note(cmd.note),
            reminderDays = Validators.reminderDays(cmd.reminderDays),
            overdueReminderEveryDays = Validators.overdueInterval(cmd.overdueReminderEveryDays)
        )
        return invoices.createTemplate(validated, LocalDate.now(clock))
    }
}

class UpdateInvoiceUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(cmd: InvoiceUpdate) {
        invoices.updateTemplate(
            cmd.copy(
                name = Validators.invoiceName(cmd.name),
                amountKurus = Validators.invoiceAmount(cmd.isAmountFixed, cmd.amountKurus),
                note = Validators.note(cmd.note),
                reminderDays = Validators.reminderDays(cmd.reminderDays),
                overdueReminderEveryDays = Validators.overdueInterval(cmd.overdueReminderEveryDays)
            )
        )
    }
}

class ArchiveInvoiceUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(templateId: Long) = invoices.archiveTemplate(templateId)
}

/** Uygulama açılışında ve şablon değişince çağrılır; eksik dönemleri üretir. */
class EnsureOccurrencesUseCase @Inject constructor(
    private val invoices: InvoiceRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(): Int =
        invoices.generateOccurrences(LocalDate.now(clock).plusDays(INVOICE_HORIZON_DAYS))
}

class SetOccurrenceAmountUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(occurrenceId: Long, amountKurus: Long) =
        invoices.setOccurrenceAmount(occurrenceId, Validators.amount(amountKurus))
}

class PayInvoiceUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(occurrenceId: Long, paidDate: LocalDate): Long? =
        invoices.payOccurrence(occurrenceId, Validators.date(paidDate))
}

class UndoInvoicePaymentUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(occurrenceId: Long) = invoices.undoPayment(occurrenceId)
}

class CancelOccurrenceUseCase @Inject constructor(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(occurrenceId: Long) = invoices.cancelOccurrence(occurrenceId)
}
