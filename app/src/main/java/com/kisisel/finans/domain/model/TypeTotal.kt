package com.kisisel.finans.domain.model

/** İşlem türüne göre toplam (kuruş). DAO toplama sorgularının sonucu. */
data class TypeTotal(val type: TransactionType, val total: Long)
