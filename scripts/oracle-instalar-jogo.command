#!/bin/bash
# Instala o Oracle (com a aba Jogo) e o GENESIS em cada telemóvel assim que aparecer ligado e
# desbloqueado (espera até 60 min), abre o jogo e tira uma captura.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/genesis/instalar.log) 2>&1
echo "=== Instalar jogo — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
DONE=" "
for round in $(seq 1 240); do
  for S in 007448c502104212 ZY22F2BR85 ZY22HT9GCS; do
    case "$DONE" in *" $S "*) continue;; esac
    [ "$(adb devices | awk -v s=$S '$1==s{print $2}')" = "device" ] || continue
    adb -s $S shell input keyevent KEYCODE_WAKEUP
    if adb -s $S shell dumpsys window | grep -m1 mCurrentFocus | grep -qiE "NotificationShade|Keyguard|Bouncer"; then
      [ $((round % 4)) = 1 ] && echo "$S ligado mas bloqueado — desbloqueie o ecrã"; continue
    fi
    M=$(adb -s $S shell getprop ro.product.model | tr -d '\r')
    echo "--- $M"
    adb -s $S install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
    adb -s $S install -r dist/GENESIS-0.1.0.apk | tail -1
    adb -s $S logcat -c
    adb -s $S shell monkey -p com.oracleos.genesis -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 10
    adb -s $S exec-out screencap -p > "logs/genesis/$S-1-jogo.png"
    adb -s $S logcat -d -v time | grep -E "FATAL|AndroidRuntime: |USER ERROR|SCRIPT ERROR" | tail -6
    adb -s $S shell am force-stop com.oracleos.genesis
    DONE="$DONE$S "
  done
  [ $(echo $DONE | wc -w) -ge 3 ] && break
  sleep 15
done
echo "Instalado em: $DONE"
echo "=== Fim $(date '+%F %T'). Pode fechar esta janela. ==="
