# Kişisel Finans Takip

Kişisel alacak, borç, ödeme, tahsilat ve düzenli fatura takibi için tek kullanıcılı, çevrimdışı, şifreli Android uygulaması.
Kotlin • Jetpack Compose • Material 3 • Room (SQLCipher) • Hilt • WorkManager • DataStore • Android Keystore • BiometricPrompt.

Kullanım için: [`docs/KULLANIM_KILAVUZU.md`](docs/KULLANIM_KILAVUZU.md) · Mimari ve tasarım kararları: [`docs/MIMARI.md`](docs/MIMARI.md)

**Kapsam:** kişisel alacak-borç ve fatura takibi aracıdır; muhasebe/vergi/e-defter yazılımı değildir (KDV, çift taraflı kayıt, mali müşavir çıktısı kapsam dışıdır).

## Özellikler
- **Kişiler**: her kişi için ayrı hesap; bir kişi hem borçlu hem alacaklı olabilir; silme yok, arşivleme var.
- **Alacak / borç**: borç verme/alma, kişi adına ödeme, kısmi ve toplu tahsilat/ödeme, kapatma, iptal. Tutarlar kuruş (`Long`).
- **Faturalar**: elektrik, su, doğalgaz, internet, telefon, kira, aidat, kredi, diğer; sabit/değişken tutar; aylık/yıllık/özel tekrar;
  her dönem ayrı kayıt; fatura ödenince kişinin alacağına eklenir.
- **Hatırlatmalar** (WorkManager): fatura vadeleri (7/3/1 gün önce, vade günü, gecikme), **borç/alacak vadeleri** (3/1 gün önce, vade günü, gecikince 3 günde bir; Ayarlar'dan kapatılabilir) ve **ajanda notları** (tarih/saatli, zamanında / 1 saat / 1 gün önce); bildirime dokununca ilgili ekran açılır.
- **Ajanda**: faturalar, vadesi olan borç/alacaklar ve kendi notlarınız gün gün tek zaman çizelgesinde; notlar tamamlanabilir, kişiye bağlanabilir, hatırlatıcı kurulabilir.
- **Raporlar**: aylık/yıllık özet, önceki dönemle karşılaştırma, kişi dökümü, fatura ödeme geçmişi, tahsilat geçmişi,
  geciken/ödenmemiş kalemler; grafikler; **PDF** (açık sekme; Kişiler sekmesinde tek kişinin hesap dökümü) ve CSV (Excel) dışa aktarma.
- **Güvenlik**: uygulama parolası (PBKDF2), PIN, biyometrik, arka planda kilit, ekran görüntüsü engeli, AES-256 şifreli veritabanı.
- **Yedekleme**: ayrı parolayla AES-256-GCM şifreli yedek; doğrulamalı, geri alınabilir (atomik) geri yükleme. Hiçbir veri sunucuya gönderilmez.

## Uygulama simgesi
Uyarlanabilir (adaptive) simge: yeşil degrade zemin, beyaz **₺** (gövde ve ayak) ve kehribar iki eğik çizgi; Android 13+ için tek renkli "temalı simge" sürümü de vardır.
Kaynaklar: `app/src/main/res/drawable/ic_launcher_*.xml`; tasarım kaynağı (SVG) ve önizleme: `docs/ikon/`.

## Derleme (GitHub Actions)
`.github/workflows/android-build.yml` her `main`/`master` push'unda debug APK üretir: `FinansTakip_V<N>.apk` (N = çalıştırma numarası,
`versionCode` ve `versionName` otomatik artar). APK önce derlenip yüklenir, birim testleri sonra çalışır; test hatası APK'yı engellemez
ama işi kırmızı yapar. Hata olursa "Başarısız testleri özetle" adımı kırılan testleri listeler, `test-raporu` artifact'ı ayrıntı içerir.

Yerelde: Android Studio'da klasörü açın (JDK 17), `app` modülünü çalıştırın. Testler: `./gradlew testDebugUnitTest`.

