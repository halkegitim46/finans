package com.kisisel.finans.security

import com.kisisel.finans.domain.FinanceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data object Success : AuthResult

    /** Hatalı giriş; attemptsLeft = geçici kilitlenmeye kalan hak. */
    data class Wrong(val attemptsLeft: Int) : AuthResult

    data class LockedOut(val untilMillis: Long) : AuthResult
    data object NotConfigured : AuthResult
}

const val MAX_FREE_ATTEMPTS = 5

/** 5. hatalı denemeden itibaren 30 sn, sonra her hatada ikiye katlanır (en çok 15 dk). */
fun lockoutMillisFor(failedAttempts: Int): Long {
    if (failedAttempts < MAX_FREE_ATTEMPTS) return 0
    val step = (failedAttempts - MAX_FREE_ATTEMPTS).coerceAtMost(10)
    return minOf(30_000L shl step, 15 * 60_000L)
}

/**
 * Parola ve PIN doğrulaması. Hatalı deneme sayacı parola ve PIN için ortaktır
 * (ikisi arasında geçiş yaparak deneme hakkı artırılamaz) ve kalıcıdır.
 */
@Singleton
class AuthService @Inject constructor(
    private val store: SecurityStore,
    private val clock: Clock
) {
    internal var iterations: Int = PasswordHasher.DEFAULT_ITERATIONS

    suspend fun isPasswordSet(): Boolean = store.load().passwordRecord != null

    suspend fun setInitialPassword(password: String) {
        PasswordPolicy.validatePassword(password)?.let { throw FinanceException.InvalidInput(it) }
        if (isPasswordSet()) throw FinanceException.InvalidInput("Parola zaten belirlenmiş.")
        val record = hash(password)
        store.update { it.copy(passwordRecord = record, failedAttempts = 0, lockoutUntilMillis = 0) }
    }

    suspend fun verifyPassword(password: String): AuthResult = check(password) { it.passwordRecord }

    suspend fun verifyPin(pin: String): AuthResult = check(pin) { it.pinRecord }

    suspend fun changePassword(old: String, new: String): AuthResult {
        PasswordPolicy.validatePassword(new)?.let { throw FinanceException.InvalidInput(it) }
        val result = verifyPassword(old)
        if (result != AuthResult.Success) return result
        val record = hash(new)
        store.update { it.copy(passwordRecord = record) }
        return AuthResult.Success
    }

    /** PIN belirlemek için mevcut parola tekrar doğrulanır. */
    suspend fun setPin(pin: String, currentPassword: String): AuthResult {
        PasswordPolicy.validatePin(pin)?.let { throw FinanceException.InvalidInput(it) }
        val result = verifyPassword(currentPassword)
        if (result != AuthResult.Success) return result
        val record = hash(pin)
        store.update { it.copy(pinRecord = record) }
        return AuthResult.Success
    }

    suspend fun removePin() = store.update { it.copy(pinRecord = null) }

    /** Biyometrik doğrulama başarılı olduğunda sayaç sıfırlanır. */
    suspend fun resetFailures() = store.update { it.copy(failedAttempts = 0, lockoutUntilMillis = 0) }

    private suspend fun hash(secret: String): String =
        withContext(Dispatchers.Default) { PasswordHasher.encode(secret.toCharArray(), iterations) }

    private suspend fun check(secret: String, pick: (SecuritySettings) -> String?): AuthResult {
        val settings = store.load()
        val now = clock.millis()
        if (settings.lockoutUntilMillis > now) return AuthResult.LockedOut(settings.lockoutUntilMillis)
        val record = pick(settings) ?: return AuthResult.NotConfigured

        val ok = withContext(Dispatchers.Default) { PasswordHasher.verify(secret.toCharArray(), record) }
        if (ok) {
            store.update { it.copy(failedAttempts = 0, lockoutUntilMillis = 0) }
            return AuthResult.Success
        }

        val failed = settings.failedAttempts + 1
        val lockMs = lockoutMillisFor(failed)
        val until = if (lockMs > 0) now + lockMs else 0L
        store.update { it.copy(failedAttempts = failed, lockoutUntilMillis = until) }
        return if (until > 0) AuthResult.LockedOut(until) else AuthResult.Wrong(MAX_FREE_ATTEMPTS - failed)
    }
}
