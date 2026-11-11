#!/bin/bash
# Oracle OS — só recompila e reinstala o app (sem voltar a verificar as coleções ZIM no telemóvel).
# Se não compilar, repõe a versão anterior do Telemetry.kt e reinstala essa.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
T=app/src/main/java/com/oracleos/app/feature/dashboard/Telemetry.kt
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
if [ $? -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  echo "ATENÇÃO: não compilou; a repor Telemetry.kt"; cp companion/ui-v23/original/Telemetry.kt "$T"
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
fi
echo "=== App concluído $(date '+%F %T'). Pode fechar esta janela. ==="
