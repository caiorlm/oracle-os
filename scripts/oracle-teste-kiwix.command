#!/bin/bash
# Oracle OS — teste: no Acervo toca em "Abrir no Kiwix" da primeira coleção e captura o que o Kiwix mostra.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
exec > >(tee -a logs/instalar/teste-kiwix.log) 2>&1
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
        adb("shell","input","swipe","540","1700","540","900","400"); time.sleep(1)
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/kiwix-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","org.kiwix.kiwixmobile")
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity"); time.sleep(14)
tap(r"^\s*Acervo\s*$"); time.sleep(4)
adb("shell","input","swipe","540","1600","540","700","500"); time.sleep(2)
adb("logcat","-c")
if tap(r"^\s*Abrir no Kiwix\s*$", swipes=4):
    time.sleep(10); shot("1-kiwix")
    if tap(r"^\s*NO\s*$"): time.sleep(8); shot("2-kiwix-depois-nao")
    log=subprocess.run(["adb","-s",s,"logcat","-d","-v","time"],capture_output=True,text=True,errors="replace").stdout
    open(f"logs/instalar/kiwix-{s}-logcat.txt","w").write("\n".join(log.splitlines()[-600:]))
    print(adb("shell","dumpsys","activity","activities").split("mResumedActivity")[1][:200] if "mResumedActivity" in adb("shell","dumpsys","activity","activities") else "")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
done
echo "=== Teste Kiwix concluído $(date '+%F %T') ==="
