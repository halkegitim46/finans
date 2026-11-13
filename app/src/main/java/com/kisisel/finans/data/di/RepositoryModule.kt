package com.kisisel.finans.data.di

import com.kisisel.finans.data.repository.AgendaRepositoryImpl
import com.kisisel.finans.data.repository.InvoiceRepositoryImpl
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.repository.AgendaRepository
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository
    @Binds abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository
    @Binds abstract fun bindInvoiceRepository(impl: InvoiceRepositoryImpl): InvoiceRepository
    @Binds abstract fun bindAgendaRepository(impl: AgendaRepositoryImpl): AgendaRepository
}
