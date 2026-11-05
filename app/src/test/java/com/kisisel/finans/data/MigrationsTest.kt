package com.kisisel.finans.data

import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Şema sürümü yükseltildiğinde (VERSION artınca) eksik migration unutulmasın diye koruma:
 * 1'den güncel sürüme kesintisiz bir migration zinciri bulunmalı. Yıkıcı migration kullanılmaz.
 */
class MigrationsTest {
    @Test fun thereIsAnUnbrokenMigrationChainToCurrentVersion() {
        var version = 1
        var steps = 0
        while (version < KisiselFinansDatabase.VERSION) {
            val next = Migrations.ALL.firstOrNull { it.startVersion == version }
            assertTrue("Sürüm $version için migration yok", next != null)
            assertTrue("Migration geriye gidemez", next!!.endVersion > version)
            version = next.endVersion
            steps++
            assertTrue("Migration zinciri döngüde", steps < 100)
        }
        assertEquals(KisiselFinansDatabase.VERSION, version.coerceAtLeast(KisiselFinansDatabase.VERSION))
    }

    @Test fun noDuplicateMigrationsForSameStartVersion() {
        val starts = Migrations.ALL.map { it.startVersion }
        assertEquals(starts.size, starts.toSet().size)
    }

    @Test fun versionIsPositive() {
        assertTrue(KisiselFinansDatabase.VERSION >= 1)
    }
}
