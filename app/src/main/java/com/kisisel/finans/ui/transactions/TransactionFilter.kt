package com.kisisel.finans.ui.transactions

import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.containsSearch

/** İşlem listesi filtresi: kişi adı/açıklama araması, tür, iptal edilenleri gösterme. */
fun filterLines(
    lines: List<LedgerLine>,
    query: String,
    type: TransactionType?,
    showCancelled: Boolean
): List<LedgerLine> = lines.filter { l ->
    (showCancelled || !l.entry.isCancelled) &&
        (type == null || l.entry.type == type) &&
        (l.personName.containsSearch(query) || l.entry.description.containsSearch(query))
}

/** Tek tür seçiliyse iptal edilmemiş işlemlerin toplamı; karışık türlerde toplam anlamsızdır (null). */
fun totalForType(lines: List<LedgerLine>, type: TransactionType?): Long? =
    if (type == null) null else lines.filter { !it.entry.isCancelled }.sumOf { it.entry.amountKurus }
