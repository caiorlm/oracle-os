#!/bin/bash
# Oracle OS — recompila o app (Kiwix "standalone" reconhecido), instala nos telemóveis ligados e testa o Kiwix.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
if [ $? -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  echo "ATENÇÃO: não compilou"; grep -E "^e: " logs/instalar/gradle.log | head -20
else
  bash scripts/oracle-teste-kiwix.command
fi
echo "=== App+Kiwix terminado $(date '+%F %T'). Pode fechar esta janela. ==="
