#!/bin/bash
# Oracle OS — leitor de enciclopédias integrado (ZIM, motor libkiwix), sem Kiwix nem permissões.
# Compila e atualiza os telemóveis ligados; se não compilar, repõe a versão anterior. Depois testa.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/zim-leitor.log) 2>&1
echo "=== Leitor ZIM — $(date '+%F %T') ==="
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong|Could not" -A2 logs/instalar/gradle.log | head -40
  cp logs/instalar/gradle.log logs/instalar/gradle-zim-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/zim-falhou && cp -R app/src/main companion/fontes/zim-falhou/main-$(date +%H%M%S)
  rm -rf app/src/main/java/com/oracleos/app/feature/zim
  tar xf companion/fontes/src-antes-zim.tar
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
def shot(n): open(f"logs/instalar/zim-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity"); time.sleep(14)
tap(r"^\s*Acervo\s*$"); time.sleep(4)
adb("shell","input","swipe","540","1600","540","700","500"); time.sleep(2)
if tap(r"^\s*Abrir\s*$", swipes=4):
    time.sleep(12); shot("1-leitor")
    if tap(r"^\s*Pesquisar\s*$"):
        time.sleep(1); tap(r"Pesquisar nesta coleção"); time.sleep(1)
        adb("shell","input","text","water"); time.sleep(6); shot("2-pesquisa")
        if tap(r"[Ww]ater", swipes=0): time.sleep(6); shot("3-artigo")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
  adb logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL|libzim|kiwix|UnsatisfiedLink" | tail -40 > "logs/instalar/zim-logcat-$SERIAL.txt"
done
echo "=== Leitor ZIM terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