> **Veritabanı şema sürümü 2** (ajanda tablosu eklendi). Mevcut kurulumlar açılışta otomatik ve veri kaybı olmadan yükseltilir (`MigrationV2Test`).
> Eski (sürüm 1) yedekler yeni sürümde geri yüklenebilir; sürüm 2 yedekler eski uygulama sürümünde açılmaz (güncelleme istenir).
> Şema dosyasını (`app/schemas/.../2.json`) depoya işlemek için aşağıdaki workflow'u yeniden çalıştırın.

## Depo ilk kurulumu (bir kez): Gradle wrapper + Room şemaları
Wrapper dosyaları (`gradlew`, `gradle/wrapper/*`) ve Room şemaları (`app/schemas/**`) depoda olmalı: aynı Gradle sürümüyle tekrarlanabilir derleme
ve şema/migration denetimi için. Actions → **"Proje dosyalarını tamamla (Gradle wrapper + Room şemaları)"** → Run workflow. İş bitince bu dosyaları depoya kendisi işler
(korumalı dalda push başarısız olursa dosyaları çalıştırmanın çıktısından alıp elle ekleyin). Sonra `SchemaGuardTest` etkinleşir: şema sürümü artırılıp şema/migration eklenmezse test kırılır.
Yerel/CI derlemesi wrapper varsa onu kullanır; yoksa geçici olarak üretir.

## Cihaz testleri (elle)
Actions → **"Cihaz testleri (emülatör)"**: gerçek SQLCipher (anahtarla açma, yanlış anahtar reddi, dosyanın düz SQLite olmaması) ve gerçek Android Keystore
(sarma/açma, anahtar kaybı) testlerini emülatörde koşar. Birim testlerinde (JVM) bu yerel kütüphaneler çalışmadığı için ayrı tutulur.

## Sürüm güncelleme ve imza anahtarı (ÖNEMLİ)
Android, farklı anahtarla imzalı bir APK'yı mevcut kurulumun üstüne yüklemez. Secret tanımlı değilse her derleme rastgele bir debug
anahtarıyla imzalanır ve her güncellemede eski uygulamayı (verileriyle birlikte) kaldırmanız gerekir. Bunu önlemek için **bir kez** sabit anahtar tanımlayın:

**Seçenek A – bilgisayarda:**
```
keytool -genkeypair -v -keystore kft.jks -storetype PKCS12 -alias kft -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 kft.jks     # çıktıyı kopyalayın
```
**Seçenek B – bilgisayarsız (yalnızca ÖZEL depoda):** Actions → "İmza anahtarı üret (tek seferlik)" → Run workflow → biten çalıştırmanın
*Artifacts* bölümünden `kft-anahtar` indirin (içinde `kft.jks`, `kft.jks.b64`, `secretler.txt`). Sonra **artifact'ı ve bu workflow dosyasını silin**.

Ardından *Settings → Secrets and variables → Actions* altına şu 4 secret'ı ekleyin:

