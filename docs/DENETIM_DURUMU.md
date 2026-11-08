# Kod denetim raporu: uygulama durumu

Kaynak: `KISISelFinansTakip_Kod_Denetim_Raporu.md` (not: yüklenen dosyanın son bölümü yarım kesilmişti; okunabilen maddeler değerlendirildi).

| Madde | Durum | Açıklama |
|---|---|---|
| Tekrar hesabında 1000 dönem sınırı güncel dönemleri atlatabilir | **Uygulandı** | Başlangıç indeksi doğrudan hesaplanıyor; testler eklendi |
| Anahtar kaybında kurtarma yok / yeni anahtar üretme riski | **Uygulandı** | Anahtar korunur, sağlık denetimi + kurtarma ekranı, dayanıklı anahtar yazımı, testler |
| Yedek içeriği finansal kurallara göre doğrulanmıyor | **Uygulandı** | `BackupSnapshotValidator` (önizleme ve geri yüklemede), gerçek veriyle yanlış-pozitif testi |
| Gradle wrapper depoda yok | **Hazır, bir kez çalıştırılmalı** | "Proje dosyalarını tamamla" workflow'u wrapper + şemaları depoya işler (bu ortamda ağ yok, jar üretilemedi) |
| Migration testi zayıf / şema dosyaları yok | **Uygulandı** | Zincir testi düzeltildi, `SchemaGuardTest`, şemalar workflow ile depoya |
| İlk açılışta ana iş parçacığında taşıma (`runBlocking`) | **Kısmen** | Artık IO iş parçacığında ve yükleme ekranıyla; taşıma kodu hâlâ tek seferlik eşzamanlı (eski veri küçük) |
| Dosya seçici kilit istisnası geniş | **Uygulandı** | Sonuç geri çağrısıyla biter, en çok 3 dk |
| CSV/PDF için açık uyarı | **Kısmen (bilinçli)** | CSV'de uyarı var. PDF'te kullanıcı tercihiyle uyarı gösterilmiyor ("PDF'te gizliliğe gerek yok") |
| Cihaz (instrumented) testleri | **Eklendi (doğrulanmadı)** | SQLCipher + Keystore testleri, elle çalışan emülatör workflow'u; ilk çalıştırmada ayar gerektirebilir |
| Kapsam netliği | **Uygulandı** | README'de kapsam cümlesi (muhasebe yazılımı değil) |
| `app_settings` kullanılmıyor | **Belgelendi** | Şemada ve yedekte tutuluyor, ayrılmış alan olarak MIMARI'de açıklandı |
| Büyük Compose dosyalarının bölünmesi | **Ertelendi** | İşlev riski yok; yeni özellikle birlikte ele alınabilir |
