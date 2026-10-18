#!/bin/sh
# Copies the standalone SignedVault page into the Android app's assets,
# pointing its libraries at bundled copies so the app works offline.
set -e
cd "$(dirname "$0")/.."
sh build.sh
A=android/app/src/main/assets
rm -rf "$A" && mkdir -p "$A/lib"
cp android/web-lib/*.js "$A/lib/"
sed -e 's#https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.min.js#lib/qrcode.min.js#' \
    -e 's#https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js#lib/jspdf.umd.min.js#' \
    -e 's#https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js#lib/jsQR.js#' \
    index.html > "$A/index.html"
if grep -q 'cdnjs.cloudflare.com\|cdn.jsdelivr.net' "$A/index.html"; then echo "CDN URL left in bundled page" >&2; exit 1; fi
echo "Bundled web app into $A"
