package com.kisisel.finans.domain

import com.kisisel.finans.core.Money
import java.time.LocalDate

private fun invalid(message: String): Nothing = throw FinanceException.InvalidInput(message)

/** Girdi doğrulama: hatalı tutar/tarih/ad girişleri kayıttan önce reddedilir. */
object Validators {
    val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    private val PHONE = Regex("""[0-9+() \-]{7,20}""")

    fun fullName(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) invalid("Ad soyad boş olamaz.")
        if (t.length > 100) invalid("Ad soyad en fazla 100 karakter olabilir.")
        return t
    }

    fun phone(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (!PHONE.matches(t)) invalid("Telefon numarası geçersiz.")
        return t
    }

    fun note(raw: String): String {
        val t = raw.trim()
        if (t.length > 500) invalid("Açıklama en fazla 500 karakter olabilir.")
        return t
    }

    fun amount(kurus: Long): Long {
        if (kurus <= 0 || kurus > Money.MAX_KURUS) {
            invalid("Tutar 0'dan büyük ve en fazla ${Money.format(Money.MAX_KURUS)} olmalı.")
        }
        return kurus
    }

    fun openingBalance(kurus: Long): Long {
        if (kurus < -Money.MAX_KURUS || kurus > Money.MAX_KURUS) invalid("Başlangıç bakiyesi çok büyük.")
        return kurus
    }

    fun date(date: LocalDate): LocalDate {
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) invalid("Tarih geçersiz.")
        return date
    }

    fun dueDate(date: LocalDate, due: LocalDate?): LocalDate? {
        if (due == null) return null
        date(due)
        if (due.isBefore(date)) invalid("Vade tarihi işlem tarihinden önce olamaz.")
        return due
    }

    fun idempotencyKey(key: String): String {
        if (key.isBlank() || key.length > 100) invalid("İşlem anahtarı geçersiz.")
        return key
    }

    fun invoiceName(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) invalid("Fatura adı boş olamaz.")
        if (t.length > 100) invalid("Fatura adı en fazla 100 karakter olabilir.")
        return t
    }

    /** Sabit tutarlı faturada tutar zorunlu; değişkende isteğe bağlı. */
    fun invoiceAmount(isFixed: Boolean, kurus: Long?): Long? {
        if (kurus == null) {
            if (isFixed) invalid("Sabit tutarlı faturada tutar gerekli.")
            return null
        }
        return amount(kurus)
    }

    fun customInterval(days: Int?): Int {
        if (days == null || days !in 1..365) invalid("Özel tekrar aralığı 1-365 gün olmalı.")
        return days
    }

    fun reminderDays(days: Set<Int>): Set<Int> {
        if (days.any { it !in 0..30 }) invalid("Hatırlatma günleri 0-30 arasında olmalı.")
        return days
    }

    fun overdueInterval(days: Int): Int {
        if (days !in 0..30) invalid("Gecikme hatırlatma aralığı 0-30 gün olmalı.")
        return days
    }
}
