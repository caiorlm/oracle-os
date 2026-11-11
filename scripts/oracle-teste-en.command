#!/bin/bash
# Oracle OS — põe o app em inglês no(s) telemóvel(is) ligado(s), corre o teste visual e volta a PT-BR.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
exec > >(tee -a logs/instalar/teste-en.log) 2>&1
setlang() {
  adb shell am force-stop com.oracleos.app
  adb shell "run-as com.oracleos.app sh -c 'mkdir -p shared_prefs && echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><string name=\\\"lang\\\">$1</string></map>\" > shared_prefs/oracle_language.xml'"
}
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL"
  echo "===== $SERIAL ====="
  setlang en
  adb shell am start -n com.oracleos.app/.MainActivity >/dev/null; sleep 8
  adb exec-out screencap -p > "logs/instalar/en-$SERIAL-1-inicio.png"
  for tab in 3 4 5; do :; done
  python3 - <<'PY'
import subprocess, re, time, os
s=os.environ["ANDROID_SERIAL"]
def adb(*a): return subprocess.run(["adb","-s",s,*a],capture_output=True,text=True).stdout
def tap(pattern):
    adb("shell","uiautomator","dump","/sdcard/ui.xml"); x=adb("shell","cat","/sdcard/ui.xml")
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        if re.search(pattern, m.group(1), re.I):
            x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); return True
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/en-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
for pat,name in [(r"^\s*AI\s*$","2-ai"),(r"^\s*Chat\s*$","3-chat"),(r"^\s*Library\s*$","4-library"),(r"^\s*System\s*$","5-system"),(r"^\s*Home\s*$","6-home")]:
    if tap(pat): time.sleep(3); shot(name)
if tap(r"EMERGENCY"): time.sleep(3); shot("7-emergency")
if tap(r"Open radio and frequencies"): time.sleep(3); shot("8-radio")
PY
  setlang pt-BR
  adb shell am start -n com.oracleos.app/.MainActivity >/dev/null
done
echo "=== Teste EN concluído $(date '+%F %T'). Pode fechar esta janela. ==="
