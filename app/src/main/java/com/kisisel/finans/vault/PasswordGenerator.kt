package com.kisisel.finans.vault

import java.security.SecureRandom

/**
 * Güvenli rastgele şifre üreticisi (SecureRandom). Okunurluk için karışan karakterler (l, I, O, 0, 1) hariçtir.
 * Seçilen her sınıftan en az bir karakter garanti edilir; sınır dışı sapma olmaması için nextInt(bound) kullanılır.
 */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val DIGITS = "23456789"
    const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"

    data class Options(
        val length: Int = 16,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true
    ) {
        val pools: List<String>
            get() = listOfNotNull(
                LOWER.takeIf { lower }, UPPER.takeIf { upper }, DIGITS.takeIf { digits }, SYMBOLS.takeIf { symbols }
            )
    }

    fun generate(options: Options, random: SecureRandom = SecureRandom()): String {
        val pools = options.pools
        require(pools.isNotEmpty()) { "En az bir karakter sınıfı seçilmeli" }
        require(options.length in MIN_LENGTH..MAX_LENGTH) { "Uzunluk $MIN_LENGTH-$MAX_LENGTH olmalı" }

        val all = pools.joinToString("")
        val chars = ArrayList<Char>(options.length)
        pools.forEach { chars += it[random.nextInt(it.length)] }        // her sınıftan en az bir
        while (chars.size < options.length) chars += all[random.nextInt(all.length)]
        for (i in chars.indices.reversed()) {                           // Fisher-Yates karıştırma
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.joinToString("")
    }
}
