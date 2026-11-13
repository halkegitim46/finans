# Kullanım Kılavuzu

## 1. İlk açılış
Uygulama ilk açıldığında bir **uygulama parolası** belirlersiniz (en az 8 karakter). Parola kurtarılamaz; unutursanız tek yol,
önceden aldığınız şifreli yedeği geri yüklemek veya verileri silip baştan başlamaktır. Android 13+ cihazlarda bildirim izni istenir;
fatura hatırlatmaları için "İzin ver" deyin.

## 2. Kişiler
**Kişiler** sekmesinden "Kişi ekle". İsteğe bağlı başlangıç bakiyesi girebilirsiniz: "Bana borçlu" (alacak) veya "Ben borçluyum".
Kişi detayında kalan alacak/borç, toplam alacak, toplam borç, tahsil edilen ve ödenen tutarlar ile işlem geçmişi görünür.
Kişiyi silemezsiniz; **arşivlersiniz** (menü → Arşivle). Kayıtlar korunur, kişi listeden kalkar, "Arşivlenenleri göster" ile görürsünüz.

## 3. Alacak ve borç işlemleri (kişi detayında veya Ana Sayfa'daki hızlı işlemlerden)
| İşlem | Anlamı |
|---|---|
| Borç ver | Kişiye para verdiniz; alacağınız artar |
| Borç al | Kişiden para aldınız; borcunuz artar |
| Kişi adına ödeme | Kişinin bir giderini siz ödediniz; alacağınız artar |
| Tahsilat al | Kişi size ödedi; alacak azalır (kısmi olabilir) |
| Borç öde | Siz kişiye ödediniz; borç azalır |
| Alacağı / Borcu kapat | Kalan tutarın tamamını tek işlemde kapatır |

Tahsilat veya ödeme, kalan tutarı **aşamaz**. Hatalı bir kaydı silemezsiniz, **iptal edersiniz** (kayıt üstü çizili görünür, hesaba katılmaz).
Tutarı yanlış girdiyseniz iptal edip yeniden girin; açıklama, tarih ve vade sonradan düzenlenebilir.
Düğmeye çift dokunmak çift kayıt oluşturmaz.

## 4. Faturalar
**Faturalar → Fatura ekle**: ad, tür, ilgili kişi (veya "Kendim"), tutar (sabit veya değişken), tekrar (tek seferlik / aylık / yıllık / özel gün aralığı),
ilk son ödeme tarihi, hatırlatmalar. Her dönem ayrı kayıttır; ödeme yalnızca o dönemi kapatır.

**Örnek – dayının elektrik faturası (1.500 TL):**
1. Fatura ekle → "Dayım elektrik", kişi: Dayım, sabit tutar 1.500, aylık.
2. Fatura gelince dönemi açıp **Ödendi olarak işaretle** → 1.500 TL ödeme kaydı oluşur ve Dayım'ın alacağına eklenir.
3. Dayım 500 TL verince Dayım'ın sayfasında **Tahsilat al** → 500 TL → kalan alacak 1.000 TL.

Değişken tutarlı faturada fatura gelince dönemde **Tutarı gir**, sonra öde. Yanlışlıkla ödediyseniz **Ödemeyi geri al** (üzerine tahsilat yapılmışsa önce tahsilatı iptal edin).
Fatura tanımını düzenlemek yalnızca sonraki dönemleri etkiler; tekrar ve ilk vade sonradan değişmez. Fatura **arşivlenir**, silinmez.

## 5. Hatırlatmalar
Her faturada seçersiniz: 7 gün önce, 3 gün önce, 1 gün önce, vade günü ve gecikenler için aralık (her gün, 2-3 günde bir, haftada bir, kapalı).
Bildirimde fatura adı, tutar ve son ödeme tarihi vardır; kilit ekranında yalnızca "Fatura hatırlatması" görünür. Dokununca ilgili fatura açılır
(uygulama kilitliyse önce kilit açılır). Ödenmiş faturalar için bildirim gönderilmez. Bildirimler gelmezse Ayarlar → Bildirim ve Pil kısayollarını kullanın.

