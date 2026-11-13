package com.kisisel.finans.data

import com.kisisel.finans.data.local.KisiselFinansDatabase
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Room şema dosyaları (app/schemas) depoya işlenmiş olmalı. Dizin yoksa (ilk derlemeden önce) test atlanır;
 * "Proje dosyalarını tamamla" workflow'u şemaları depoya ekler.
 * Güncel sürümün dosyası derleme sırasında üretilir (CI'da henüz commit edilmemiş olabilir), bu yüzden
 * zorunlu tutulmaz; ama mevcut dosyalar arasında BOŞLUK olmamalı (migration testleri eski şemaya ihtiyaç duyar).
 */
class SchemaGuardTest {
    private val dir = File("schemas/com.kisisel.finans.data.local.KisiselFinansDatabase")

    private fun versions(): List<Int> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            ?.sorted()
            .orEmpty()

    @Test fun exportedSchemasHaveNoGaps() {
        val found = versions()
        assumeTrue("Şema dizini yok; 'Proje dosyalarını tamamla' workflow'unu çalıştırın.", found.isNotEmpty())
        val expected = (found.first()..found.last()).toList()
        assertTrue("schemas/ içinde eksik sürüm var: bulunan=$found", found == expected)
        assertTrue("Şemalar güncel sürümden yeni olamaz", found.last() <= KisiselFinansDatabase.VERSION)
    }

    @Test fun latestExportedSchemaListsAllTables() {
        val found = versions()
        assumeTrue("Şema dosyası yok", found.isNotEmpty())
        val json = File(dir, "${found.last()}.json").readText()
        val required = mutableListOf("persons", "transactions", "invoice_templates", "invoice_occurrences", "payments", "app_settings")
        if (found.last() >= 2) required += "agenda_items"
        for (table in required) {
            assertTrue("$table tablosu şemada yok", Regex("\"tableName\"\\s*:\\s*\"$table\"").containsMatchIn(json))
        }
    }
}
