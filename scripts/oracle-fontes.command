#!/bin/bash
# Oracle OS — respostas do assistente com fontes clicáveis (abre o PDF na página citada ou o texto),
# respostas mais completas e fontes também na Conversa (modo Estudo). Compila e atualiza todos os
# telemóveis ligados; se não compilar, repõe o código anterior (companion/fontes) e reinstala.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/fontes.log) 2>&1
echo "=== Fontes — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -40 | tee companion/fontes/FAILED
  echo "ATENÇÃO: não compilou; a repor o código anterior"
  mkdir -p companion/fontes/falhou && cp -R $A companion/fontes/falhou/src-$(date +%H%M%S)
  rm -f $A/feature/knowledge/SourceViews.kt
  tar xf companion/fontes/src-antes-fontes.tar -C $A
  tar xf companion/fontes/knowledge-antes-stem.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  rm -f companion/fontes/FAILED; date > companion/fontes/OK; echo "Fontes compiladas e instaladas"
fi
bash scripts/oracle-telefone-teste.command
echo "=== Fontes terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
