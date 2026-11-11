#!/bin/bash
# Fundo escuro e texto claro na bússola, plantas e socorro perto de mim; compila e atualiza os telemóveis.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/fundo.log) 2>&1
echo "=== Fundo — $(date '+%F %T') ==="
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -10; tar xf companion/fontes/src-antes-fundo.tar; ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
fi
bash scripts/oracle-bussola-teste.command
echo "=== Fundo terminado $(date '+%F %T'). Pode fechar esta janela. ==="
