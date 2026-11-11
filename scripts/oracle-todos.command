#!/bin/bash
# Oracle OS — atualiza TODOS os telemóveis ligados por USB: compila o APK mais recente, instala-o,
# instala o Kiwix e o Organic Maps, copia o mapa, o acervo, o modelo e as coleções ZIM já
# descarregadas (verificadas no telemóvel) e corre o teste. Se não compilar, repõe Telemetry.kt.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
T=app/src/main/java/com/oracleos/app/feature/dashboard/Telemetry.kt
ORACLE_APPS=1 bash scripts/oracle-instalar.command
if [ $? -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  echo "ATENÇÃO: não compilou; a repor Telemetry.kt"; cp companion/ui-v23/original/Telemetry.kt "$T"
  ORACLE_APPS=1 bash scripts/oracle-instalar.command
fi
bash scripts/oracle-telefone-teste.command
echo "=== Todos atualizados $(date '+%F %T'). Pode fechar esta janela. ==="
