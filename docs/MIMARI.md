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

## Aşama 7 – Bildirimler (WorkManager)
- **Akış**: `ReminderWorker` (günlük 09:00 civarı) → `ReminderService.run()` → eksik dönemleri üret → `ReminderPlanner` (saf mantık)
  bugün gönderilecekleri seçer → `ReminderNotifier` bildirimi gösterir → `ReminderLog` (DataStore) gönderilenleri kaydeder.
- **Kurallar**: vadeden önce şablonda seçili günlerde (7/3/1/vade günü) bir kez; gecikmişse 1. günde ve sonra her N günde (N=0 kapalı).
  Ödenmiş/iptal/arşivli/hatırlatması kapalı dönemler için gönderilmez. Aynı anahtar ("dönem:gün") ikinci kez gönderilmez.
- **Çalışma zamanı**: uygulama açılışı + yeniden başlatma/güncelleme (`BootReceiver`) → `schedule()` (KEEP, tekrar çağrı güvenli).
  Açılışta bir kez ek kontrol (kaçan günleri yakalar). `Mutex` ile eşzamanlı çalışma çift bildirim üretmez.
- **Android**: POST_NOTIFICATIONS (13+) ilk açılışta açıklamalı istenir; kapalıysa Ana Sayfa'da uyarı + ayarlar bağlantısı.
  Pil: WorkManager Doze'a uyumlu, tam zamanlı alarm/pil muafiyeti yok → bildirim dakikalar-saatler gecikebilir.
- **Gizlilik**: kilit ekranında genel metin ("Fatura hatırlatması"), tutar/ad yalnızca kilit açıkken görünür.
- **Dokununca**: `MainActivity` (singleTop) `occurrence_id` ekstrasını alır → fatura dönemi detayı açılır.
  ⚠ Aşama 8'de uygulama kilidi eklenince bu yönlendirme kilit ekranı aşıldıktan sonra uygulanmalı.

## Aşama 8 – Güvenlik
- **Parola**: ilk kurulumda zorunlu (en az 8 karakter). PBKDF2-HMAC-SHA256, 600.000 iterasyon, 16 bayt rastgele tuz; açık metin saklanmaz,
  sabit zamanlı karşılaştırma. PIN (4-8 rakam, isteğe bağlı) aynı yöntemle; PIN ayarlamak için parola tekrar doğrulanır.
- **Deneme sınırı** (parola + PIN ortak, kalıcı): 5. hatalı denemede 30 sn kilit, sonra her hatada 2 katı, en çok 15 dk.
- **Biyometrik**: yalnızca güçlü (class 3), kolaylık amaçlı kilit açma; başarılıysa deneme sayacı sıfırlanır.
- **Kilit**: süreç yeniden başlayınca her zaman kilitli. Arka plana geçince (ayarlanabilir süre: hemen/30 sn/1 dk/5 dk) kilitlenir;
  ekran döndürme kilitlemez. Bildirimden gelen fatura yönlendirmesi kilit açılana kadar bekler.
- **Veritabanı şifreleme**: SQLCipher (AES-256), rastgele 256 bit anahtar → Android Keystore AES-GCM anahtarıyla sarmalanıp `db_key.bin` içinde saklanır.
  Anahtar parolaya bağlı DEĞİL (arka plan işleri kilitliyken de çalışabilsin). Keystore anahtarı kullanıcı doğrulaması istemez.
- **Eski veriden geçiş**: şifresiz `kisisel_finans.db` varsa ve şifreli veritabanı boşsa tüm tablolar kimlikler korunarak tek transaction'da
  şifreli veritabanına kopyalanır, kayıtlar birebir doğrulanır; YALNIZCA başarılıysa eski dosya silinir. Başarısızlıkta eski dosya durur,
  Ayarlar'da uyarı + silme seçeneği görünür.
- **Unutulan parola**: kurtarılamaz (kasıtlı). Yollar: şifreli yedekten geri yükleme (Aşama 9) veya "SİL" yazarak tüm verileri silip baştan başlama.
- **Ekran koruması**: FLAG_SECURE (varsayılan açık): ekran görüntüsü ve son uygulamalar önizlemesi engellenir.
- **Loglar**: parola/PIN/tutar loglanmaz; beklenmeyen hatalarda ayrıntı gösterilmez.
- Kapsam dışı/sonraki: yedek dosyası şifreleme (Aşama 9). Yedek için dosya seçici açılınca uygulama arka plana gidebilir; Aşama 9'da kısa süreli kilit istisnası eklenecek.

## Aşama 9 – Şifreli yedekleme
- **Akış (yedek al)**: veritabanı snapshot → JSON → gzip → AES-256-GCM (ayrı **yedek parolası**, PBKDF2-HMAC-SHA256 600.000 iterasyon)
  → kullanıcının seçtiği dosyaya (Android Storage Access Framework; depolama izni gerekmez, ağ erişimi yok).
- **Dosya düzeni**: `KFTB | sürüm | iterasyon | tuz(16) | nonce(12) | anahtar kontrolü(8) | şifreli veri + GCM etiketi`.
  Başlık GCM'de AAD olarak korunur. Anahtar kontrolü yanlış parolayı "bozuk dosya"dan ayırır.
- **Veri bütünlüğü**: GCM etiketi (değişiklik/bozulma), JSON'da biçim + sürüm + kayıt sayıları doğrulaması, gzip boyut sınırı (64 MB).
- **Geri yükleme (iki aşama)**: dosya seç → yedek parolası → çöz + doğrula (veri değişmez) → özet (tarih, kayıt sayıları) → onay →
  mevcut TÜM veriler tek transaction'da silinip yedek yüklenir. Hata olursa (ör. tutarsız yabancı anahtar) önceki veriler olduğu gibi kalır.
