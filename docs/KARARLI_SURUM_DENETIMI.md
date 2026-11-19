# Kararlı sürüm denetimi: tasarım ve kodlama ilkeleri

Kapsam: ana kod (143 dosya, ~13 bin satır), testler (42 dosya, ~5 bin satır), manifest, Gradle ve CI.
Yöntem: otomatik taramalar (kullanılmayan içe aktarma, riskli kalıplar, katman bağımlılık yönü, tekrarlanan kod, saat kaynağı, istisna yönetimi, yedekleme/manifest ayarları) + riskli noktaların elle incelenmesi.
**Sınırlama:** bu ortamda derleme/test çalıştırılamaz; tüm değişiklikler CI'da (Android CI + Release: testler + derleme) doğrulanır.

## 1. Güçlü yönler (korunacak)
| İlke | Durum |
|---|---|
| **Katmanlı mimari / bağımlılık yönü** | Taramada ihlal yok: `domain` hiçbir Android/Room/UI sınıfına bağımlı değil; `data → ui` yok; ViewModel'ler DAO'ya doğrudan erişmiyor (depo + use case üzerinden). |
| **Para doğruluğu** | Tüm tutarlar `Long` kuruş; `Float/Double` yok; taşma korumalı `Money`. |
| **Veri kaybı güvenliği** | Silme yok (arşiv/iptal), Room migration'ları açık ve testli, yıkıcı geçiş yok, yedek içeriği finansal kurallarla doğrulanıyor. |
| **Gizlilik** | `INTERNET` izni yok (çevrimdışı), SQLCipher, Keystore'a sarılı anahtar, `allowBackup=false`, kasa için ayrı doğrulama, hassas işaretli/temizlenen pano, günlükte (`Log/println`) hiçbir kayıt yok. |
| **Test edilebilirlik** | `Clock` enjeksiyonu, saf hesap nesneleri (`RecurrenceCalculator`, `ReportCalculator`, `AgendaBuilder`, `ReminderPlanner`), ~370 test. |
| **Sağlam hata yönetimi** | Alan istisnaları (`FinanceException`), kullanıcıya anlaşılır iletiler, açılışta sağlık denetimi + kurtarma ekranı. |

## 2. Bulgular ve durum
| # | Önem | Bulgu | Durum |
|---|---|---|---|
| 1 | Orta | Üç yerde `catch (e: Exception)` iptal istisnasını (`CancellationException`) yutuyordu (yedek/PDF/CSV dışa aktarma). ViewModel kapanınca sahte "yazılamadı" iletisi ve bozulan yapısal eşzamanlılık. | **Düzeltildi**: önce `CancellationException` yeniden fırlatılıyor. |
| 2 | Orta | Android 12+ cihazdan cihaza aktarım için veri çıkarım kuralı yoktu (`allowBackup=false` tek başına bu kanalı açıkça kapatmaz). | **Düzeltildi**: `data_extraction_rules.xml` (bulut + cihaz aktarımı) ve `backup_rules.xml` (Android 11-) tüm alanları hariç tutar. |
| 3 | Düşük | Üç `!!`: `AgendaBuilder`, `ReportCalculator`, `RecurrenceCalculator`. | **Düzeltildi**: `requireNotNull` (açıklayıcı ileti) ve `mapNotNull` ile güvenli biçime çevrildi; davranış aynı. |
| 4 | Düşük | `requireActivePerson` aynı gövdeyle üç depoda kopyaydı. | **Düzeltildi**: tek `PersonDao.requireActive` (`PersonGuard.kt`). |
| 5 | Düşük | `Locale("tr","TR")` 9 yerde, tarih biçimleri (`dd.MM.yyyy`, `HH:mm` …) 7 yerde ayrı tanımlıydı. | **Düzeltildi**: `core/Locales.kt` (`TurkishLocale`) ve `core/DateFormats.kt`; test eklendi. |
| 6 | Düşük | Kullanılmayan 12 içe aktarma, güvensiz `as ClipboardManager` dönüşümü, `AppLockManager.backgroundedAt` için eksik `@Volatile`. | **Düzeltildi**. |
| 7 | Düşük | `LegacyDatabaseMigrator` içinde `runBlocking` (tek seferlik, IO iş parçacığında, yükleme ekranı ardında). | **Kabul edildi**: kapsamı dar, belgeli, testli; kaldırmak gereksiz risk. |
| 8 | Düşük | İki arayüz noktasında doğrudan `LocalDate.now()` (liste varsayılanı, tarih seçici başlangıcı) ve kilit ekranı sayacında `System.currentTimeMillis()`. | **Kabul edildi**: yalnızca görsel başlangıç değerleri; iş mantığı `Clock` kullanıyor. |
| 9 | Bilgi | Kullanıcıya dönük metinler kod içinde Türkçe sabit (`strings.xml` yok). Tek dilli ürün için sorun değil; çok dilli olunacaksa büyük bir iş. | **Öneri** (kapsam dışı). |
| 10 | Bilgi | Uzun parçalar: `BackupSnapshotValidator.validate` (~140 satır), `SettingsScreen` (~190), `PersonDetailScreen` (~170). Davranış testli ve doğru; bölme, derleme yapılamayan ortamda gereksiz risk. | **Öneri**: CI yeşilken ayrı bir iş olarak bölünsün. |
| 11 | Bilgi | Arayüz (Compose) ve cihaz üstü akışların (biyometrik, bildirim) otomatik testi yok. | **Öneri**: gerçek cihaz kontrol listesi (aşağıda) ve cihaz testi workflow'u. |

