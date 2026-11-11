#!/bin/bash
# Oracle OS — V2.3 (operacional): posição no ecrã de emergência com SMS, aviso de bateria
# fraca, menos atualizações em Sobrevivência e barras escuras no mapa e PDF. Compila e instala; se não compilar, repõe os ficheiros
# anteriores (guardados em companion/ui-v23/original) e instala a versão anterior. Depois testa.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/instalar"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/v23.log") 2>&1
echo "=== V2.3 — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app; B=companion/ui-v23/original
bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" "$LOG/gradle.log"; then
  grep -E "^e: " "$LOG/gradle.log" | head -30 > companion/ui-v23/FAILED
  cp "$LOG/gradle.log" "$LOG/gradle-v23-falhou.log"
  echo "ATENÇÃO: V2.3 não compilou; a repor a versão anterior"
  for f in feature/emergency/EmergencyScreen.kt MainActivity.kt feature/dashboard/DashboardScreen.kt platform/AndroidActions.kt feature/maps/OfflineMapActivity.kt feature/knowledge/PdfPageActivity.kt; do
    cp "$B/$(basename $f)" "$A/$f"
  done
  bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  date > companion/ui-v23/OK; echo "V2.3 compilado e instalado"
fi
echo "instalar: exit $code"
[ $code -eq 0 ] && bash scripts/oracle-telefone-teste.command
echo "=== V2.3 terminado $(date '+%F %T'). Pode fechar esta janela. ==="
