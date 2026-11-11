#!/bin/bash
# Oracle OS — o assistente responde "onde fica o hospital mais próximo?" com o índice offline e o GPS.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar
exec > >(tee -a logs/instalar/chat-locais.log) 2>&1
echo "=== Chat + locais — $(date '+%F %T') ==="
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong|Could not" -A2 logs/instalar/gradle.log | head -40
  cp logs/instalar/gradle.log logs/instalar/gradle-chat-locais-falhou.log
  cp app/src/main/java/com/oracleos/app/OracleControllerImpl.kt companion/fontes/OracleControllerImpl-falhou.kt
  tar xf companion/fontes/src-antes-locais.tar app/src/main/java/com/oracleos/app/OracleControllerImpl.kt
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
fi
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL"
  python3 - <<'PY'
import subprocess, re, time, os
s=os.environ["ANDROID_SERIAL"]
def adb(*a): return subprocess.run(["adb","-s",s,*a],capture_output=True,text=True).stdout
def tap(pattern, swipes=0):
    for i in range(swipes+1):
        adb("shell","uiautomator","dump","/sdcard/ui.xml"); x=adb("shell","cat","/sdcard/ui.xml")
        for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
            if re.search(pattern, m.group(1), re.I):
                x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); print("toque:",m.group(1)); return True
        adb("shell","input","swipe","540","1600","540","900","400"); time.sleep(1)
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/chatlocais-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity"); time.sleep(25)
tap(r"^\s*(IA|AI)\s*$"); time.sleep(3)
tap(r"Pesquisar acervo|Search library")
adb("shell","uiautomator","dump","/sdcard/ui.xml"); x=adb("shell","cat","/sdcard/ui.xml")
m=re.search(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x) or re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*class="android.widget.EditText"', x)
if m:
    x1,y1,x2,y2=map(int,m.groups()); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); time.sleep(1)
    adb("shell","input","text","onde%sfica%so%shospital%smais%sproximo"); time.sleep(1)
    adb("shell","input","keyevent","KEYCODE_ESCAPE"); time.sleep(1)
    tap(r"^\s*(Buscar|Search)\s*$"); time.sleep(15); shot("1")
else: print("sem campo de texto")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
done
echo "=== Chat + locais terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
