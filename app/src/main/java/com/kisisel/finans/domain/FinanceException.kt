package com.kisisel.finans.domain

import com.kisisel.finans.core.Money

/** Kullanıcıya gösterilebilir Türkçe mesajlı iş kuralı hataları. */
sealed class FinanceException(message: String) : Exception(message) {
    class InvalidInput(message: String) : FinanceException(message)
    class PersonNotFound : FinanceException("Kişi bulunamadı.")
    class PersonArchived : FinanceException("Kişi arşivlenmiş. Önce arşivden çıkarın.")
    class TransactionNotFound : FinanceException("İşlem bulunamadı.")
    class AlreadyCancelled : FinanceException("İşlem zaten iptal edilmiş.")
    class NothingToSettle : FinanceException("Kapatılacak açık tutar yok.")
    class ExceedsRemaining(val remainingKurus: Long) :
        FinanceException("Tutar kalan bakiyeyi aşıyor. Kalan: ${Money.format(remainingKurus)}")
    class HasActiveAllocations :
        FinanceException("Bu kayıt için tahsilat/ödeme yapılmış. Önce onları iptal edin.")
    class LinkedToInvoice :
        FinanceException("Bu işlem bir faturaya bağlı. Fatura ekranından geri alın.")
    class WouldOverSettle :
        FinanceException("İptal, tahsilat/ödemelerin alacak/borçtan fazla kalmasına yol açar.")

    class OccurrenceNotFound : FinanceException("Fatura dönemi bulunamadı.")
    class TemplateNotFound : FinanceException("Fatura bulunamadı.")
    class OccurrenceAlreadyPaid : FinanceException("Bu dönem zaten ödenmiş.")
    class OccurrenceNotPaid : FinanceException("Bu dönem ödenmiş değil.")
    class OccurrenceNotPayable : FinanceException("İptal edilmiş dönem ödenemez.")
    class AmountRequired : FinanceException("Önce fatura tutarını girin.")

    /** Yedek dosyası/parolası/içeriği ile ilgili kullanıcıya gösterilebilir hata. */
    class BackupInvalid(message: String) : FinanceException(message)
    class AgendaNotFound : FinanceException("Ajanda kaydı bulunamadı.")
}