## 5b. Ajanda ve notlar
Ana Sayfa'daki **Ajanda** bölümünden veya "Ajanda notu" düğmesinden açılır. Ajanda; ödenmemiş faturalarınızı, vadesi olan borç/alacaklarınızı ve kendi notlarınızı **günlere göre** tek listede gösterir
(Geciken → Bugün → Yarın → sonraki günler; 30 günden ilerisi "daha sonraki kayıtlar" olarak sayılır).
- **Not ekle**: başlık, isteğe bağlı açıklama, tarih, **saat** (isteğe bağlı), ilgili kişi (isteğe bağlı) ve hatırlatma (hatırlatma yok / zamanında / 1 saat önce / 1 gün önce). Saat seçmezseniz hatırlatma o günün 09:00'unda yapılır.
- Notun yanındaki kutuyla **tamamlandı** işaretlersiniz (geri alınabilir); tamamlananlar ayrı bölümde durur ve hatırlatma göndermez. Notu silerseniz arşivlenir.
- Satıra dokununca fatura, kişi (borç/alacak) veya not düzenleme ekranı açılır.
- **Borç/alacak vade hatırlatmaları**: vadesi olan açık borç/alacaklar için vadeden 3 ve 1 gün önce, vade günü ve gecikince 3 günde bir bildirim gelir (Ayarlar → Bildirimler ve pil'den kapatılabilir). Faturadan doğan alacaklar ayrıca hatırlatılmaz; fatura hatırlatması geçerlidir.

## 6. Ana Sayfa ve İşlemler
Ana Sayfa: net bakiye, toplam alacak/borç, bu ay ödemeler ve tahsilatlar, geciken ve yaklaşan faturalar, son işlemler, hızlı işlemler.
**İşlemler**: tüm kişilerin defteri; arama, tür filtresi, iptal edilenleri gösterme.

## 7. Raporlar
Dönem seçin (bu ay, geçen ay, son 3 ay, bu yıl, geçen yıl, özel aralık). Sekmeler: **Özet** (karşılaştırma, aylık/yıllık, grafikler),
**Kişiler** (dönem başı/sonu bakiye ve hareketler), **Faturalar** (ödeme geçmişi), **Tahsilat**, **Bekleyen** (geciken ve ödenmemiş).
Sağ üstteki indirme düğmesi geçerli sekmeyi **PDF** (yazdırılabilir, sayfa numaralı) veya **CSV** (Excel'de açılır) olarak kaydeder.
**Kişiler** sekmesinde her kişinin kartında "Bu kişinin dökümünü PDF olarak kaydet" düğmesi vardır; üst menüden PDF seçerseniz tüm kişiler tek dosyada olur.
Dosya adı örneği: `KFT_Hesap_Cagla_Ozturk_2026-11-01_2026-11-30.pdf`. PDF ve CSV dosyaları **şifrelenmez**; paylaşırken dikkatli olun.

## 8. Güvenlik (Ayarlar)
Parolayı değiştirme, PIN, parmak izi/yüz, arka plana geçince kilitleme ve süresi, ekran görüntüsü engeli, "Şimdi kilitle".
5 hatalı denemeden sonra giriş geçici kilitlenir (30 sn, sonra katlanarak en çok 15 dk). Veritabanı AES-256 ile şifrelidir; hiçbir veri sunucuya gönderilmez.

## 9. Yedekleme ve geri yükleme (Ayarlar → Yedekleme)
- **Yedek al**: ayrı bir *yedek parolası* belirlersiniz (uygulama parolasından bağımsızdır; unutursanız yedek açılamaz). Dosyayı telefona veya Drive gibi bir yere kaydedersiniz.
- **Yedekten geri yükle**: dosyayı seçin → yedek parolası → özet (tarih, kayıt sayıları) → onay. Mevcut **tüm veriler silinip yedekle değiştirilir**. İşlem tek adımda yapılır; hata olursa eski verileriniz korunur.
- Düzenli yedek alın (örn. her ay sonu). Yedek dosyasını ve parolasını ayrı yerlerde saklayın.

## 10. Parolamı unuttum
Kilit ekranında "Parolamı unuttum" → verileri silin → yeni parola belirleyin → Ayarlar → Yedekleme → Yedekten geri yükle.
Yedeğiniz yoksa veriler geri getirilemez.