## 3. Eklenen altyapı
- `.editorconfig`: biçim kuralları (girinti, satır uzunluğu, son satır, boşluk temizliği).
- `code-quality.yml` (elle): Android Lint + detekt raporu; derlemeyi etkilemez.

## 4. Kararlı sürüm için kontrol listesi
1. Actions "Android CI + Release" yeşil, 0 test hatası; artifact'ta `FinansTakip_V<N>_release.apk` ve `mapping-V<N>` var.
2. "Proje dosyalarını tamamla" çalıştırıldı: `gradlew`, `gradle/wrapper`, `app/schemas/**/3.json` depoda (şema/migration testleri tam etkin).
3. Telefonda (release APK): kurulum → kilit kurulumu → kişi/fatura/işlem ekle → PDF/CSV dışa aktar → yedek al/geri yükle → ajanda notu bildirimi → kasa ekle/kopyala/kilit → arka plana alıp dönüşte kilit.
4. Yükseltme denemesi: önceki sürümün üstüne kur; veriler korunmalı (şema 1→3).
5. Pil tasarrufu/bildirim izni kapalı senaryoda uyarılar (Ayarlar) doğru.
6. İmza anahtarı (`.jks`) ve parolaları güvenli yerde yedekli; aynı anahtarla imzalamaya devam edin.
7. Sürüm notu: bilinen sınırlar (README) güncel.

## 5. İkinci tur (Ana Sayfa yeniden tasarımı + ölü kod temizliği)
| # | Önem | Bulgu | Durum |
|---|---|---|---|
| 12 | Orta | Ana Sayfa'da 7 hızlı işlem düğmesi ve aynı bilgiyi tekrarlayan üç liste (ajanda, geciken faturalar, yaklaşan faturalar) vardı; ilk bakışta karar vermek zordu. | **Yeniden tasarlandı**: net durum özeti, 4 hızlı işlem, tek "dikkat gerektirenler" listesi (geciken + 7 gün, fatura/vade/not), son 5 hareket, ilk kullanımda karşılama kartı. Fatura ekle, ajanda notu ve kasa düğmeleri ekrandan kalktı (yan menüde/kendi bölümlerinde). |
| 13 | Düşük | Ölü kod: `InvoiceDao.observeUnpaidBetween`, `TransactionDao.getBetween`, `occurrenceIdOfKey` (yalnızca testte kullanılıyordu), `HomeLogic.splitInvoices` + testi. | **Silindi** (şemayı etkilemez). |
| 14 | Bilgi | Ana Sayfa'da `HomeDialog.PickPaymentKind(val unused: Int = 0)` gereksiz yer tutucu parametre. | **Düzeltildi** (`data object`). |
| 15 | Bilgi | Kullanılmayan bağımlılıklar: `compose.ui.tooling.preview` (hiç `@Preview` yok), katalogda `room-testing`. | **Öneri**: kendi düzenlediğiniz `build.gradle.kts` ile çakışmamak için dokunulmadı; isterseniz kaldırılır. |
| 16 | Bilgi | `HomeViewModel` birleştirme akışının (özet + ajanda + hareketler) otomatik testi yok; mantığı oluşturan parçalar (`AgendaBuilder`, depolar) testli. | **Öneri**. |

Ek taramalar temiz çıktı: yaşam döngüsüz `collectAsState` yok, tıklanabilir simgelerde eksik `contentDescription` yok, hiçbir yerde `Log/println` yok, kullanılmayan import kalmadı, kullanılmayan kaynak dosyası yok.

## 6. CI/CD iyileştirmeleri (yüklenen `android-build_1.yml` üzerinde)
| # | Önem | Bulgu | Durum |
|---|---|---|---|
| 17 | Orta | Test adımı `shell: bash` (örtük `-e`) altında `pipefail` ile çalışıyordu: test kırılınca adım, hata özetini yazan bölüme gelmeden sonlanıyordu; ayrıca `continue-on-error` yüzünden `build` işi testler kırıkken YEŞİL bitiyordu. | **Düzeltildi**: sonuç elle yönetiliyor, özet her zaman yazılıyor, test kırıksa iş APK yüklendikten sonra KIRMIZI bitiyor. |
| 18 | Orta | `versionCode = run_number` (küçük sayı): önceki zaman damgalı sürümler telefonda kuruluysa Android "downgrade" diye reddederdi. | **Düzeltildi**: ortak, hep artan zaman damgalı kod (UTC dakika). |
| 19 | Orta | Parolalar iş düzeyi `env` ve `GITHUB_ENV` ile TÜM sonraki adımlara (üçüncü taraf `softprops/action-gh-release` dahil) açıktı. | **Düzeltildi**: yalnızca imza ve Gradle adımına verilir; `GITHUB_ENV`'e sadece anahtar dosya yolu yazılır; GitHub Release `gh` CLI ile (üçüncü taraf eylem kaldırıldı). |
| 20 | Düşük | Preflight: `proguard-rules.pro` yanlış yol (hep "YOK"), `KEY_ALIAS` özet satırında değer iki kez yazılıyordu. | **Düzeltildi**. |
| 21 | Düşük | Debug APK rastgele anahtarla imzalanıyordu (güncelleme çakışması). | **Düzeltildi**: sabit anahtar varsa debug de onunla imzalanır (PR'larda atlanır). |
| 22 | Düşük | Tekrarlanan adımlar (JDK/Gradle/wrapper/sürüm/imza), satır sonu (CRLF) riski, wrapper doğrulaması yok, bağımlılık güncelleme otomasyonu yok. | **Düzeltildi**: ortak bileşik eylem + test edilmiş betikler, `.gitattributes`, wrapper doğrulama (depoda varsa), `dependabot.yml`, Lint işi, genel durum özeti, artifact saklama süreleri. |
