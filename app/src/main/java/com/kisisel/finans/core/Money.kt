package com.kisisel.finans.core

import java.math.BigDecimal

/**
 * Tüm para tutarları kuruş cinsinden Long olarak tutulur. Float/Double kullanılmaz.
 */
object Money {
    /** 10 milyar TL üstü tutarlar reddedilir (yanlış giriş koruması). */
    const val MAX_KURUS: Long = 1_000_000_000_000L

    private val THOUSANDS = Regex("""\d{1,3}(\.\d{3})+""")
    private val DECIMAL_DOT = Regex("""\d+\.\d{1,2}""")
    private val INT_PART = Regex("""\d{1,3}(\.\d{3})+|\d+""")

    /**
     * Türkçe girişi kuruşa çevirir. "1.500,50", "1500", "12,5", "₺2.000 TL" kabul edilir.
     * Negatif, hatalı biçimli, 2'den fazla ondalıklı veya MAX_KURUS üstü giriş için null döner.
     * Not: "1.500" binlik ayraç, "1.5" ondalık olarak yorumlanır.
     */
    fun parse(input: String): Long? {
        val s = input.trim()
            .replace("₺", "")
            .replace("TL", "", ignoreCase = true)
            .replace("\u00A0", "")
            .replace(" ", "")
        if (s.isEmpty()) return null

        val normalized: String = when {
            s.contains(',') -> {
                val parts = s.split(',')
                if (parts.size != 2) return null
                val intPart = parts[0]
                val frac = parts[1]
                if (!INT_PART.matches(intPart)) return null
                if (frac.length !in 1..2 || !frac.all { it.isDigit() }) return null
                intPart.replace(".", "") + "." + frac
            }
            s.contains('.') -> when {
                THOUSANDS.matches(s) -> s.replace(".", "")
                DECIMAL_DOT.matches(s) -> s
                else -> return null
            }
            else -> if (s.all { it.isDigit() }) s else return null
        }

        val kurus = try {
            BigDecimal(normalized).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            return null
        } catch (e: NumberFormatException) {
            return null
        }
        return if (kurus in 0..MAX_KURUS) kurus else null
    }

    /** Sıfırdan büyük tutar ister; değilse null. */
    fun parsePositive(input: String): Long? = parse(input)?.takeIf { it > 0 }

    /** 150050 -> "₺1.500,50"; negatif: "-₺1.234,56". Yerel ayardan bağımsız, deterministik. */
    fun format(kurus: Long, withSymbol: Boolean = true): String {
        val negative = kurus < 0
        val abs = if (kurus == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(kurus)
        val lira = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val kr = (abs % 100).toString().padStart(2, '0')
        val body = "$lira,$kr"
        return (if (negative) "-" else "") + (if (withSymbol) "₺" else "") + body
    }

    /** Taşmaya karşı güvenli toplama (taşarsa ArithmeticException). */
    fun add(a: Long, b: Long): Long = Math.addExact(a, b)

    fun subtract(a: Long, b: Long): Long = Math.subtractExact(a, b)
}
