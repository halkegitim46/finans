package com.kisisel.finans.backup

import com.kisisel.finans.domain.FinanceException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDateTime

class BackupIoTest {
    @Test fun readLimitedReadsWithinLimit() {
        val data = ByteArray(20_000) { (it % 251).toByte() }
        assertArrayEquals(data, readLimited(ByteArrayInputStream(data), 20_000))
        assertEquals(0, readLimited(ByteArrayInputStream(ByteArray(0)), 10).size)
    }

    @Test fun readLimitedRejectsOversize() {
        assertThrows(FinanceException.BackupInvalid::class.java) {
            readLimited(ByteArrayInputStream(ByteArray(101)), 100)
        }
    }

    @Test fun fileNameFormat() {
        assertEquals(
            "KisiselFinansTakip_yedek_2026-11-04_1530.kftbak",
            backupFileName(LocalDateTime.of(2026, 11, 4, 15, 30))
        )
        assertEquals(
            "KisiselFinansTakip_yedek_2026-01-05_0907.kftbak",
            backupFileName(LocalDateTime.of(2026, 1, 5, 9, 7))
        )
    }
}
