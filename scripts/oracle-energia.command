#!/bin/bash
# Oracle OS — Poupança extrema de energia + Pacote de impressão. Compila (repõe a versão anterior se
# falhar), instala nos telemóveis com os PDFs de impressão, e testa com capturas de ecrã.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar
exec > >(tee -a logs/instalar/energia.log) 2>&1
echo "=== Energia + impressão — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong" -A2 logs/instalar/gradle.log | head -30
  cp logs/instalar/gradle.log logs/instalar/gradle-energia-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/energia-falhou && cp -R app/src/main companion/fontes/energia-falhou/main-$(date +%H%M%S)
  mkdir -p _to_delete && mv app/src/main/java/com/oracleos/app/ui/PowerSaver.kt app/src/main/java/com/oracleos/app/feature/print _to_delete/ 2>/dev/null
  tar xf companion/fontes/src-antes-energia.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
  echo "ENERGIA_FALHOU" > logs/instalar/energia-estado.txt
else
  echo "ENERGIA_OK $code" > logs/instalar/energia-estado.txt
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
                x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); print("toque:",m.group(1)[:50]); return True
        adb("shell","input","swipe","540","1500","540","800","400"); time.sleep(1)
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/energia-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
print("impressão no telefone:", adb("shell","ls","/sdcard/Android/data/com.oracleos.app/files/impressao").split())
adb("shell","am","force-stop","com.google.android.photopicker")
adb("shell","am","start","-S","-W","-n","com.oracleos.app/.MainActivity"); time.sleep(25)
tap(r"^\s*(Sistema|System)\s*$"); time.sleep(3); shot("1-sistema")
if tap(r"^\s*(Ligar poupança extrema|Turn on extreme power saving)\s*$", swipes=2):
    time.sleep(3); shot("2-ligada")
tap(r"^\s*(Início|Home)\s*$"); time.sleep(3); shot("3-inicio-preto")
if tap(r"^\s*(IMPRIMIR|PRINT)\s*$", swipes=3):
    time.sleep(4); shot("4-impressao")
    adb("shell","input","keyevent","KEYCODE_BACK"); time.sleep(2)
tap(r"^\s*(Sistema|System)\s*$"); time.sleep(3)
if tap(r"^\s*(Desligar poupança extrema|Turn off extreme power saving)\s*$", swipes=2):
    time.sleep(2); shot("5-desligada")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
  adb logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL" | tail -20 > "logs/instalar/energia-logcat-$SERIAL.txt"
done
echo "=== Energia + impressão terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