| Secret | Değer |
|---|---|
| `KFT_KEYSTORE_BASE64` | `kft.jks` dosyasının base64 çıktısı (`kft.jks.b64` içeriği) |
| `KFT_KEYSTORE_PASSWORD` | anahtar deposu parolası |
| `KFT_KEY_ALIAS` | `kft` |
| `KFT_KEY_PASSWORD` | anahtar parolası (PKCS12'de depo parolasıyla aynı) |

**`kft.jks` dosyasını ve parolaları güvenli bir yerde yedekleyin.** Kaybederseniz sürümleri üst üste kuramazsınız.
Debug ve release derlemeleri aynı anahtarı kullanır; imza parmak izi her çalıştırmada log'a yazılır (aynı kalmalı).
Not: anahtarı sabitledikten sonraki **ilk** kurulumda, rastgele anahtarla kurulu eski sürümü kaldırmanız gerekir (bir kereye mahsus).

## Release APK
Actions → **Release APK (imzalı)** → Run workflow. Secret'lar yoksa durur (asla debug anahtarıyla imzalamaz). Testler geçmeden üretmez,
imzayı `apksigner` ile doğrular, `FinansTakip_V<N>_release.apk` ve R8 `mapping-V<N>` artifact'larını yükler.
Release APK açılışta çöküyorsa `minify` seçeneğini `false` yapıp yeniden çalıştırın (R8 kuralları gerçek cihazda henüz denenmedi) ve hatayı bildirin.

## Kurulum notları
Telefonda APK'yı açarken "bilinmeyen kaynak" izni ve Play Protect "yine de yükle" uyarısı çıkabilir; uygulama Play Store dışından dağıtıldığı için normaldir.

## Bilinen sınırlar
- Tek kullanıcı, tek cihaz, yalnızca Türk lirası ve Türkçe.
- **Parola kurtarılamaz.** Kurtarma yolu: önceden alınmış şifreli yedek veya verileri silip baştan başlama. Düzenli yedek alın.
- Cihaz sıfırlanırsa veya Keystore anahtarı kaybolursa şifreli veritabanı açılamaz. Uygulama çökmek yerine bir **kurtarma ekranı** gösterir (tekrar dene / yazarak onaylanan sıfırlama); anahtarsız veri geri getirilemez, yedek bu yüzden şart. Anahtar dosyası eksikken mevcut veritabanı için asla yeni anahtar üretilmez.
- Yedekleme manueldir; otomatik/zamanlanmış yedek yok. Dışa aktarma: Excel yerine CSV; PDF ve CSV dosyaları şifrelenmez.
- PDF, Android'in yerleşik çizicisiyle üretilir (A4, sistem yazı tipi); sayfa görünümü cihazda elle kontrol edilmelidir.
- Ajanda notu hatırlatmaları WorkManager ile planlanır; pil tasarrufu nedeniyle birkaç dakika gecikebilir (tam zamanlı alarm kullanılmaz). Telefon uzun süre kapalıysa 24 saatten eski not hatırlatmaları gönderilmez.
- Hatırlatmalar yaklaşık 09:00'da, Android pil tasarrufuna göre gecikebilir; yalnızca seçilen "tam gün"ler bildirilir (telefon bir gün kapalıysa o günün 7/3/1 hatırlatması sonradan gönderilmez).
- Uygulama açıkken gece yarısı geçerse ekranlar, uygulama arka plana alınıp tekrar açılınca güncellenir.
- Tutar girişinde `1.500` binlik ayraç, `1.5` ondalık olarak yorumlanır; `1.500,50` ve `1500,50` biçimleri güvenlidir.
- Veritabanı şeması sürüm 1'dedir; ilk şema değişikliğinde migration yazılması gerekir (`MigrationsTest` unutulmasını engeller).
- Keystore ve SQLCipher için cihaz testleri vardır (elle çalıştırılan workflow); arayüz (Compose), biyometrik ve bildirim akışlarının otomatik testi yoktur, bunlar elle denenmelidir.
- Tek modüllü proje, paket bazlı katmanlama (`data/`, `domain/`, `ui/`, `security/`, `backup/`, `report/`, `reminder/`).
- Kütüphane sürümleri `gradle/libs.versions.toml` içindedir; belirli aralıklarla güncellenmesi önerilir.

## Sorun giderme
| Belirti | Ne yapmalı |
|---|---|
| Actions kırmızı, APK var | Testlerden biri kırık; "Başarısız testleri özetle" çıktısına bakın |
| `e:` ile başlayan satırlar | Derleme hatası; satırları paylaşın |
| "Uygulama yüklenemedi / paket çakışması" | Eski sürüm farklı anahtarla imzalı; kaldırıp yükleyin, sonra sabit anahtarı tanımlayın |
| Bildirim gelmiyor | Ayarlar → Bildirim ayarları ve Pil optimizasyonu kısayollarını kullanın |
| Release APK çöküyor | `minify=false` ile tekrar derleyin |
