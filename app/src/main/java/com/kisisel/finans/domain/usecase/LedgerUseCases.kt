package com.kisisel.finans.domain.usecase

import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.Validators
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.SettlementResult
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.LedgerRepository
import java.time.LocalDate
import javax.inject.Inject

/** Borç verme, borç alma, kişi adına ödeme. */
class RecordClaimUseCase @Inject constructor(private val ledger: LedgerRepository) {
    suspend operator fun invoke(
        personId: Long,
        type: TransactionType,
        amountKurus: Long,
        date: LocalDate,
        dueDate: LocalDate?,
        description: String,
        idempotencyKey: String
    ): Long {
        if (!type.createsReceivable && !type.createsPayable) {
            throw FinanceException.InvalidInput("Bu işlem türü alacak/borç oluşturmaz.")
        }
        val d = Validators.date(date)
        return ledger.recordClaim(
            NewClaim(
                personId = personId,
                type = type,
                amountKurus = Validators.amount(amountKurus),
                date = d,
                dueDate = Validators.dueDate(d, dueDate),
                description = Validators.note(description),
                idempotencyKey = Validators.idempotencyKey(idempotencyKey)
            )
        )
    }
}

/** Tahsilat alma / borç ödeme (kısmi dahil). Kalan tutarı aşamaz. */
class RecordSettlementUseCase @Inject constructor(private val ledger: LedgerRepository) {
    suspend operator fun invoke(
        personId: Long,
        type: TransactionType,
        amountKurus: Long,
        date: LocalDate,
        description: String,
        idempotencyKey: String
    ): SettlementResult {
        if (!type.isSettlement) throw FinanceException.InvalidInput("Bu işlem türü tahsilat/ödeme değildir.")
        return ledger.settle(
            NewSettlement(
                personId = personId,
                type = type,
                amountKurus = Validators.amount(amountKurus),
                date = Validators.date(date),
                description = Validators.note(description),
                idempotencyKey = Validators.idempotencyKey(idempotencyKey)
            )
        )
    }
}

/** Alacak kapatma (COLLECTION) veya borç kapatma (DEBT_PAYMENT): kalanın tamamı tek işlemde. */
class CloseAccountUseCase @Inject constructor(private val ledger: LedgerRepository) {
    suspend operator fun invoke(
        personId: Long,
        type: TransactionType,
        date: LocalDate,
        description: String,
        idempotencyKey: String
    ): SettlementResult {
        if (!type.isSettlement) throw FinanceException.InvalidInput("Kapatma için tahsilat veya borç ödeme türü gerekir.")
        return ledger.settle(
            NewSettlement(
                personId = personId,
                type = type,
                amountKurus = null,
                date = Validators.date(date),
                description = Validators.note(description),
                idempotencyKey = Validators.idempotencyKey(idempotencyKey)
            )
        )
    }
}

class CancelTransactionUseCase @Inject constructor(private val ledger: LedgerRepository) {
    suspend operator fun invoke(transactionId: Long) = ledger.cancel(transactionId)
}

class UpdateTransactionDetailsUseCase @Inject constructor(private val ledger: LedgerRepository) {
    suspend operator fun invoke(transactionId: Long, description: String, date: LocalDate, dueDate: LocalDate?) {
        val d = Validators.date(date)
        ledger.updateDetails(transactionId, Validators.note(description), d, Validators.dueDate(d, dueDate))
    }
}
