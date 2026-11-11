#!/bin/bash
# Oracle OS — põe todos os telemóveis ligados ao mesmo nível: instala Kiwix e Organic Maps onde faltam,
# copia os mapas de rotas e as enciclopédias ZIM (cabe o que o espaço livre permitir).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar
exec > >(tee -a logs/instalar/paridade.log) 2>&1
echo "=== Paridade dos telemóveis — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
VER=260928
for S in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  MODEL=$(adb -s $S shell getprop ro.product.model | tr -d '\r')
  PKGS=$(adb -s $S shell pm list packages | tr -d '\r')
  NEED=0
  echo "$PKGS" | grep -q "org.kiwix" || { echo "[$MODEL] a instalar Kiwix"; adb -s $S install -r downloads/library/apps/kiwix-v3.14.1-arm64-v8a-standalone.apk; NEED=1; }
  echo "$PKGS" | grep -q "app.organicmaps" || { echo "[$MODEL] a instalar Organic Maps"; adb -s $S install -r downloads/library/apps/OrganicMaps-26092932-web-release.apk; NEED=1; }
  DIR=/sdcard/Android/data/app.organicmaps.web/files
  adb -s $S shell "mkdir -p $DIR/$VER"
  for m in UK_Scotland_North UK_Scotland_South; do
    f="downloads/organicmaps/$VER/$m.mwm"; size=$(stat -f %z "$f")
    have=$(adb -s $S shell "stat -c %s '$DIR/$VER/$m.mwm' 2>/dev/null" | tr -d '\r')
    [ "$have" = "$size" ] && echo "[$MODEL] mapa $m já está" || { adb -s $S push "$f" "$DIR/$VER/$m.mwm" && echo "[$MODEL] mapa $m copiado"; }
  done
  ZIMS=$(adb -s $S shell "ls /sdcard/Android/data/com.oracleos.app/files/kiwix/*.zim 2>/dev/null | wc -l" | tr -d '\r ')
  if [ "${ZIMS:-0}" -lt 5 ]; then
    echo "[$MODEL] a copiar enciclopédias ZIM (pode demorar)"
    python3 scripts/phone_install.py --serial "$S" 2>&1 | grep -vE "já no telefone" | tail -40
  else
    echo "[$MODEL] já tem $ZIMS enciclopédias"
  fi
done
echo "=== Paridade terminada $(date '+%F %T'). Pode fechar esta janela. ==="
