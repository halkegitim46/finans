package com.kisisel.finans.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAccessTest {
    private var t = 1_000L
    private val access = VaultAccess { t }

    @Test fun startsLocked() {
        assertFalse(access.isUnlocked())
    }

    @Test fun unlockOpensForTheWindowOnly() {
        access.unlock()
        assertTrue(access.isUnlocked())
        t += VaultAccess.WINDOW_MS - 1
        assertTrue(access.isUnlocked())
        t += 2
        assertFalse(access.isUnlocked())
    }

    @Test fun touchExtendsWhileOpen() {
        access.unlock()
        t += VaultAccess.WINDOW_MS - 10
        access.touch()
        t += VaultAccess.WINDOW_MS - 10
        assertTrue("etkileşim pencereyi uzatmalı", access.isUnlocked())
    }

    @Test fun touchNeverReopensAnExpiredOrLockedVault() {
        access.touch()
        assertFalse(access.isUnlocked())
        access.unlock()
        t += VaultAccess.WINDOW_MS + 1
        access.touch()
        assertFalse(access.isUnlocked())
    }

    @Test fun lockClosesImmediately() {
        access.unlock()
        access.lock()
        assertFalse(access.isUnlocked())
        assertEquals(Long.MIN_VALUE, access.unlockedUntil.value)
    }
}
