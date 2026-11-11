#!/bin/bash
# Oracle OS — abre o app no telemóvel, vai ao mapa e ao acervo e guarda capturas em logs/instalar/check-*.png
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
: > logs/instalar/teste.log
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL" ORACLE_SHOT_PREFIX="$SERIAL-"
  echo "===== $(adb shell getprop ro.product.model | tr -d '\r') ($SERIAL) =====" | tee -a logs/instalar/teste.log
  python3 scripts/phone_check.py 2>&1 | tee -a logs/instalar/teste.log
  adb logcat -d -v time 2>/dev/null | grep -E "com\.oracleos|AndroidRuntime|FATAL|maplibre|Mbgl" | tail -150 > "logs/instalar/teste-logcat-$SERIAL.txt"
done
echo "=== Teste concluído $(date '+%F %T'). Pode fechar esta janela. ===" | tee -a logs/instalar/teste.log
