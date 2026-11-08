package com.kisisel.finans.data

import com.kisisel.finans.data.local.KisiselFinansDatabase
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Room şema dosyaları (app/schemas) depoya işlenmiş olmalı: şema sürümü artınca eksik dosya veya
 * unutulan migration fark edilsin. Dizin henüz yoksa (ilk derlemeden önce) test atlanır;
 * "Proje dosyalarını tamamla" workflow'u şemaları depoya ekler.
 */
class SchemaGuardTest {
    private val dir = File("schemas/com.kisisel.finans.data.local.KisiselFinansDatabase")

    @Test fun exportedSchemaExistsForEveryVersion() {
        assumeTrue("Şema dizini yok; 'Proje dosyalarını tamamla' workflow'unu çalıştırın.", dir.isDirectory)
        for (v in 1..KisiselFinansDatabase.VERSION) {
            assertTrue("schemas/.../$v.json eksik: sürüm artırıldıysa yeni şemayı ve migration'ı ekleyin", File(dir, "$v.json").isFile)
        }
    }

    @Test fun currentSchemaListsAllTables() {
        val file = File(dir, "${KisiselFinansDatabase.VERSION}.json")
        assumeTrue("Güncel şema dosyası yok", file.isFile)
        val json = file.readText()
        for (table in listOf("persons", "transactions", "invoice_templates", "invoice_occurrences", "payments", "app_settings")) {
            assertTrue("$table tablosu şemada yok", Regex("\"tableName\"\\s*:\\s*\"$table\"").containsMatchIn(json))
        }
    }
}
