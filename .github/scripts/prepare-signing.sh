#!/usr/bin/env bash
# İmza anahtarını GitHub Secrets'tan hazırlar.
#
# Girdi (ortam): KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (eski KFT_ önekli adlar workflow'da yedek okunur).
# SIGNING_STRICT=true  → eksik/hatalı bilgi işi durdurur; aksi halde yalnızca uyarı verir ve imza atlanır.
# Çıktı: GITHUB_ENV'e YALNIZCA KEYSTORE_PATH (gizli olmayan dosya yolu) yazılır; parolalar asla GITHUB_ENV'e yazılmaz,
#        Gradle adımına doğrudan `env:` ile verilir (üçüncü taraf adımlara sızmasın).
#        GITHUB_OUTPUT: ready=true|false
set -euo pipefail

strict="${SIGNING_STRICT:-false}"
out="${GITHUB_OUTPUT:-/dev/null}"
genv="${GITHUB_ENV:-/dev/null}"

fail_or_warn() {
  echo "ready=false" >> "$out"
  if [ "$strict" = "true" ]; then
    echo "::error::$1"
    exit 1
  fi
  echo "::warning::$1 İmza atlandı (APK rastgele debug anahtarıyla imzalanacak; güncellemek için eski sürümü kaldırmanız gerekir)."
  exit 0
}

missing=()
echo "::group::Secret durumu (yalnızca var/yok; değer yazılmaz)"
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -n "${!name:-}" ]; then
    echo "  [var] $name"
  else
    echo "  [YOK] $name"
    missing+=("$name")
  fi
done
echo "::endgroup::"
if [ "${#missing[@]}" -gt 0 ]; then
  fail_or_warn "İmza secret'ları eksik: ${missing[*]}. Settings → Secrets and variables → Actions altında KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD tanımlı olmalı (eski KFT_ önekli adlar da kabul edilir)."
fi

keystore="${RUNNER_TEMP:-/tmp}/release.jks"
if ! printf '%s' "$KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$keystore" 2>/dev/null || [ ! -s "$keystore" ]; then
  fail_or_warn "KEYSTORE_BASE64 çözülemedi: değer .jks dosyasının base64 çıktısı olmalı (tek satır, boşluksuz; 'base64 -w0 dosya.jks')."
fi
size="$(wc -c < "$keystore" | tr -d ' ')"
if [ "$size" -lt 100 ]; then
  fail_or_warn "Çözülen anahtar deposu çok küçük ($size bayt): KEYSTORE_BASE64 değeri bozuk olabilir."
fi

if ! keytool -list -keystore "$keystore" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1; then
  fail_or_warn "Anahtar deposu açılamadı veya '$KEY_ALIAS' takma adı yok: KEYSTORE_PASSWORD ya da KEY_ALIAS hatalı olabilir."
fi

echo "KEYSTORE_PATH=$keystore" >> "$genv"
echo "ready=true" >> "$out"
echo "İmza anahtarı hazır (takma ad: $KEY_ALIAS, $size bayt)."
