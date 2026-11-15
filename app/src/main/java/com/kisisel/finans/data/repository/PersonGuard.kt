package com.kisisel.finans.data.repository

import com.kisisel.finans.data.local.dao.PersonDao
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.domain.FinanceException

/** Yeni kayıt bağlanacak kişi var ve arşivli değil olmalı (borç/alacak, fatura, ajanda için ortak kural). */
internal suspend fun PersonDao.requireActive(id: Long): PersonEntity {
    val person = getById(id) ?: throw FinanceException.PersonNotFound()
    if (person.isArchived) throw FinanceException.PersonArchived()
    return person
}
