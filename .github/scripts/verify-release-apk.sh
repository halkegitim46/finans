#!/usr/bin/env bash
# Release APK'nın imzasını doğrular, sürüm adıyla yeniden adlandırır ve çıktıları yazar.
# Girdi (ortam): VERSION_NAME; EXPECT_OWN_KEY=true ise debug anahtarıyla imzalı APK hata sayılır.
# Test için: APK_DIR, APKSIGNER değişkenleri geçersiz kılınabilir.
set -uo pipefail
dir="${APK_DIR:-app/build/outputs/apk/release}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
output="${GITHUB_OUTPUT:-/dev/null}"
APKSIGNER="${APKSIGNER:-$(ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)}"
if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "::error::apksigner bulunamadı (ANDROID_HOME=${ANDROID_HOME:-tanımsız})."
  exit 1
fi
echo "apksigner: $APKSIGNER"

shopt -s nullglob
apks=("$dir"/*.apk)
if [ "${#apks[@]}" -eq 0 ]; then
  echo "::error::Release APK bulunamadı ($dir boş veya yok). Klasör içeriği:"
  ls -la "$dir" 2>/dev/null || echo "(klasör yok)"
  exit 1
fi

: > imza.txt
failed=0
for f in "${apks[@]}"; do
  echo "=== $f ==="
  out="$("$APKSIGNER" verify --verbose --print-certs "$f" 2>&1)"
  rc=$?
  printf '%s\n' "$out" | tee -a imza.txt
  if [ "$rc" -ne 0 ]; then
    failed=1
    echo "::error::apksigner doğrulaması başarısız: $f (kod $rc)"
  fi
done
[ "$failed" -eq 0 ] || exit 1

if grep -qi "Android Debug" imza.txt; then
  if [ "${EXPECT_OWN_KEY:-false}" = "true" ]; then
    echo "::error::APK debug anahtarıyla imzalanmış ama imza secret'ları tanımlı: app/build.gradle.kts signingConfigs bloğunu ve KEYSTORE_* ortam değişkenlerini kontrol edin."
    exit 1
  fi
  echo "::warning::Release APK debug anahtarıyla imzalandı (kendi anahtarınızla değil)."
else
  echo "### ✅ Release APK kendi anahtarınızla imzalı" >> "$summary"
fi
{
  grep -E "certificate DN:" imza.txt | sed -E 's/^[[:space:]]*/- /' || true
  grep -E "certificate SHA-256 digest:" imza.txt | sed -E 's/^[[:space:]]*/- /' || true
} >> "$summary"

name="FinansTakip_V${VERSION_NAME:?VERSION_NAME gerekli}.apk"
mv "${apks[0]}" "$dir/$name"
echo "APK_PATH=$dir/$name" >> "$output"
echo "APK_NAME=$name" >> "$output"
echo "Release APK hazır: $dir/$name"
