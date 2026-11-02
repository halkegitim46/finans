package com.kisisel.finans.data.di

import android.content.Context
import androidx.room.Room
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.Migrations
import com.kisisel.finans.data.local.dao.InvoiceDao
import com.kisisel.finans.data.local.dao.PaymentDao
import com.kisisel.finans.data.local.dao.PersonDao
import com.kisisel.finans.data.local.dao.SettingsDao
import com.kisisel.finans.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Şifreleme (SQLCipher + Keystore) 8. aşamada bu fabrikaya eklenecek. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KisiselFinansDatabase =
        Room.databaseBuilder(context, KisiselFinansDatabase::class.java, KisiselFinansDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun providePersonDao(db: KisiselFinansDatabase): PersonDao = db.personDao()
    @Provides fun provideTransactionDao(db: KisiselFinansDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideInvoiceDao(db: KisiselFinansDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun providePaymentDao(db: KisiselFinansDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: KisiselFinansDatabase): SettingsDao = db.settingsDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
