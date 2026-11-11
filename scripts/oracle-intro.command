#!/bin/bash
# Oracle OS — introdução cinematográfica 3D: compila (repõe a versão anterior se falhar), instala nos
# telemóveis ligados e filma a introdução em cada um (capturas + FPS no logcat).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar/intro
exec > >(tee -a logs/instalar/intro.log) 2>&1
echo "=== Introdução — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong" -A2 logs/instalar/gradle.log | head -40
  cp logs/instalar/gradle.log logs/instalar/gradle-intro-falhou.log
  echo "INTRO_FALHOU" > logs/instalar/intro-estado.txt
  mkdir -p companion/fontes/intro-falhou && cp -R app/src/main companion/fontes/intro-falhou/main-$(date +%H%M%S)
  mkdir -p _to_delete/intro-$(date +%H%M%S) && mv app/src/main/java/com/oracleos/app/feature/intro _to_delete/intro-$(date +%H%M%S)/ 2>/dev/null
  tar xf companion/fontes/src-antes-intro.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  exit 1
fi
echo "INTRO_OK $code" > logs/instalar/intro-estado.txt
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
for S in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  adb -s $S shell input keyevent KEYCODE_WAKEUP
  adb -s $S shell am force-stop com.oracleos.app
  adb -s $S logcat -c
  adb -s $S shell am start -n com.oracleos.app/.MainActivity --ez oracle_intro true >/dev/null
  start=$(python3 -c "import time;print(time.time())")
  for n in $(seq 1 14); do
    el=$(python3 -c "import time;print(f'{time.time()-$start:04.1f}')")
    adb -s $S exec-out screencap -p > "logs/instalar/intro/$S-$n-${el}s.png"
  done
  sleep 3
  adb -s $S exec-out screencap -p > "logs/instalar/intro/$S-99-depois.png"
  adb -s $S logcat -d -v time | grep -E "OracleIntro|FATAL|AndroidRuntime: " | tail -10 | tee "logs/instalar/intro/$S-logcat.txt"
done
echo "=== Introdução terminada $(date '+%F %T'). Pode fechar esta janela. ==="
