#!/bin/bash
# Oracle OS — instala a versão com a introdução em todos os telemóveis ligados e filma a introdução
# em cada um. Espera (até 20 min) que cada telemóvel esteja ligado E desbloqueado.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar/intro
exec > >(tee -a logs/instalar/intro-teste.log) 2>&1
echo "=== Teste da introdução — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
APK=app/build/outputs/apk/debug/app-debug.apk
DONE=" "
for round in $(seq 1 80); do
  for S in 007448c502104212 ZY22F2BR85 ZY22HT9GCS; do
    case "$DONE" in *" $S "*) continue;; esac
    [ "$(adb devices | awk -v s=$S '$1==s{print $2}')" = "device" ] || continue
    adb -s $S shell input keyevent KEYCODE_WAKEUP
    focus=$(adb -s $S shell dumpsys window | grep -m1 mCurrentFocus)
    if echo "$focus" | grep -qiE "NotificationShade|Keyguard|Bouncer|StatusBar"; then
      [ $((round % 4)) = 1 ] && echo "$S ligado mas bloqueado — desbloqueie o ecrã"
      continue
    fi
    MODEL=$(adb -s $S shell getprop ro.product.model | tr -d '\r')
    echo "--- $MODEL: a instalar e filmar"
    adb -s $S install -r "$APK" | tail -1
    adb -s $S shell am force-stop com.oracleos.app
    adb -s $S logcat -c
    adb -s $S shell am start -n com.oracleos.app/.MainActivity --ez oracle_intro true >/dev/null
    start=$(python3 -c "import time;print(time.time())")
    for n in $(seq 1 24); do
      el=$(python3 -c "import time;print(f'{time.time()-$start:04.1f}')")
      adb -s $S exec-out screencap -p > "logs/instalar/intro/$S-$n-${el}s.png"
    done
    sleep 3
    adb -s $S exec-out screencap -p > "logs/instalar/intro/$S-99-depois.png"
    adb -s $S logcat -d -v time | grep -E "OracleIntro|FATAL|AndroidRuntime: " | tail -10 | tee "logs/instalar/intro/$S-logcat.txt"
    DONE="$DONE$S "
  done
  [ $(echo $DONE | wc -w) -ge 3 ] && break
  sleep 15
done
echo "Filmados: $DONE"
echo "=== Teste da introdução terminado $(date '+%F %T'). Pode fechar esta janela. ==="
