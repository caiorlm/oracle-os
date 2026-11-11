#!/bin/bash
# Oracle OS V2.1 (visual): espera o acervo de texto terminar de instalar, aplica o novo visual,
# compila, instala no telemóvel e tira capturas (arranque, painel, mapa, acervo, emergência).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/v21.log) 2>&1
caffeinate -dimsu -w $$ &
echo "=== V2.1 — $(date '+%F %T'): à espera do acervo de texto ==="
while pgrep -f "oracle-acervo-texto.command" >/dev/null; do sleep 30; done
S=companion/ui-v21; A=app/src/main/java/com/oracleos/app
mkdir -p $A/feature/dashboard
cp $S/OracleTheme.kt $S/Components.kt $A/ui/
cp $S/Telemetry.kt $S/BootScreen.kt $S/DashboardScreen.kt $A/feature/dashboard/
cp $S/MainActivity.kt $A/
echo "visual V2.1 aplicado"
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
bash scripts/oracle-telefone-teste.command
echo "=== V2.1 concluída $(date '+%F %T') ==="
