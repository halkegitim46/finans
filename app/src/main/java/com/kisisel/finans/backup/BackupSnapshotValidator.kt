package com.kisisel.finans.backup

import com.kisisel.finans.core.Money
import com.kisisel.finans.data.local.DatabaseSnapshot
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import java.time.LocalDate

/**
 * Yedek içeriğinin uygulamanın finansal kurallarıyla uyumunu denetler (şifreleme/bütünlük kontrolünden bağımsız).
 * Amaç: elle düzenlenmiş, bozuk üretilmiş veya başka bir sürümden gelen tutarsız veriyi geri yükleme ÖNCESİNDE
 * yakalamak. Uygulamanın kendi ürettiği her veri bu kurallardan geçmelidir (testlerle doğrulanır).
 * Mesajlar kişisel veri içermez (yalnızca kayıt numaraları).
 */
object BackupSnapshotValidator {
    private val MIN_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
    private val MAX_DAY = LocalDate.of(2100, 12, 31).toEpochDay()
    private const val MAX = Money.MAX_KURUS
    private const val MAX_PROBLEMS = 50

    fun validate(s: DatabaseSnapshot): List<String> {
        val problems = ArrayList<String>()
        fun bad(message: String) {
            if (problems.size < MAX_PROBLEMS) problems += message
        }
        fun badAmount(v: Long) = v < 1 || v > MAX
        fun badDay(v: Long) = v < MIN_DAY || v > MAX_DAY

        // ---- kişiler ----
        val personIds = HashSet<Long>()
        for (p in s.persons) {
            if (!personIds.add(p.id)) bad("Kişi #${p.id} tekrar ediyor")
            if (p.fullName.isBlank()) bad("Kişi #${p.id} adı boş")
            if (p.openingBalanceKurus < -MAX || p.openingBalanceKurus > MAX) bad("Kişi #${p.id} başlangıç bakiyesi geçersiz")
        }

        // ---- fatura şablonları ----
        val templateIds = HashSet<Long>()
        for (t in s.invoiceTemplates) {
            if (!templateIds.add(t.id)) bad("Fatura #${t.id} tekrar ediyor")
            if (t.name.isBlank()) bad("Fatura #${t.id} adı boş")
            if (t.personId != null && t.personId !in personIds) bad("Fatura #${t.id} olmayan kişiye bağlı")
            if (t.amountKurus != null && badAmount(t.amountKurus)) bad("Fatura #${t.id} tutarı geçersiz")
            if (t.recurrence == Recurrence.CUSTOM_DAYS && (t.customIntervalDays == null || t.customIntervalDays !in 1..365)) {
                bad("Fatura #${t.id} özel tekrar aralığı geçersiz")
            }
            if (badDay(t.firstDueDate)) bad("Fatura #${t.id} ilk vade tarihi geçersiz")
            if (t.overdueReminderEveryDays !in 0..30) bad("Fatura #${t.id} gecikme hatırlatma aralığı geçersiz")
        }

        // ---- fatura dönemleri ----
        val occurrenceIds = HashSet<Long>()
        val periodKeys = HashSet<Pair<Long, String>>()
        for (o in s.invoiceOccurrences) {
            if (!occurrenceIds.add(o.id)) bad("Fatura dönemi #${o.id} tekrar ediyor")
            if (o.templateId !in templateIds) bad("Fatura dönemi #${o.id} olmayan faturaya bağlı")
            if (!periodKeys.add(o.templateId to o.periodKey)) bad("Fatura dönemi #${o.id} aynı dönem için tekrar ediyor")
            if (o.amountKurus != null && badAmount(o.amountKurus)) bad("Fatura dönemi #${o.id} tutarı geçersiz")
            if (badDay(o.dueDate)) bad("Fatura dönemi #${o.id} vade tarihi geçersiz")
            if (o.status == OccurrenceStatus.PAID) {
                if (o.paidAt == null) bad("Ödenmiş fatura dönemi #${o.id} ödeme zamanı yok")
                if (o.amountKurus == null) bad("Ödenmiş fatura dönemi #${o.id} tutarsız")
            } else if (o.paidAt != null) {
                bad("Ödenmemiş fatura dönemi #${o.id} ödeme zamanı içeriyor")
            }
        }

        // ---- işlemler ----
        val txById = HashMap<Long, com.kisisel.finans.data.local.entity.TransactionEntity>()
        val keys = HashSet<String>()
        for (t in s.transactions) {
            if (txById.put(t.id, t) != null) bad("İşlem #${t.id} tekrar ediyor")
            if (t.idempotencyKey.isBlank()) bad("İşlem #${t.id} anahtarı boş")
            else if (!keys.add(t.idempotencyKey)) bad("İşlem #${t.id} anahtarı başka bir işlemle aynı")
            if (t.personId !in personIds) bad("İşlem #${t.id} olmayan kişiye bağlı")
            if (badAmount(t.amountKurus)) bad("İşlem #${t.id} tutarı geçersiz")
            if (badDay(t.transactionDate)) bad("İşlem #${t.id} tarihi geçersiz")
            if (t.dueDate != null && badDay(t.dueDate)) bad("İşlem #${t.id} vade tarihi geçersiz")
            if (t.occurrenceId != null) {
                if (t.occurrenceId !in occurrenceIds) bad("İşlem #${t.id} olmayan fatura dönemine bağlı")
                if (t.type != TransactionType.PAID_ON_BEHALF) bad("İşlem #${t.id} faturaya bağlı ama türü uygun değil")
            }
            if (t.isCancelled != (t.cancelledAt != null)) bad("İşlem #${t.id} iptal bilgisi tutarsız")
        }

        // ---- ödeme/tahsilat dağıtımları ----
        val paymentIds = HashSet<Long>()
        val pairs = HashSet<Pair<Long, Long>>()
        val perClaimActive = HashMap<Long, Long>()
        val perSettlement = HashMap<Long, Long>()
        for (p in s.payments) {
            if (!paymentIds.add(p.id)) bad("Dağıtım #${p.id} tekrar ediyor")
            if (!pairs.add(p.settlementTxId to p.claimTxId)) bad("Dağıtım #${p.id} aynı işlem çifti için tekrar ediyor")
            if (badAmount(p.amountKurus)) bad("Dağıtım #${p.id} tutarı geçersiz")
            val settlement = txById[p.settlementTxId]
            val claim = txById[p.claimTxId]
            if (settlement == null || claim == null) {
                bad("Dağıtım #${p.id} olmayan işleme bağlı")
                continue
            }
            if (!settlement.type.isSettlement) bad("Dağıtım #${p.id} tahsilat/ödeme olmayan işleme bağlı")
            if (!(claim.type.createsReceivable || claim.type.createsPayable)) bad("Dağıtım #${p.id} alacak/borç olmayan işleme dağıtılmış")
            if (settlement.personId != claim.personId) bad("Dağıtım #${p.id} farklı kişilerin işlemlerini eşliyor")
            val directionOk = (settlement.type == TransactionType.COLLECTION && claim.type.createsReceivable) ||
                (settlement.type == TransactionType.DEBT_PAYMENT && claim.type.createsPayable)
            if (!directionOk) bad("Dağıtım #${p.id} yönü uyumsuz (tahsilat↔alacak, ödeme↔borç)")
            if (claim.isCancelled && !settlement.isCancelled) bad("Dağıtım #${p.id} iptal edilmiş kayda etkin tahsilat/ödeme dağıtıyor")
            perSettlement.merge(settlement.id, p.amountKurus, Long::plus)
            if (!settlement.isCancelled) perClaimActive.merge(claim.id, p.amountKurus, Long::plus)
        }
        for ((claimId, total) in perClaimActive) {
            val claim = txById[claimId] ?: continue
            if (total > claim.amountKurus) bad("İşlem #$claimId için dağıtılan tutar işlem tutarını aşıyor")
        }
        for ((settlementId, total) in perSettlement) {
            val settlement = txById[settlementId] ?: continue
            if (total > settlement.amountKurus) bad("Tahsilat/ödeme #$settlementId dağıtımları tutarını aşıyor")
        }

        // ---- kişi bazlı bakiye değişmezi: tahsilat ≤ alacak, ödeme ≤ borç (etkin işlemler) ----
        val active = s.transactions.filter { !it.isCancelled }.groupBy { it.personId }
        for (person in s.persons) {
            val tx = active[person.id].orEmpty()
            fun sum(vararg types: TransactionType) = tx.filter { it.type in types }.sumOf { it.amountKurus }
            val receivable = maxOf(person.openingBalanceKurus, 0L) + sum(TransactionType.LEND, TransactionType.PAID_ON_BEHALF)
            val payable = maxOf(-person.openingBalanceKurus, 0L) + sum(TransactionType.BORROW)
            if (sum(TransactionType.COLLECTION) > receivable) bad("Kişi #${person.id} tahsilatları alacağından fazla")
            if (sum(TransactionType.DEBT_PAYMENT) > payable) bad("Kişi #${person.id} ödemeleri borcundan fazla")
        }

        // ---- ayarlar ----
        val settingKeys = HashSet<String>()
        for (a in s.settings) {
            if (a.settingKey.isBlank()) bad("Boş ayar anahtarı")
            else if (!settingKeys.add(a.settingKey)) bad("Ayar anahtarı tekrar ediyor")
        }
        return problems
    }

    /** Sorun varsa kullanıcıya gösterilebilir [FinanceException.BackupInvalid] fırlatır. */
    fun requireValid(s: DatabaseSnapshot) {
        val problems = validate(s)
        if (problems.isNotEmpty()) {
            val more = if (problems.size > 1) " (+${problems.size - 1} sorun daha)" else ""
            throw FinanceException.BackupInvalid("Yedek içeriği finansal kurallarla uyumsuz: ${problems.first()}$more")
        }
    }
}
