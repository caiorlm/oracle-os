#!/bin/bash
# Oracle OS — lista, em cada telemóvel ligado, as apps de rádio FM de fábrica e se o aparelho
# declara recetor de rádio. Só lê; não muda nada. Resultado em logs/instalar/radio-pacotes.txt
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
OUT=logs/instalar/radio-pacotes.txt; : > "$OUT"
for S in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  echo "===== $(adb -s $S shell getprop ro.product.model | tr -d '\r') ($S) · $(adb -s $S shell getprop ro.board.platform | tr -d '\r') =====" >> "$OUT"
  adb -s $S shell pm list packages | grep -iE "fm|radio" >> "$OUT"
  adb -s $S shell pm list features | grep -iE "radio|fm" >> "$OUT"
  for p in $(adb -s $S shell pm list packages | grep -iE "fm|radio" | sed 's/package://' | tr -d '\r'); do
    echo "-- $p" >> "$OUT"; adb -s $S shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$p" 2>/dev/null | tail -1 >> "$OUT"
  done
done
cat "$OUT"
echo "=== Rádio verificado. Pode fechar esta janela. ==="
