#!/usr/bin/env bash
# app/build.gradle.kts içindeki versionCode / versionName satırlarını günceller ve DOĞRULAR.
# Girdi (ortam): VERSION_CODE, VERSION_NAME. İsteğe bağlı argüman: dosya yolu.
set -euo pipefail
: "${VERSION_CODE:?VERSION_CODE gerekli}"
: "${VERSION_NAME:?VERSION_NAME gerekli}"
file="${1:-app/build.gradle.kts}"
[ -f "$file" ] || { echo "::error::$file bulunamadı"; exit 1; }

cp "$file" "$file.orig"
sed -i "s/^\(\s*\)versionCode = .*/\1versionCode = ${VERSION_CODE}/" "$file"
sed -i "s/^\(\s*\)versionName = .*/\1versionName = \"${VERSION_NAME}\"/" "$file"

echo "::group::Güncel sürüm satırları"
grep -nE 'versionCode|versionName' "$file"
echo "::endgroup::"
grep -q "versionCode = ${VERSION_CODE}" "$file" || { echo "::error::versionCode güncellenemedi: $file içindeki satır biçimi 'versionCode = <sayı>' olmalı."; exit 1; }
grep -q "versionName = \"${VERSION_NAME}\"" "$file" || { echo "::error::versionName güncellenemedi: satır biçimi 'versionName = \"...\"' olmalı."; exit 1; }
diff -u "$file.orig" "$file" || true
rm -f "$file.orig"
