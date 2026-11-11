#!/bin/bash
# Oracle OS — descarrega o leitor Kiwix (ARM64, do GitHub oficial kiwix/kiwix-android) e instala-o
# nos telemóveis ligados por USB. Retoma se a ligação cair.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
exec > >(tee -a logs/dados/kiwix-apk.log) 2>&1
echo "=== Kiwix APK — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
URL=https://github.com/kiwix/kiwix-android/releases/download/v3.14.1/v3.14.1-arm64-v8a-standalone.apk
OUT=downloads/library/apps/kiwix-v3.14.1-arm64-v8a-standalone.apk
SIZE=70366062
mkdir -p downloads/library/apps
until [ -f "$OUT" ]; do
  curl -fL -C - --retry 5 -o "$OUT.part" "$URL"
  if [ "$(stat -f %z "$OUT.part" 2>/dev/null)" = "$SIZE" ]; then mv "$OUT.part" "$OUT"; else sleep 10; fi
done
ls -l "$OUT"
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  echo "a instalar no $SERIAL"; adb -s "$SERIAL" install -r "$OUT"
done
echo "=== Kiwix APK concluído $(date '+%F %T'). Pode fechar esta janela. ==="
