#!/bin/bash
# Oracle OS — põe o Realme em dia (saiu do cabo por volta das 21:15): espera que volte a aparecer,
# instala o APK mais recente, o acervo novo (399 documentos), o pacote de impressão e confirma tudo.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/realme-sync.log) 2>&1
echo "=== Realme — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
S=007448c502104212
adb devices -l
for i in $(seq 1 240); do
  st=$(adb devices | awk -v s=$S '$1==s{print $2}')
  [ "$st" = "device" ] && break
  [ $((i % 10)) = 1 ] && echo "à espera do Realme (estado: ${st:-não ligado}) — ligue o cabo USB e desbloqueie o ecrã; se aparecer 'Permitir depuração USB', toque em Permitir"
  sleep 30
done
[ "$(adb devices | awk -v s=$S '$1==s{print $2}')" = "device" ] || { echo "Realme não apareceu"; exit 1; }
echo "Realme ligado"
adb -s $S install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/phone_install.py --serial $S --no-zim
adb -s $S shell am force-stop com.oracleos.app
adb -s $S shell am start -n com.oracleos.app/.MainActivity >/dev/null
sleep 15
adb -s $S logcat -d -v time | grep -E "FATAL|AndroidRuntime: " | tail -5
echo "=== Realme em dia $(date '+%F %T'). Pode fechar esta janela. ==="
