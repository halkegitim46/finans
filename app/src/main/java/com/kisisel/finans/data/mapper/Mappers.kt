package com.kisisel.finans.data.mapper

import com.kisisel.finans.core.toLocalDate
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.Person

fun PersonEntity.toDomain() = Person(
    id = id,
    fullName = fullName,
    phone = phone,
    note = note,
    openingBalanceKurus = openingBalanceKurus,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TransactionEntity.toDomain() = LedgerEntry(
    id = id,
    personId = personId,
    type = type,
    amountKurus = amountKurus,
    date = transactionDate.toLocalDate(),
    dueDate = dueDate?.toLocalDate(),
    description = description,
    occurrenceId = occurrenceId,
    isCancelled = isCancelled,
    createdAt = createdAt
)

// ---- Fatura ----
fun parseReminderDays(raw: String): Set<Int> =
    raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

fun formatReminderDays(days: Set<Int>): String = days.sortedDescending().joinToString(",")

fun com.kisisel.finans.data.local.entity.InvoiceTemplateEntity.toDomain(personName: String?) =
    com.kisisel.finans.domain.model.InvoiceTemplate(
        id = id,
        personId = personId,
        personName = personName,
        name = name,
        type = type,
        amountKurus = amountKurus,
        isAmountFixed = isAmountFixed,
        recurrence = recurrence,
        customIntervalDays = customIntervalDays,
        firstDueDate = firstDueDate.toLocalDate(),
        note = note,
        remindersEnabled = remindersEnabled,
        reminderDays = parseReminderDays(reminderDaysBefore),
        overdueReminderEveryDays = overdueReminderEveryDays,
        isActive = isActive,
        isArchived = isArchived
    )
