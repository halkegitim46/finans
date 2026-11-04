package com.kisisel.finans.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Son yedeğin tarihi ve dosya bilgisi (içerik değil). */
data class BackupInfo(
    val createdAt: Long,
    val fileName: String,
    val sizeBytes: Long,
    val persons: Int,
    val transactions: Int,
    val invoices: Int
)

interface BackupInfoStore {
    val info: Flow<BackupInfo?>
    suspend fun record(info: BackupInfo)
}

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup")

@Singleton
class DataStoreBackupInfoStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupInfoStore {

    private val at = longPreferencesKey("last_at")
    private val name = stringPreferencesKey("last_name")
    private val size = longPreferencesKey("last_size")
    private val persons = intPreferencesKey("last_persons")
    private val tx = intPreferencesKey("last_transactions")
    private val inv = intPreferencesKey("last_invoices")

    override val info: Flow<BackupInfo?> = context.backupDataStore.data.map { p ->
        val createdAt = p[at] ?: return@map null
        BackupInfo(createdAt, p[name].orEmpty(), p[size] ?: 0L, p[persons] ?: 0, p[tx] ?: 0, p[inv] ?: 0)
    }

    override suspend fun record(info: BackupInfo) {
        context.backupDataStore.edit {
            it[at] = info.createdAt
            it[name] = info.fileName
            it[size] = info.sizeBytes
            it[persons] = info.persons
            it[tx] = info.transactions
            it[inv] = info.invoices
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun bindBackupInfoStore(impl: DataStoreBackupInfoStore): BackupInfoStore
}
