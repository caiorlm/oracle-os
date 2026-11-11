#!/bin/bash
# Fecha o seletor de fotos sem escolher nada e testa as plantas com as fotos de exemplo do Oracle.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/plantas-teste.log) 2>&1
echo "=== Teste plantas 2 — $(date '+%F %T') ==="
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL"
  W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); w=${W%x*}; h=${W#*x}
  adb shell input keyevent KEYCODE_WAKEUP
  # Clear the selection (X at bottom-left of the picker), then close it.
  adb shell input tap $((w*12/100)) $((h*917/1000)); sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
  adb shell am force-stop com.google.android.photopicker 2>/dev/null
  adb shell am force-stop com.google.android.providers.media.module 2>/dev/null
  adb shell am force-stop com.android.providers.media.module 2>/dev/null
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell dumpsys activity activities | grep -m3 "mResumedActivity\|topResumedActivity"
  n=0
  for img in downloads/plants/test-*; do
    n=$((n+1))
    adb shell mkdir -p /sdcard/Android/data/com.oracleos.app/files/test
    adb push "$img" /sdcard/Android/data/com.oracleos.app/files/test/plant.jpg >/dev/null
    adb shell am force-stop com.google.android.photopicker
    adb shell am force-stop com.oracleos.app
    adb shell am start -S -W -n com.oracleos.app/.MainActivity --es oracle_plant_test 1 >/dev/null
    sleep 30
    top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
    echo "$top"
    case "$top" in *photopicker*) echo "seletor ainda aberto: sem capturas"; continue;; esac
    adb exec-out screencap -p > "logs/instalar/plantas-ok-$SERIAL-$n-a.png"
    adb shell input swipe $((w/2)) $((h*8/10)) $((w/2)) $((h*3/10)) 500; sleep 2
    adb exec-out screencap -p > "logs/instalar/plantas-ok-$SERIAL-$n-b.png"
    adb shell input swipe $((w/2)) $((h*8/10)) $((w/2)) $((h*3/10)) 500; sleep 2
    adb exec-out screencap -p > "logs/instalar/plantas-ok-$SERIAL-$n-c.png"
  done
  adb shell rm -rf /sdcard/Android/data/com.oracleos.app/files/test
  adb shell input keyevent KEYCODE_HOME
done
echo "=== teste 2 terminado $(date '+%F %T') ==="
