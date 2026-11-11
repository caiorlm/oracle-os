#!/bin/bash
# Oracle OS — Acervo em árvore (Índice por área → categoria → obra → documento, Pesquisa, Enciclopédias),
# resumo opcional pela IA. Espera pelos downloads em curso (corpo/espírito, defesa), refaz o pacote de
# texto com tudo, aplica a árvore, compila (repõe se falhar), atualiza os telemóveis e testa.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar
exec > >(tee -a logs/instalar/arvore.log) 2>&1
echo "=== Árvore do acervo — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
for i in $(seq 1 360); do
  if pgrep -f "oracle-corpo-espirito.command|oracle-defesa.command" >/dev/null; then
    [ $i = 1 ] && echo "a aguardar os downloads em curso…"; sleep 20; else break; fi
done
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-corpo.json downloads/library-corpo --only hatha-yoga-pradipika,mindfulness-plain-english >> logs/dados/corpo-espirito-fetch.log 2>&1
python3 companion/library-builder/build_knowledge_pack.py --text-only --no-retry > logs/dados/pacotes-arvore.log 2>&1
echo "pacote de texto: exit $?"; tail -2 logs/dados/pacotes-arvore.log
tar cf companion/fontes/src-antes-arvore.tar app/src knowledge/src
python3 companion/arvore/aplicar.py && (cd companion/i18n && python3 gerar_kotlin.py >/dev/null)
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong" -A2 logs/instalar/gradle.log | head -30
  cp logs/instalar/gradle.log logs/instalar/gradle-arvore-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/arvore-falhou && cp -R app/src/main companion/fontes/arvore-falhou/main-$(date +%H%M%S)
  rm -f app/src/main/java/com/oracleos/app/feature/knowledge/LibraryTree.kt
  tar xf companion/fontes/src-antes-arvore.tar
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
                x1,y1,x2,y2=map(int,m.group(2,3,4,5)); adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)); print("toque:",m.group(1)[:50]); return True
        adb("shell","input","swipe","540","1500","540","800","400"); time.sleep(1)
    print("não encontrado:",pattern); return False
def shot(n): open(f"logs/instalar/arvore-{s}-{n}.png","wb").write(subprocess.run(["adb","-s",s,"exec-out","screencap","-p"],capture_output=True).stdout)
adb("shell","am","force-stop","com.google.android.photopicker")
adb("shell","am","start","-S","-W","-n","com.oracleos.app/.MainActivity"); time.sleep(25)
tap(r"^\s*(Acervo|Library)\s*$"); time.sleep(6); shot("1-indice")
if tap(r"^\s*(Saúde e primeiros socorros|Health and first aid)\s*$"):
    time.sleep(2); shot("2-area")
    if tap(r"^\s*(Primeiros socorros|First aid)\s*$"): time.sleep(2); shot("3-categoria")
tap(r"^\s*(Enciclopédias|Encyclopedias)\s*$"); time.sleep(3); shot("4-enciclopedias")
tap(r"^\s*(Mapa|Map)\s*$"); time.sleep(3)
if tap(r"^\s*(Abrir bússola|Open compass)\s*$", swipes=3):
    time.sleep(6); shot("5-bussola")
adb("shell","input","keyevent","KEYCODE_HOME")
PY
  adb logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL" | tail -20 > "logs/instalar/arvore-logcat-$SERIAL.txt"
done
echo "=== Árvore do acervo terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
