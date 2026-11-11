#!/bin/bash
# Oracle OS — inventário completo: tamanho do APK e tudo o que está em cada telemóvel ligado.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
OUT=logs/instalar/inventario.txt
{
echo "=== Inventário Oracle — $(date '+%F %T') ==="
ls -l app/build/outputs/apk/debug/app-debug.apk | awk '{printf "APK local: %.1f MB\n", $5/1048576}'
for S in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  A="adb -s $S shell"
  echo; echo "##### $($A getprop ro.product.manufacturer | tr -d '\r') $($A getprop ro.product.model | tr -d '\r') ($S) · Android $($A getprop ro.build.version.release | tr -d '\r')"
  $A df -h /data | tail -1 | awk '{print "Armazenamento: total "$2" · usado "$3" · livre "$4}'
  $A cat /proc/meminfo | head -1
  $A dumpsys package com.oracleos.app | grep -m2 -E "versionName|lastUpdateTime" | sed 's/^ */App: /'
  $A pm path com.oracleos.app | tr -d '\r' | sed 's/package://' | while read p; do $A stat -c "APK instalado: %s bytes" "$p"; done
  D=/sdcard/Android/data/com.oracleos.app/files
  echo "-- Pastas do Oracle (tamanho)"; $A "du -sh $D/* 2>/dev/null"
  echo "-- Pacotes assinados"; $A "for p in $D/packs/*/; do echo \"\$(du -sh \$p | cut -f1)  \$(basename \$p)\"; done"
  echo "-- Documentos no pacote do acervo"; $A "ls $D/packs/oracle-survival-library-*/ 2>/dev/null | head -3; find $D/packs/oracle-survival-library-* -type f 2>/dev/null | wc -l"
  echo "-- Modelos de IA"; $A "find $D -name '*.gguf' -exec ls -la {} \; 2>/dev/null" | awk '{printf "%.2f GB  %s\n",$5/1073741824,$NF}'
  echo "-- Enciclopédias ZIM"; $A "ls -la $D/kiwix/*.zim 2>/dev/null" | awk '{printf "%.2f GB  %s\n",$5/1073741824,$NF}'
  echo "-- Impressão"; $A "find $D/impressao -type f 2>/dev/null | wc -l; du -sh $D/impressao /sdcard/Documents/Oracle-Impressao 2>/dev/null"
  echo "-- Mapas Organic Maps"; $A "du -sh /sdcard/Android/data/app.organicmaps*/files/* 2>/dev/null | tail -5"
  echo "-- Apps de apoio"; $A pm list packages | tr -d '\r' | grep -E "kiwix|organicmaps|oracleos" | sed 's/package:/  /'
done
} 2>&1 | tee "$OUT"
