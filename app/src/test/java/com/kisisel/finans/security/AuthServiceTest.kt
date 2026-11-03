package com.kisisel.finans.security

import com.kisisel.finans.domain.FinanceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FakeSecurityStore : SecurityStore {
    private val state = MutableStateFlow(SecuritySettings())
    override val settings: Flow<SecuritySettings> = state
    override suspend fun update(transform: (SecuritySettings) -> SecuritySettings) {
        state.value = transform(state.value)
    }
    val current: SecuritySettings get() = state.value
}

class MutableClock(var nowMillis: Long = 1_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
}

class AuthServiceTest {
    private lateinit var store: FakeSecurityStore
    private lateinit var clock: MutableClock
    private lateinit var auth: AuthService

    @Before fun setUp() {
        store = FakeSecurityStore()
        clock = MutableClock()
        auth = AuthService(store, clock).also { it.iterations = 1_000 }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test fun setupStoresHashedPasswordOnly() = run {
        assertFalse(auth.isPasswordSet())
        auth.setInitialPassword("güçlü-parola-9")
        assertTrue(auth.isPasswordSet())
        assertFalse(store.current.passwordRecord!!.contains("güçlü-parola-9"))
        assertEquals(AuthResult.Success, auth.verifyPassword("güçlü-parola-9"))
    }

    @Test fun weakOrRepeatedSetupRejected() = run {
        assertThrows(FinanceException.InvalidInput::class.java) { runBlocking { auth.setInitialPassword("kısa") } }
        auth.setInitialPassword("güçlü-parola-9")
        assertThrows(FinanceException.InvalidInput::class.java) { runBlocking { auth.setInitialPassword("başka-parola-9") } }
    }

    @Test fun notConfiguredWhenNoPassword() = run {
        assertEquals(AuthResult.NotConfigured, auth.verifyPassword("x"))
        assertEquals(AuthResult.NotConfigured, auth.verifyPin("1234"))
    }

    @Test fun wrongAttemptsCountDownThenLockOut() = run {
        auth.setInitialPassword("güçlü-parola-9")
        assertEquals(AuthResult.Wrong(4), auth.verifyPassword("yanlış-1"))
        assertEquals(AuthResult.Wrong(3), auth.verifyPassword("yanlış-2"))
        assertEquals(AuthResult.Wrong(2), auth.verifyPassword("yanlış-3"))
        assertEquals(AuthResult.Wrong(1), auth.verifyPassword("yanlış-4"))
        val locked = auth.verifyPassword("yanlış-5")
        assertEquals(AuthResult.LockedOut(clock.nowMillis + 30_000), locked)
    }

    @Test fun lockedOutRejectsEvenCorrectPasswordUntilExpiry() = run {
        auth.setInitialPassword("güçlü-parola-9")
        repeat(5) { auth.verifyPassword("yanlış") }
        val until = clock.nowMillis + 30_000

        clock.nowMillis += 10_000
        assertEquals(AuthResult.LockedOut(until), auth.verifyPassword("güçlü-parola-9"))

        clock.nowMillis = until + 1
        assertEquals(AuthResult.Success, auth.verifyPassword("güçlü-parola-9"))
        assertEquals(0, store.current.failedAttempts)
        assertEquals(0L, store.current.lockoutUntilMillis)
    }

    @Test fun lockoutGrowsOnFurtherFailures() = run {
        auth.setInitialPassword("güçlü-parola-9")
        repeat(5) { auth.verifyPassword("yanlış") }
        clock.nowMillis += 31_000
        val second = auth.verifyPassword("yanlış")
        assertEquals(AuthResult.LockedOut(clock.nowMillis + 60_000), second)
    }

    @Test fun lockoutDurationsAndCap() {
        assertEquals(0L, lockoutMillisFor(0))
        assertEquals(0L, lockoutMillisFor(4))
        assertEquals(30_000L, lockoutMillisFor(5))
        assertEquals(60_000L, lockoutMillisFor(6))
        assertEquals(120_000L, lockoutMillisFor(7))
        assertEquals(15 * 60_000L, lockoutMillisFor(30))
        assertEquals(15 * 60_000L, lockoutMillisFor(10_000))
    }

    @Test fun changePassword() = run {
        auth.setInitialPassword("eski-parola-9")
        assertEquals(AuthResult.Wrong(4), auth.changePassword("yanlış-eski", "yeni-parola-9"))
        assertEquals(AuthResult.Success, auth.changePassword("eski-parola-9", "yeni-parola-9"))
        assertEquals(AuthResult.Success, auth.verifyPassword("yeni-parola-9"))
        assertTrue(auth.verifyPassword("eski-parola-9") is AuthResult.Wrong)
        assertThrows(FinanceException.InvalidInput::class.java) {
            runBlocking { auth.changePassword("yeni-parola-9", "kısa") }
        }
    }

    @Test fun pinFlow() = run {
        auth.setInitialPassword("güçlü-parola-9")
        assertTrue(auth.setPin("2468", "yanlış-parola") is AuthResult.Wrong)
        assertNull(store.current.pinRecord)

        assertEquals(AuthResult.Success, auth.setPin("2468", "güçlü-parola-9"))
        assertNotNull(store.current.pinRecord)
        assertFalse(store.current.pinRecord!!.contains("2468"))
        assertEquals(AuthResult.Success, auth.verifyPin("2468"))
        assertTrue(auth.verifyPin("1357") is AuthResult.Wrong)

        auth.removePin()
        assertEquals(AuthResult.NotConfigured, auth.verifyPin("2468"))
        assertThrows(FinanceException.InvalidInput::class.java) {
            runBlocking { auth.setPin("1111", "güçlü-parola-9") }
        }
    }

    @Test fun failureCounterIsSharedBetweenPasswordAndPin() = run {
        auth.setInitialPassword("güçlü-parola-9")
        auth.setPin("2468", "güçlü-parola-9")
        repeat(3) { auth.verifyPassword("yanlış") }
        auth.verifyPin("0000")
        val last = auth.verifyPin("0001")
        assertTrue(last is AuthResult.LockedOut)
    }

    @Test fun resetFailures() = run {
        auth.setInitialPassword("güçlü-parola-9")
        repeat(5) { auth.verifyPassword("yanlış") }
        auth.resetFailures()
        assertEquals(0, store.current.failedAttempts)
        assertEquals(AuthResult.Success, auth.verifyPassword("güçlü-parola-9"))
    }
}
