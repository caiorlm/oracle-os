#!/bin/bash
# Oracle OS — "Socorro perto de mim": índice offline de hospitais, água potável, abrigos, farmácias,
# polícia, bombeiros e desfibrilhadores (OpenStreetMap) para Reino Unido, Irlanda, Portugal, Espanha e
# França, com rotas no Organic Maps. Cria o pacote assinado, compila a app e atualiza os telemóveis.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar
for pid in $(pgrep -f "oracle-locais.command"); do [ "$pid" != "$$" ] && kill "$pid" 2>/dev/null; done
pkill -f "places/build_places.py" 2>/dev/null; sleep 1
exec > >(tee -a logs/instalar/locais.log) 2>&1
echo "=== Locais de socorro — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
python3 -c "import osmium" 2>/dev/null || python3 -m pip install --user --quiet osmium || python3 -m pip install --user --quiet --break-system-packages osmium
python3 -c "import osmium; print('pyosmium', osmium.__version__ if hasattr(osmium,'__version__') else '?')"
python3 companion/places/build_places.py
echo "índice: exit $?"
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong|Could not" -A2 logs/instalar/gradle.log | head -40
  cp logs/instalar/gradle.log logs/instalar/gradle-locais-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/locais-falhou && cp -R app/src/main companion/fontes/locais-falhou/main-$(date +%H%M%S)
  rm -rf app/src/main/java/com/oracleos/app/feature/places
  tar xf companion/fontes/src-antes-locais.tar
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
def shot(n): open(f"logs/instalar/locais-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
print(adb("shell","ls","-la","/sdcard/Android/data/com.oracleos.app/files/packs/"))
adb("shell","am","force-stop","com.oracleos.app"); adb("shell","am","start","-n","com.oracleos.app/.MainActivity"); time.sleep(30)
tap(r"^\s*(Mapa|Map)\s*$"); time.sleep(3)
if tap(r"^\s*(Procurar perto de mim|Search near me)\s*$", swipes=2):
    time.sleep(25); shot("1-hospitais")
    if tap(r"^\s*(Água potável|Drinking water)\s*$"): time.sleep(5); shot("2-agua")
    if tap(r"^\s*(Abrigos|Shelters)\s*$"): time.sleep(5); shot("3-abrigos")
    if tap(r"^\s*(Hospitais|Hospitals)\s*$"): time.sleep(4)
    if tap(r"^\s*(Rota a pé|Walk route)\s*$"): time.sleep(8); shot("4-organic")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
  adb logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL|SQLite" | tail -30 > "logs/instalar/locais-logcat-$SERIAL.txt"
done
echo "=== Locais de socorro terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
