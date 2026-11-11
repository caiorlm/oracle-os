#!/bin/bash
# Oracle OS — mapas offline do Organic Maps (Escócia Norte e Sul) copiados para os telemóveis, para as
# rotas a pé/carro funcionarem sem internet. Fonte: espelho oficial listado em cdn.organicmaps.app.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar downloads/organicmaps
exec > >(tee -a logs/instalar/organic-mapas.log) 2>&1
echo "=== Mapas Organic Maps — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
VER="${OM_VERSION:-260928}"   # data version of the Organic Maps build on the phones (2026.09.29)
MAPS=("UK_Scotland_North" "UK_Scotland_South")
mkdir -p "downloads/organicmaps/$VER"
for m in "${MAPS[@]}"; do
  f="downloads/organicmaps/$VER/$m.mwm"
  [ -s "$f" ] && echo "já descarregado: $m" && continue
  url="https://omaps.webfreak.org/maps/$VER/$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$m").mwm"
  curl -fL --retry 5 -C - -o "$f.part" "$url" && mv "$f.part" "$f" && echo "descarregado: $m ($(du -h "$f" | cut -f1))"
done
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
for SERIAL in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  echo "--- $(adb -s $SERIAL shell getprop ro.product.model)"
  PKG=$(adb -s $SERIAL shell pm list packages | tr -d '\r' | grep -o "app\.organicmaps[a-z.]*" | head -1)
  if [ -z "$PKG" ]; then echo "Organic Maps não instalado"; continue; fi
  echo "pacote $PKG versão $(adb -s $SERIAL shell dumpsys package $PKG | grep -m1 versionName | tr -d ' \r')"
  DIR="/sdcard/Android/data/$PKG/files"
  adb -s $SERIAL shell "ls -la $DIR; ls -la $DIR/*/ 2>/dev/null | head -40"
  adb -s $SERIAL shell "rm -rf '$DIR/261103'; mkdir -p '$DIR/$VER'"
  for m in "${MAPS[@]}"; do
    f="downloads/organicmaps/$VER/$m.mwm"
    [ -s "$f" ] || continue
    size=$(stat -f %z "$f")
    have=$(adb -s $SERIAL shell "stat -c %s '$DIR/$VER/$m.mwm' 2>/dev/null" | tr -d '\r')
    if [ "$have" = "$size" ]; then echo "já no telefone: $m"; else adb -s $SERIAL push "$f" "$DIR/$VER/$m.mwm" && echo "copiado: $m"; fi
  done
  adb -s $SERIAL shell "ls -la '$DIR/$VER'"
  adb -s $SERIAL shell am force-stop $PKG
done
echo "=== Mapas Organic Maps terminado $(date '+%F %T'). Pode fechar esta janela. ==="
