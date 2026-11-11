#!/bin/bash
# GENESIS — instala o Godot 4.7.2 (editor macOS, binário Linux para testes automáticos e modelos de
# exportação Android). Confere SHA-512 com a lista oficial antes de instalar.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar downloads/godot tools
# Só uma cópia deste script; corta transferências paradas anteriores.
for p in $(pgrep -f oracle-godot-instalar.command); do [ "$p" != "$$" ] && kill "$p" 2>/dev/null; done
pkill -f "curl .*Godot_v" 2>/dev/null
exec > >(tee -a logs/instalar/godot.log) 2>&1
echo "=== Godot — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
VER=4.7.2
BASE="https://github.com/godotengine/godot/releases/download/${VER}-stable"
D=downloads/godot
for f in SHA512-SUMS.txt Godot_v${VER}-stable_macos.universal.zip Godot_v${VER}-stable_linux.x86_64.zip Godot_v${VER}-stable_export_templates.tpz; do
  [ -s "$D/$f" ] && { echo "já existe: $f"; continue; }
  echo "a descarregar $f"
  for i in $(seq 1 40); do curl -fL -sS --retry 5 --connect-timeout 30 --speed-limit 2000 --speed-time 45 -C - -o "$D/$f.part" "$BASE/$f" && break; echo "  tentativa $i parou ($(du -h "$D/$f.part" 2>/dev/null | cut -f1)); a retomar"; sleep 10; done
  [ -s "$D/$f.part" ] && mv "$D/$f.part" "$D/$f"
done
cd $D
for f in Godot_v${VER}-stable_macos.universal.zip Godot_v${VER}-stable_linux.x86_64.zip Godot_v${VER}-stable_export_templates.tpz; do
  want=$(grep " $f\$" SHA512-SUMS.txt | awk '{print $1}')
  have=$(shasum -a 512 "$f" | awk '{print $1}')
  if [ -n "$want" ] && [ "$want" = "$have" ]; then echo "SHA-512 OK: $f"; else echo "SHA-512 FALHOU: $f ($want vs $have)"; exit 1; fi
done
cd "$ROOT"
rm -rf tools/godot && mkdir -p tools/godot && unzip -q -o "$D/Godot_v${VER}-stable_macos.universal.zip" -d tools/godot
unzip -q -o "$D/Godot_v${VER}-stable_linux.x86_64.zip" -d tools/godot
T="$HOME/Library/Application Support/Godot/export_templates/${VER}.stable"
mkdir -p "$T" && rm -rf /tmp/godot-tpz && unzip -q -o "$D/Godot_v${VER}-stable_export_templates.tpz" -d /tmp/godot-tpz && cp -R /tmp/godot-tpz/templates/* "$T/"
ls tools/godot "$T" | head -30
echo "GODOT_OK" > logs/instalar/godot-estado.txt
echo "=== Godot instalado $(date '+%F %T'). Pode fechar esta janela. ==="
