#!/bin/bash
# Oracle OS — teste visual do assistente: pesquisa no separador IA e captura as fontes clicáveis.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
exec > >(tee -a logs/instalar/teste-chat.log) 2>&1
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  export ANDROID_SERIAL="$SERIAL"
  python3 - <<'PY'
import subprocess, re, time, os
s=os.environ["ANDROID_SERIAL"]
def adb(*a): return subprocess.run(["adb","-s",s,*a],capture_output=True,text=True).stdout
def tap(pattern):
    adb("shell","uiautomator","dump","/sdcard/ui.xml"); x=adb("shell","cat","/sdcard/ui.xml")
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        if re.search(pattern, m.group(1), re.I):
            x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); print("toque:",m.group(1)); return True
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/chat-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity"); time.sleep(14)
tap(r"^\s*IA\s*$"); time.sleep(3); shot("0-ia")
tap(r"Pesquisar acervo"); time.sleep(1)
if tap(r"Pergunte à biblioteca"):
    time.sleep(1); adb("shell","input","text","purificar%sagua%scom%slixivia"); time.sleep(1)
    tap(r"^\s*Buscar\s*$"); time.sleep(10); shot("1-resposta")
    adb("shell","input","swipe","540","1700","540","700","400"); time.sleep(2); shot("2-fontes")
    if tap(r"Abrir (PDF|documento)"): time.sleep(6); shot("3-aberto"); adb("shell","input","keyevent","KEYCODE_BACK")
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity")
PY
done
echo "=== Teste chat concluído $(date '+%F %T') ==="
