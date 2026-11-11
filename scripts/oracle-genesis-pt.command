#!/bin/bash
# GENESIS em português por omissão: exporta de novo, instala nos telemóveis ligados (Oracle + jogo),
# abre o Oracle na aba Jogo, carrega em "Jogar GENESIS" e tira capturas.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/genesis/instalar2.log) 2>&1
echo "=== GENESIS PT — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
G="$ROOT/tools/godot/Godot.app/Contents/MacOS/Godot"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export GODOT_ANDROID_KEYSTORE_DEBUG_PATH="$HOME/.android/debug.keystore"
export GODOT_ANDROID_KEYSTORE_DEBUG_USER=androiddebugkey GODOT_ANDROID_KEYSTORE_DEBUG_PASSWORD=android
"$G" --headless --path genesis --export-debug "Android" "$ROOT/dist/GENESIS-0.1.0.apk" > logs/genesis/export2.log 2>&1
ls -lh dist/GENESIS-0.1.0.apk
DONE=" "
for round in $(seq 1 160); do
  for S in 007448c502104212 ZY22F2BR85 ZY22HT9GCS; do
    case "$DONE" in *" $S "*) continue;; esac
    [ "$(adb devices | awk -v s=$S '$1==s{print $2}')" = "device" ] || continue
    adb -s $S shell input keyevent KEYCODE_WAKEUP
    if adb -s $S shell dumpsys window | grep -m1 mCurrentFocus | grep -qiE "NotificationShade|Keyguard|Bouncer"; then
      [ $((round % 4)) = 1 ] && echo "$S ligado mas bloqueado — desbloqueie o ecrã"; continue
    fi
    export ANDROID_SERIAL=$S
    M=$(adb shell getprop ro.product.model | tr -d '\r')
    echo "--- $M"
    adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -E "Success|Failure|failed|INSTALL" 
    adb install -r dist/GENESIS-0.1.0.apk 2>&1 | grep -E "Success|Failure|failed|INSTALL"
    adb shell dumpsys package com.oracleos.app | grep -m1 lastUpdateTime
    python3 - <<'PY'
import subprocess, re, time, os
s=os.environ["ANDROID_SERIAL"]
def adb(*a): return subprocess.run(["adb","-s",s,*a],capture_output=True,text=True).stdout
def tap(pattern, swipes=0):
    for i in range(swipes+1):
        adb("shell","uiautomator","dump","/sdcard/ui.xml"); x=adb("shell","cat","/sdcard/ui.xml")
        for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
            if re.search(pattern, m.group(1), re.I):
                x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); print("toque:",m.group(1)[:40]); return True
        adb("shell","input","swipe","500","1400","500","700","400"); time.sleep(1)
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/genesis/{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","com.oracleos.genesis")
adb("shell","am","start","-S","-W","-n","com.oracleos.app/.MainActivity"); time.sleep(16)
tap(r"^\s*(Pular|Skip|PULAR|SKIP)\s*›?\s*$"); time.sleep(10)
if tap(r"^\s*(JOGO|Jogo|GAME|Game)\s*$"):
    time.sleep(3); shot("2-aba-jogo")
    if tap(r"(Jogar GENESIS|Play GENESIS)", swipes=1):
        time.sleep(9); shot("3-genesis")
adb("shell","am","force-stop","com.oracleos.genesis")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
    adb logcat -d -v time | grep -E "FATAL|SCRIPT ERROR|USER ERROR" | tail -5
    DONE="$DONE$S "
  done
  [ $(echo $DONE | wc -w) -ge 3 ] && break
  sleep 15
done
echo "Feito em: $DONE"
echo "=== Fim $(date '+%F %T'). Pode fechar esta janela. ==="
