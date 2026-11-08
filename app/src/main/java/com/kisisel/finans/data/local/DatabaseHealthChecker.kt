package com.kisisel.finans.data.local

import com.kisisel.finans.security.DatabaseKeyStore
import com.kisisel.finans.security.DatabaseKeyUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

sealed interface DbHealth {
    data object Ok : DbHealth
    data class Problem(val kind: Kind, val detail: String) : DbHealth

    enum class Kind { KEY_MISSING, KEY_UNREADABLE, KEY_WRITE_FAILED, OPEN_FAILED }
}

/**
 * Uygulama içeriği açılmadan önce veritabanının kullanılabilir olduğunu doğrular. Çökme yerine kurtarma ekranı
 * gösterilebilsin diye hatalar yakalanıp sınıflandırılır. İlk açılış (ve eski şifresiz veriden taşıma) burada,
 * ana iş parçacığının dışında yapılır. Hata ayrıntısına kişisel veri konmaz.
 */
@Singleton
class DatabaseHealthChecker @Inject constructor(
    private val keyStore: DatabaseKeyStore,
    private val database: Provider<KisiselFinansDatabase>
) {
    suspend fun check(): DbHealth = withContext(Dispatchers.IO) {
        try {
            keyStore.getOrCreateKeyHex()
            database.get().openHelper.writableDatabase   // yanlış anahtar / bozuk dosya burada ortaya çıkar
            DbHealth.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: DatabaseKeyUnavailableException) {
            DbHealth.Problem(
                when (e.reason) {
                    DatabaseKeyUnavailableException.Reason.MISSING -> DbHealth.Kind.KEY_MISSING
                    DatabaseKeyUnavailableException.Reason.UNREADABLE -> DbHealth.Kind.KEY_UNREADABLE
                    DatabaseKeyUnavailableException.Reason.WRITE_FAILED -> DbHealth.Kind.KEY_WRITE_FAILED
                },
                e.reason.message
            )
        } catch (e: Throwable) {
            DbHealth.Problem(DbHealth.Kind.OPEN_FAILED, "Veritabanı açılamadı.")
        }
    }
}
