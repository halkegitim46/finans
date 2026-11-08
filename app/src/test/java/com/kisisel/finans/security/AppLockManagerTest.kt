package com.kisisel.finans.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockManagerTest {
    private val withPassword = SecuritySettings(passwordRecord = "v1:1:AA:BB")

    private fun manager(s: SecuritySettings = withPassword) = AppLockManager().also { it.onSettings(s) }

    @Test fun startsLoadingThenDependsOnPassword() {
        val m = AppLockManager()
        assertEquals(LockState.LOADING, m.state.value)
        m.onSettings(SecuritySettings())
        assertEquals(LockState.NEEDS_SETUP, m.state.value)

        val m2 = AppLockManager()
        m2.onSettings(withPassword)
        assertEquals(LockState.LOCKED, m2.state.value)
    }

    @Test fun laterSettingsChangesDoNotRelock() {
        val m = manager()
        m.onUnlocked()
        m.onSettings(withPassword.copy(biometricEnabled = true))
        assertEquals(LockState.UNLOCKED, m.state.value)
    }

    @Test fun setupThenUnlock() {
        val m = AppLockManager()
        m.onSettings(SecuritySettings())
        m.onUnlocked()
        assertEquals(LockState.UNLOCKED, m.state.value)
    }

    @Test fun locksAfterTimeoutInBackground() {
        val m = manager(withPassword.copy(lockTimeoutSeconds = 30))
        m.onUnlocked()
        m.onBackgrounded(1_000)
        m.onForegrounded(11_000)             // 10 sn: süre dolmadı
        assertEquals(LockState.UNLOCKED, m.state.value)

        m.onBackgrounded(20_000)
        m.onForegrounded(50_001)             // 30+ sn
        assertEquals(LockState.LOCKED, m.state.value)
    }

    @Test fun zeroTimeoutLocksImmediately() {
        val m = manager(withPassword.copy(lockTimeoutSeconds = 0))
        m.onUnlocked()
        m.onBackgrounded(5_000)
        m.onForegrounded(5_000)
        assertEquals(LockState.LOCKED, m.state.value)
    }

    @Test fun lockOnBackgroundDisabledNeverLocks() {
        val m = manager(withPassword.copy(lockOnBackground = false))
        m.onUnlocked()
        m.onBackgrounded(0)
        m.onForegrounded(10_000_000)
        assertEquals(LockState.UNLOCKED, m.state.value)
    }

    @Test fun foregroundWithoutBackgroundDoesNothing() {
        val m = manager()
        m.onUnlocked()
        m.onForegrounded(99_999)
        assertEquals(LockState.UNLOCKED, m.state.value)
    }

    @Test fun lockOnlyAffectsUnlocked() {
        val m = AppLockManager()
        m.onSettings(SecuritySettings())
        m.lock()
        assertEquals(LockState.NEEDS_SETUP, m.state.value)

        val m2 = manager()
        m2.lock()
        assertEquals(LockState.LOCKED, m2.state.value)
        m2.onUnlocked()
        m2.lock()
        assertEquals(LockState.LOCKED, m2.state.value)
    }

    @Test fun externalUiExemptionLastsUntilResultCallback() {
        val m = manager(withPassword.copy(lockTimeoutSeconds = 0))
        m.onUnlocked()
        m.allowExternalUi(1_000)
        m.onBackgrounded(2_000)          // dosya seçici açıldı
        m.onForegrounded(9_000)          // seçiciden dönüldü: kilitlenmez
        assertEquals(LockState.UNLOCKED, m.state.value)

        m.endExternalUi()                // sonuç (veya iptal) geri çağrısı
        m.onBackgrounded(10_000)         // gerçek arka plan: kilitlenir
        m.onForegrounded(10_001)
        assertEquals(LockState.LOCKED, m.state.value)
    }

    @Test fun cancelledPickerAlsoEndsExemption() {
        val m = manager(withPassword.copy(lockTimeoutSeconds = 0))
        m.onUnlocked()
        m.allowExternalUi(0)
        m.endExternalUi()                // kullanıcı seçiciyi iptal etti
        m.onBackgrounded(1_000)
        m.onForegrounded(1_001)
        assertEquals(LockState.LOCKED, m.state.value)
    }

    @Test fun externalUiExemptionExpiresAfterThreeMinutes() {
        val m = manager(withPassword.copy(lockTimeoutSeconds = 0))
        m.onUnlocked()
        m.allowExternalUi(0)
        m.onBackgrounded(1_000)
        m.onForegrounded(3 * 60 * 1000L + 1)   // süre doldu, geri çağrı hiç gelmedi
        assertEquals(LockState.LOCKED, m.state.value)
    }
}