- **Uyumluluk**: yedeğin veritabanı sürümü uygulamanınkinden yeniyse reddedilir; eskiyse (ileride migration) kabul edilecek şekilde tasarlandı.
- **Kilit**: dosya seçici uygulamayı arka plana attığından, seçiciye gitmeden önce `allowExternalUi` çağrılır; dönüşte BİR kez (en çok 5 dk) kilitlenmez.
- **Son yedek bilgisi**: tarih, dosya adı, boyut, kayıt sayıları (içerik değil) DataStore'da.
- **Unutulan uygulama parolası**: Ayarlar'dan veya kilit ekranından verileri silip yeni parola belirle → Ayarlar → Yedekleme → Yedekten geri yükle.
- Yedek dosyası uygulama dışına çıktığı için güvenliği YEDEK PAROLASINA bağlıdır: güçlü ve ayrı bir parola kullanın, parolayı yedekten ayrı saklayın.

## Aşama 10 – Raporlar
- **Hesap**: `ReportCalculator` saf fonksiyonlar (kuruş/Long). İptal edilen işlemler hiçbir rapora girmez.
- **Aralık**: Bu ay, geçen ay, son 3 ay, bu yıl, geçen yıl veya özel (başlangıç/bitiş dahil). Önceki dönem: tam ay → önceki ay,
  tam yıl → önceki yıl, diğerleri → aynı uzunlukta hemen önceki aralık.
- **Özet**: alacak oluşan (borç verme + kişi adına ödeme), tahsilat, borç oluşan, borç ödemesi, net değişim; önceki dönemle fark ve yüzde
  (yüzde yalnızca gösterim); aylık seri (en çok 36 ay) ve yıllık seri; iki grafik (alacak/tahsilat, borç/ödeme).
- **Kişi dökümü**: dönem başı bakiye (başlangıç bakiyesi + önceki hareketler), yürüyen bakiyeli hareketler, dönem sonu bakiye. + = kişi bana borçlu.
- **Fatura geçmişi**: ödeme tarihi aralıkta olan dönemler, fatura başına toplam ve dönem listesi.
- **Tahsilat geçmişi**: aralıktaki tahsilatlar ve toplam.
- **Bekleyen**: bugün itibarıyla geciken ve bekleyen faturalar + vadesi olan açık alacak/borçlar. Faturadan doğan alacaklar (vade = fatura vadesi) dahil edilmez.
- **Grafik**: harici kütüphane yok; Compose ile gruplanmış sütun grafiği, erişilebilirlik açıklamalı, altında tam tutarlı tablo.
- **CSV**: Excel (TR) uyumlu: `;` ayraç, ondalık `,`, binlik ayracı yok, UTF-8 BOM, CRLF; formül enjeksiyonuna karşı `=+-@` ile başlayan metne `'` eklenir.
  Dışa aktarmadan önce "dosya şifrelenmez" uyarısı. Dosya seçici için kilit istisnası (Aşama 9'daki mekanizma).

## Aşama 11 – Sertleştirme ve teslimat
- **Gün değişimi**: `DayTicker` ("bugün" tek kaynağı) uygulama ön plana gelince yenilenir; ana sayfa, faturalar ve raporlar (hazır dönemler dahil) gün/ay değişince yeniden hesaplanır.
- **Release**: ayrı `release.yml` (elle): secret zorunlu, testler geçmeden üretmez, `apksigner` doğrulaması, R8 `mapping` artifact'ı, `minify` anahtarı.
  Debug ve release aynı sabit anahtarı kullanır (güncelleme veri kaybı olmadan). `generate-keystore.yml` yalnızca özel depoda, tek seferlik.
- **Testler**: `LegacyMigrationTest` (şifresiz → şifreli veri taşıma, hedef dolu/yok/başarı senaryoları), `MigrationsTest` (şema sürümü artınca eksik migration'ı yakalar), `DayTickerTest`.
- **ABI**: arm64-v8a, armeabi-v7a, x86_64 (SQLCipher yerel kütüphaneleri). **Tema**: grafik serileri için ayrı üçüncül renk (açık/koyu).

### Şartname uyumu (özet)
| Şartname maddesi | Durum |
|---|---|
| Kotlin, Compose, Material 3, MVVM + Clean, Room, Flow, Hilt, WorkManager, DataStore, Keystore, Biometric, Navigation | Tamam |
| "Modüler" yapı | Tek Gradle modülü + paket bazlı katmanlar (kasıtlı sadelik) |
| Para: kuruş, Float/Double yok | Tamam (grafik çubuk oranı yalnızca görsel) |
| Kişi/alacak-borç/fatura/ödeme-tahsilat ilişkisi, kısmi işlemler, iptal/arşiv | Tamam |
| Bildirimler (7/3/1/vade/geciken, dokununca açılış, yeniden başlatma, izin, pil) | Tamam; cihazda elle doğrulanmalı |
| Raporlar + grafik + dışa aktarma | Tamam; Excel yerine CSV |
| Güvenlik: parola, PIN, biyometrik, arka plan kilidi, şifreli DB, Keystore, şifreli yedek, log'da hassas veri yok | Tamam |
| Yedek: manuel, dışa aktarma, geri yükleme, tarih/dosya bilgisi, bütünlük, bozuk dosya hatası | Tamam |
| Testler (finansal hesap, kısmi ödeme/tahsilat, fatura, tekrar, bildirim planlama, yedek, kilit, migration, hatalı girişler) | Birim/Robolectric testleri tamam; arayüz ve cihaz testleri yok |
| Derlenebilir, telefona kurulabilir APK | GitHub Actions ile (debug ve imzalı release) |
