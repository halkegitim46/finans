package com.kisisel.finans.data.repository

import androidx.room.withTransaction
import com.kisisel.finans.core.toDb
import com.kisisel.finans.core.toLocalDate
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.dao.ClaimWithAllocated
import com.kisisel.finans.data.local.entity.PaymentEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity
import com.kisisel.finans.data.mapper.toDomain
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.LedgerCalculator
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.SettlementResult
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.model.TypeTotal
import com.kisisel.finans.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val db: KisiselFinansDatabase,
    private val clock: Clock
) : LedgerRepository {

    private val persons = db.personDao()
    private val transactions = db.transactionDao()
    private val payments = db.paymentDao()

    override suspend fun recordClaim(cmd: NewClaim): Long = db.withTransaction {
        require(cmd.type.createsReceivable || cmd.type.createsPayable) { "Alacak/borç türü bekleniyordu" }
        transactions.getByIdempotencyKey(cmd.idempotencyKey)?.let { return@withTransaction it.id }
        requireActivePerson(cmd.personId)
        val now = clock.millis()
        transactions.insert(
            TransactionEntity(
                personId = cmd.personId,
                type = cmd.type,
                amountKurus = cmd.amountKurus,
                transactionDate = cmd.date.toDb(),
                dueDate = cmd.dueDate?.toDb(),
                description = cmd.description,
                idempotencyKey = cmd.idempotencyKey,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun settle(cmd: NewSettlement): SettlementResult = db.withTransaction {
        require(cmd.type.isSettlement) { "Tahsilat/ödeme türü bekleniyordu" }

        // Tekrarlanan istek: mevcut sonucu dön, ikinci kez kaydetme.
        transactions.getByIdempotencyKey(cmd.idempotencyKey)?.let { existing ->
            return@withTransaction SettlementResult(
                transactionId = existing.id,
                amountKurus = existing.amountKurus,
                allocatedKurus = payments.sumAllocatedForSettlement(existing.id)
            )
        }

        val person = requireActivePerson(cmd.personId)
        val isCollection = cmd.type == TransactionType.COLLECTION
        val balance = LedgerCalculator.balance(person.openingBalanceKurus, transactions.getTotalsByType(person.id))
        val remaining = if (isCollection) balance.remainingReceivable else balance.remainingPayable
        if (remaining <= 0) throw FinanceException.NothingToSettle()
        val amount = cmd.amountKurus ?: remaining
        if (amount > remaining) throw FinanceException.ExceedsRemaining(remaining)

        val now = clock.millis()
        val txId = transactions.insert(
            TransactionEntity(
                personId = person.id,
                type = cmd.type,
                amountKurus = amount,
                transactionDate = cmd.date.toDb(),
                description = cmd.description,
                idempotencyKey = cmd.idempotencyKey,
                createdAt = now,
                updatedAt = now
            )
        )

        // Açık kayıtlara eskiden yeniye (FIFO) dağıt.
        val claims: List<ClaimWithAllocated> =
            if (isCollection) transactions.getReceivableClaims(person.id) else transactions.getPayableClaims(person.id)
        var left = amount
        for (claim in claims) {
            if (left <= 0) break
            val open = claim.openKurus
            if (open <= 0) continue
            val take = min(open, left)
            payments.insert(
                PaymentEntity(settlementTxId = txId, claimTxId = claim.tx.id, amountKurus = take, createdAt = now)
            )
            left -= take
        }
        SettlementResult(transactionId = txId, amountKurus = amount, allocatedKurus = amount - left)
    }

    override suspend fun cancel(transactionId: Long) {
        db.withTransaction {
            val tx = transactions.getById(transactionId) ?: throw FinanceException.TransactionNotFound()
            if (tx.isCancelled) throw FinanceException.AlreadyCancelled()
            if (tx.occurrenceId != null) throw FinanceException.LinkedToInvoice()
            if (!tx.type.isSettlement && payments.sumAllocatedForClaim(tx.id) > 0) {
                throw FinanceException.HasActiveAllocations()
            }
            transactions.cancel(tx.id, clock.millis())

            // İptal sonrası tahsilat/ödeme toplamı alacak/borç toplamını aşmamalı (aksi halde geri alınır).
            val person = persons.getById(tx.personId) ?: throw FinanceException.PersonNotFound()
            val b = LedgerCalculator.balance(person.openingBalanceKurus, transactions.getTotalsByType(person.id))
            if (b.collected > b.totalReceivable || b.paid > b.totalPayable) {
                throw FinanceException.WouldOverSettle()
            }
            Unit
        }
    }

    override suspend fun updateDetails(
        transactionId: Long,
        description: String,
        date: LocalDate,
        dueDate: LocalDate?
    ) {
        val rows = transactions.updateDetails(
            id = transactionId,
            description = description,
            date = date.toDb(),
            dueDate = dueDate?.toDb(),
            now = clock.millis()
        )
        if (rows == 0) {
            val tx = transactions.getById(transactionId) ?: throw FinanceException.TransactionNotFound()
            if (tx.isCancelled) throw FinanceException.AlreadyCancelled()
        }
    }

    override fun observePersonSummary(personId: Long): Flow<PersonSummary?> =
        combine(persons.observeById(personId), transactions.observeTotalsByType(personId)) { person, totals ->
            person?.let { PersonSummary(it.toDomain(), LedgerCalculator.balance(it.openingBalanceKurus, totals)) }
        }

    override fun observePersonSummaries(): Flow<List<PersonSummary>> =
        combine(persons.observeActive(), transactions.observeTotalsByPerson()) { list, totals ->
            val byPerson = totals.groupBy { it.personId }
            list.map { p ->
                val t = byPerson[p.id].orEmpty().map { TypeTotal(it.type, it.total) }
                PersonSummary(p.toDomain(), LedgerCalculator.balance(p.openingBalanceKurus, t))
            }
        }

    override fun observeEntries(personId: Long): Flow<List<LedgerEntry>> =
        transactions.observeByPerson(personId).map { l -> l.map { it.toDomain() } }

    override fun observeRecentEntries(limit: Int): Flow<List<LedgerEntry>> =
        transactions.observeRecent(limit).map { l -> l.map { it.toDomain() } }

    override fun observeDashboard(today: LocalDate): Flow<DashboardTotals> {
        val from = today.withDayOfMonth(1).toDb()
        val to = today.withDayOfMonth(today.lengthOfMonth()).toDb()
        return combine(
            transactions.observeTotalsByType(),
            transactions.observeOpeningReceivables(),
            transactions.observeOpeningPayables(),
            transactions.observeTotalsByTypeBetween(from, to)
        ) { all, openingReceivable, openingPayable, month ->
            val b = LedgerCalculator.balance(openingReceivable, openingPayable, all)
            fun monthSum(t: TransactionType) = month.filter { it.type == t }.sumOf { it.total }
            DashboardTotals(
                remainingReceivable = b.remainingReceivable,
                remainingPayable = b.remainingPayable,
                net = b.net,
                monthPaid = monthSum(TransactionType.PAID_ON_BEHALF) + monthSum(TransactionType.DEBT_PAYMENT),
                monthCollected = monthSum(TransactionType.COLLECTION)
            )
        }
    }

    override fun observeAllLines(): Flow<List<LedgerLine>> =
        combine(transactions.observeAll(), persons.observeAll()) { txs, people -> toLines(txs, people) }

    override fun observeRecentLines(limit: Int): Flow<List<LedgerLine>> =
        combine(transactions.observeRecent(limit), persons.observeAll()) { txs, people -> toLines(txs, people) }

    override fun observeClaims(): Flow<List<ClaimLine>> =
        combine(transactions.observeAllClaims(), persons.observeAll()) { claims, people ->
            val names = people.associate { it.id to it.fullName }
            claims.map { c ->
                ClaimLine(
                    entryId = c.tx.id,
                    personId = c.tx.personId,
                    personName = names[c.tx.personId].orEmpty(),
                    type = c.tx.type,
                    amountKurus = c.tx.amountKurus,
                    allocatedKurus = c.allocatedKurus,
                    date = c.tx.transactionDate.toLocalDate(),
                    dueDate = c.tx.dueDate?.toLocalDate(),
                    occurrenceId = c.tx.occurrenceId,
                    description = c.tx.description
                )
            }
        }

    private fun toLines(txs: List<TransactionEntity>, people: List<PersonEntity>): List<LedgerLine> {
        val names = people.associate { it.id to it.fullName }
        return txs.map { LedgerLine(it.toDomain(), names[it.personId].orEmpty()) }
    }

    private suspend fun requireActivePerson(id: Long): PersonEntity {
        val p = persons.getById(id) ?: throw FinanceException.PersonNotFound()
        if (p.isArchived) throw FinanceException.PersonArchived()
        return p
    }
}
