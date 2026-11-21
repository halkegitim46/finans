# Sürüm uyumluluğu rehberi

Android derlemesinde en sık kırılma nedeni **birbirine bağlı sürümlerin ayrı ayrı yükseltilmesidir**. Bu belge hangi sürümlerin birlikte değiştiğini,
uyumsuzluğun hangi hata iletisiyle kendini gösterdiğini ve güvenle nasıl yükseltileceğini anlatır.
CI'daki **preflight → "Sürüm uyumluluğu"** adımı bu kuralların çoğunu derlemeden ÖNCE (10 saniyede, nedeniyle birlikte) denetler.

## Şu an doğrulanmış küme (CI'da 369 birim test geçti, imzalı release derlendi)
| Bileşen | Sürüm |
|---|---|
| Gradle (iş akışı `GRADLE_VERSION`) | 8.9 |
| Android Gradle Plugin (AGP) | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Hilt / Dagger | 2.52 |
| Room | 2.6.1 |
| Compose BOM | 2024.12.01 |
| WorkManager | 2.10.0 |
| compileSdk / targetSdk | 35 / 35 |

## Birlikte değişen gruplar (tek PR/commit'te yükseltin)
1. **Kotlin + KSP**: KSP sürümü `<kotlin>-<ksp>` biçimindedir; ilk bölüm Kotlin sürümüyle AYNI olmalıdır.
2. **Kotlin + Hilt + Room**: Kotlin 2.2.x için Hilt ≥ 2.57; Kotlin 2.x/KSP2 için Room ≥ 2.6.1.
3. **AGP + Gradle**: AGP 8.7 → Gradle ≥ 8.9; 8.8 → 8.10.2; 8.9/8.10 → 8.11.1; 8.11–8.13 → 8.13; 9.x → 9.1+. (`GRADLE_VERSION` iş akışında tek yerde.)
4. **compileSdk + AGP + androidx**: yeni androidx sürümleri (core-ktx ≥ 1.17, activity ≥ 1.11 …) compileSdk 36 ister; compileSdk 36 için AGP ≥ 8.9.1 gerekir.

## Hata iletisi → neden → çözüm
| İleti (özet) | Neden | Çözüm |
|---|---|---|
| `ksp-X is too old / too new for kotlin-Y` | KSP, Kotlin ile eşleşmiyor | `ksp` sürümünü `<kotlin>-<…>` biçiminde Kotlin'e eşitleyin |
| `Provided Metadata instance has version 2.x, while maximum supported version is 2.y` | Hilt/Dagger/Room, Kotlin'den eski | Hilt ≥ 2.57 (Kotlin 2.2), Room ≥ 2.6.1 |
| `requires libraries and applications that depend on it to compile against version 36 or later` | Bir androidx sürümü compileSdk 36 ister | compileSdk + AGP + Gradle'ı birlikte yükseltin ya da kütüphaneyi geri alın |
| `Minimum supported Gradle version is X` | AGP, Gradle'dan yeni | `GRADLE_VERSION` değerini yükseltin |
| `Could not find com.google.dagger:...:X` | Sürüm yok / yazım hatası | `libs.versions.toml` değerini düzeltin |

## Güvenli yükseltme yöntemi
1. Yükseltilecek GRUBU seçin (yukarıdaki dört gruptan biri); aynı anda yalnızca bir grup.
2. `gradle/libs.versions.toml` (+ gerekiyorsa `GRADLE_VERSION`, `compileSdk`) değiştirin, ayrı bir dalda push edin.
3. Actions'ta preflight uyumluluk adımı ve testler yeşil olmalı; release APK derlenmeli.
4. Telefonda release APK'yı deneyin (açılış, veritabanı, bildirim, yedek/geri yükleme).
5. Ancak sonra ana dala birleştirin. Sorun çıkarsa bilinen-iyi kümeye (yukarıdaki tablo) dönün.

## Dependabot
`.github/dependabot.yml` çekirdek grubu (Kotlin, KSP, AGP, Hilt, Room) otomatik PR'dan hariç tutar: bunlar tek tek değil, birlikte ve elle yükseltilir.
