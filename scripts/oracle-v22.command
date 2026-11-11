#!/bin/bash
# Oracle OS — V2.2 (interação): vibração e som ao toque, animação ao premir, transições entre
# separadores e estados de carregamento. Compila e instala; se não compilar, repõe os ficheiros
# anteriores (guardados em companion/ui-v22/original) e instala a versão anterior. Depois testa.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/instalar"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/v22.log") 2>&1
echo "=== V2.2 — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app; B=companion/ui-v22/original
bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" "$LOG/gradle.log"; then
  grep -E "^e: " "$LOG/gradle.log" | head -30 > companion/ui-v22/FAILED
  cp "$LOG/gradle.log" "$LOG/gradle-v22-falhou.log"
  echo "ATENÇÃO: V2.2 não compilou; a repor a versão anterior"
  for f in ui/Components.kt MainActivity.kt feature/dashboard/DashboardScreen.kt feature/chat/ChatScreen.kt feature/knowledge/KnowledgeScreen.kt feature/system/SystemScreen.kt feature/emergency/EmergencyScreen.kt; do
    cp "$B/$(basename $f)" "$A/$f"
  done
  mkdir -p companion/ui-v22/falhou && mv "$A/ui/Feedback.kt" companion/ui-v22/falhou/ 2>/dev/null
  bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  date > companion/ui-v22/OK; echo "V2.2 compilado e instalado"
fi
echo "instalar: exit $code"
[ $code -eq 0 ] && bash scripts/oracle-telefone-teste.command
echo "=== V2.2 terminado $(date '+%F %T'). Pode fechar esta janela. ==="
