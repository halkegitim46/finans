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

## Aşama 3 – İşlem katmanı
- `domain/repository` arayüzleri, `data/repository` Room uygulamaları. Yazma işlemleri `db.withTransaction` içinde: hata → tam geri alma.
- **Hata modeli**: use case/repository `FinanceException` (Türkçe mesajlı) fırlatır; ViewModel yakalayıp gösterir.
- **Çift kayıt**: form açılırken `UUID` üret, aynı anahtar tekrar gelirse mevcut kayıt döner.
- **Tahsilat/ödeme** kalan tutarı aşamaz; kalan = alacak/borç − tahsilat/ödeme (başlangıç bakiyesi dahil).
  Tutar açık kayıtlara eskiden yeniye (FIFO) dağıtılır. Tutar verilmezse (kapatma) kalanın tamamı.
- **İptal**: tahsilat/ödeme iptal → dağıtımlar otomatik serbest kalır (geçmiş korunur).
  Üzerine tahsilat yapılmış alacak/borç iptal edilemez; faturaya bağlı işlem fatura ekranından geri alınır (Aşama 5).
- **Düzeltme**: yalnızca açıklama/tarih/vade güncellenir; tutar hatası için iptal + yeniden giriş.
- **Ana sayfa**: bu ay ödemeler = kişi adına ödeme + borç ödeme; tahsilat ayrı. Hiçbir tutar iki kez sayılmaz.
- Okuma akışları (Flow) repository üzerinden ViewModel'lere doğrudan verilir.
- Testler: Robolectric + bellek içi Room (`LedgerRepositoryTest`), saf birim testler (`ValidatorsTest` vb.).

## Aşama 4 – Kişiler ekranları
- `ui/navigation`: alt menü (6 bölüm), NavHost. Üst düzey ekranlar menüyü gösterir; detay/düzenle ekranları gizler.
- Kişiler: liste + arama + arşiv görünümü, ekle/düzenle (başlangıç bakiyesi yalnızca eklerken), detay (6 bakiye alanı,
  işlem geçmişi, borç ver/al, adına ödeme, tahsilat, borç öde, alacak/borç kapat, iptal, arşivle).
- Henüz geliştirilmeyen sekmeler (Ana Sayfa, Faturalar, İşlemler, Raporlar, Ayarlar) açıkça "Aşama N'te eklenecek" ekranı gösterir.
- ViewModel'ler Hilt ile; hata mesajları `launchCatching` ile Türkçe, beklenmeyen hatalarda ayrıntı gösterilmez/loglanmaz.
- Çift dokunuş: giriş penceresi açılırken UUID anahtar üretilir, kaydederken buton kilitlenir.

## Aşama 5 – Faturalar
- **Şablon + dönem**: `invoice_templates` tanım, `invoice_occurrences` her dönem. Dönem anahtarı (şablon, periodKey) UNIQUE → üretim idempotent.
- **Üretim kuralı**: ilk dönem her zaman oluşur; sonraki dönemler şablonun oluşturulduğu günden önceyse atlanır. Ufuk: bugün + 45 gün
  (uygulama açılışında ve şablon eklenince; günlük otomatik üretim Aşama 7 WorkManager'da).
- **Ödeme** (tek DB transaction): dönem durumu koşullu güncellenir (çift ödeme engeli), faturada kişi varsa `PAID_ON_BEHALF`
  işlemi (occurrenceId bağlı) açılır. Kişisiz (kendi) faturada alacak oluşmaz. Yalnızca o dönem kapanır.
- **Geri alma**: dönem tekrar açılır, bağlı alacak iptal edilir; üzerine tahsilat dağıtılmışsa engellenir. Defterden doğrudan iptal edilemez (LinkedToInvoice).
- **Değişken tutar**: dönem tutarı boş kalır; tutar girilmeden ödenemez.
- **Şablon düzenleme** yalnızca sonraki dönemleri etkiler; tekrar/ilk vade değişmez. Arşiv: yeni dönem üretilmez, geçmiş korunur.
- Hatırlatma ayarları (7/3/1/vade günü, gecikme aralığı) şablonda saklanır; bildirimler Aşama 7'de.

## Aşama 6 – Ana Sayfa ve İşlemler
- **Ana Sayfa**: net bakiye, toplam (kalan) alacak/borç, bu ay ödemeler/tahsilatlar (hepsi veritabanından, `observeDashboard`),
  geciken ve 30 gün içinde yaklaşan faturalar, son 8 işlem, hızlı işlemler (alacak/borç ekle, fatura ekle, ödeme yap, tahsilat al).
- **Hızlı işlem akışı**: kişi seç → tutar/tarih penceresi (Aşama 4'teki `EntryDialog`) → aynı use case'ler. "Ödeme yap":
  borcumu öde (DEBT_PAYMENT) veya kişi adına öde (PAID_ON_BEHALF). Tahsilat/ödemede yalnızca açık bakiyesi olan kişiler listelenir.
- **İşlemler**: tüm kişilerin defteri; kişi/açıklama arama, tür filtresi, iptal edilenleri göster; tek tür seçilince toplam.
- Sayfa açıkken gün/ay değişirse ana sayfa toplamları yeniden açılışta güncellenir (WorkManager Aşama 7).
