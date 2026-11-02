# Kişisel Finans Takip – Mimari ve Plan

## Katmanlar (tek modül, paket bazlı Clean Architecture)
- `data/` : Room (entity, dao, database, migration), DataStore, repository uygulamaları, yedekleme
- `domain/` : model, repository arayüzleri, use case'ler (MoneyCalculator, PayInvoiceUseCase vb.)
- `ui/` : Compose ekranları + ViewModel (Hilt), Navigation, tema
- `work/` : WorkManager (hatırlatma, boot sonrası yeniden planlama, dönem üretimi)
- `security/` : parola hash (PBKDF2), Keystore, biyometrik, DB/yedek şifreleme

## Para
Tüm tutarlar `Long` (kuruş). Float/Double yok. Gösterim: NumberFormat tr-TR / TRY.

## Veritabanı (Room)
- Person(id, adSoyad, telefon?, aciklama, baslangicBakiyeKurus, arsivli, olusturma, guncelleme)
- InvoiceTemplate(id, personId→Person, ad, tur, tutarKurus?, tutarSabit, tekrar(AYLIK/YILLIK/OZEL), ozelGunAralik?, gunSayisi/vadeGunu, hatirlatmaAyarJson, aktif, arsivli)
- InvoiceOccurrence(id, templateId→InvoiceTemplate, donemKey(UNIQUE templateId+donem), tutarKurus, sonOdemeTarihi, durum(ODENMEDI/ODENDI/IPTAL), odenenPaymentId?)
- Transaction(id, personId→Person, tur(BORC_VERME, BORC_ALMA, KISI_ADINA_ODEME, TAHSILAT, BORC_ODEME, KAPATMA), tutarKurus, islemTarihi, vade?, aciklama, durum, occurrenceId?, idempotencyKey UNIQUE, iptal, olusturma, guncelleme)
- Payment(id, transactionId→Transaction, tutarKurus, tarih, not) — kısmi ödeme/tahsilat satırları
- AppSettings(key, value) — kilit, hatırlatma varsayılanları, tema
Silme yok: `arsivli` / `iptal` bayrakları. İndeks: FK sütunları, vade, donemKey. Çok adımlı yazımlar `@Transaction`.

## Örnek akış (dayı faturası)
Fatura öde → tek DB transaction: occurrence=ODENDI + Transaction(KISI_ADINA_ODEME 1500) → kişi alacağı +1500. Tahsilat 500 → Transaction(TAHSILAT) → kalan 1000. Gelir olarak ayrıca sayılmaz.

## Ekranlar
Kilit/Kurulum → Ana Sayfa | Kişiler (liste→detay) | Faturalar (liste→detay/ekle) | İşlemler | Raporlar | Ayarlar (güvenlik, yedek, bildirim, tema)

## Aşamalar
1. İskele + GitHub Actions (bu paket)
2. Para/tarih yardımcıları + Room şeması + DAO + testler
3. Repository + use case'ler (borç/alacak/kısmi ödeme/tahsilat) + testler
4. Kişiler ekranları
5. Fatura şablon/dönem üretimi + ödeme akışı
6. Ana sayfa + işlemler
7. WorkManager bildirimleri
8. Güvenlik (parola, biyometrik, SQLCipher, Keystore)
9. Yedek/geri yükleme (şifreli)
10. Raporlar + grafik + CSV
11. Sertleştirme, migration testleri, release APK

## Aşama 2 – Tasarım kararları
- **Payment tablosu = dağıtım**: bir tahsilat/ödeme işlemi (settlement) bir alacak/borç işlemini (claim) kaç kuruş kapattı.
  Claim kalanı = tutar − Σ(dağıtımlar). Dağıtılmayan kısım kişi düzeyinde kalır. Kapatma işlemleri
  ayrı tür değil: kalan tutar kadar tahsilat/ödeme kaydıdır.
- **Kişi bakiyesi** (LedgerCalculator): tahsilat yalnızca alacağı, borç ödemesi yalnızca borcu azaltır.
  Başlangıç bakiyesi pozitif = alacak, negatif = borç.
- **Silme yok**: kişi → arşiv, işlem → iptal, tüm FK'ler RESTRICT (fatura dönemi → işlem bağı SET_NULL).
- **Çift kayıt koruması**: transactions.idempotencyKey UNIQUE; (templateId, periodKey) UNIQUE; (settlementTxId, claimTxId) UNIQUE.
- **Gecikme** kayıtta tutulmaz; ödenmemiş ve vadesi bugünden önce olan dönem gecikmiştir.
- Tarihler epochDay, zaman damgaları epoch ms. Şema `app/schemas` altına dışa aktarılır (commit edin).
