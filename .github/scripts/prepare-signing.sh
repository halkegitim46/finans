#!/usr/bin/env bash
# İmza anahtarını GitHub Secrets'tan hazırlar ve sonraki adımlar için ortam değişkenlerini yazar.
#
# Beklenen ortam değişkenleri (workflow secrets'tan doldurur; eski KFT_ önekli adlar da workflow'da yedek olarak okunur):
#   KEYSTORE_BASE64    anahtar deposunun (.jks) base64 çıktısı (tek satır)
#   KEYSTORE_PASSWORD  depo parolası
#   KEY_ALIAS          anahtarın takma adı
#   KEY_PASSWORD       anahtar parolası
# SIGNING_STRICT=true ise eksik/hatalı imza bilgisi işi durdurur; aksi halde yalnızca uyarı verilir.
#
# Yazılan çıktı (GITHUB_ENV): KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD  (app/build.gradle.kts bunları okur)
set -euo pipefail

strict="${SIGNING_STRICT:-false}"

fail_or_warn() {
  if [ "$strict" = "true" ]; then
    echo "::error::$1"
    exit 1
  fi
  echo "::warning::$1 APK, rastgele debug anahtarıyla imzalanacak (güncellemek için eski sürümü kaldırmanız gerekir)."
  exit 0
}

# Hangi değerler var? (değerler ASLA yazdırılmaz, yalnızca var/yok)
missing=()
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -n "${!name:-}" ]; then
    echo "  [var] $name"
  else
    echo "  [YOK] $name"
    missing+=("$name")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  fail_or_warn "İmza secret'ları eksik: ${missing[*]}. GitHub → Settings → Secrets and variables → Actions altında KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD tanımlı olmalı (eski KFT_ önekli adlar da kabul edilir)."
fi

keystore="${RUNNER_TEMP:-/tmp}/release.jks"
if ! printf '%s' "$KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$keystore" 2>/dev/null || [ ! -s "$keystore" ]; then
  fail_or_warn "KEYSTORE_BASE64 çözülemedi: değer .jks dosyasının base64 çıktısı olmalı (tek satır, boşluksuz; 'base64 -w0 dosya.jks')."
fi

if ! keytool -list -keystore "$keystore" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1; then
  fail_or_warn "Anahtar deposu açılamadı veya '$KEY_ALIAS' takma adı yok: KEYSTORE_PASSWORD ya da KEY_ALIAS hatalı olabilir."
fi

{
  echo "KEYSTORE_PATH=$keystore"
  echo "KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
  echo "KEY_ALIAS=$KEY_ALIAS"
  echo "KEY_PASSWORD=$KEY_PASSWORD"
} >> "$GITHUB_ENV"
echo "İmza anahtarı hazır (takma ad: $KEY_ALIAS)."
