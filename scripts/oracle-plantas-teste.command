#!/bin/bash
# Fecha o seletor de fotos, recompila e testa a identificação de plantas com fotos de exemplo guardadas
# na pasta do próprio Oracle (não usa a galeria). Remove as fotos de teste postas antes na galeria.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/plantas-teste.log) 2>&1
echo "=== Teste plantas — $(date '+%F %T') ==="
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  adb -s $SERIAL shell input keyevent KEYCODE_BACK; sleep 1; adb -s $SERIAL shell input keyevent KEYCODE_BACK
  adb -s $SERIAL shell rm -rf /sdcard/Pictures/OracleTeste
  adb -s $SERIAL shell content delete --uri content://media/external/images/media --where "\"_data LIKE '%OracleTeste%'\"" 2>/dev/null
done
for i in $(seq 1 60); do
  if pgrep -f "oracle-acervo-med.command" >/dev/null && ! grep -q "Enciclopédias offline" logs/dados/acervo-med.log; then
    [ $i = 1 ] && echo "a aguardar o acervo médico…"; sleep 20; else break; fi
done
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
grep -E "^e: " logs/instalar/gradle.log | head -20
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL"
  n=0
  for img in downloads/plants/test-*; do
    n=$((n+1))
    adb shell mkdir -p /sdcard/Android/data/com.oracleos.app/files/test
    adb push "$img" /sdcard/Android/data/com.oracleos.app/files/test/plant.jpg >/dev/null
    adb shell am force-stop com.oracleos.app
    adb shell am start -n com.oracleos.app/.MainActivity --es oracle_plant_test 1 >/dev/null
    sleep 30
    W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); w=${W%x*}; h=${W#*x}
    adb exec-out screencap -p > "logs/instalar/plantas-teste-$SERIAL-$n-a.png"
    adb shell input swipe $((w/2)) $((h*8/10)) $((w/2)) $((h*3/10)) 500; sleep 2
    adb exec-out screencap -p > "logs/instalar/plantas-teste-$SERIAL-$n-b.png"
    adb shell input swipe $((w/2)) $((h*8/10)) $((w/2)) $((h*3/10)) 500; sleep 2
    adb exec-out screencap -p > "logs/instalar/plantas-teste-$SERIAL-$n-c.png"
  done
  adb shell rm -rf /sdcard/Android/data/com.oracleos.app/files/test
  adb shell input keyevent KEYCODE_HOME
  adb logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL" | tail -20 > "logs/instalar/plantas-logcat-$SERIAL.txt"
done
echo "=== teste terminado $(date '+%F %T') ==="
