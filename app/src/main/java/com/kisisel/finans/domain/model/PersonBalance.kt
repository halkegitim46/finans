package com.kisisel.finans.domain.model

data class PersonBalance(
    val totalReceivable: Long,
    val totalPayable: Long,
    val collected: Long,
    val paid: Long,
    val remainingReceivable: Long,
    val remainingPayable: Long
) {
    /** Pozitif: kişi bana borçlu. Negatif: ben kişiye borçluyum. */
    val net: Long get() = remainingReceivable - remainingPayable
}
