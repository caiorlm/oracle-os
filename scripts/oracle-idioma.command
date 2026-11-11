#!/bin/bash
# Oracle OS — app bilingue (PT-BR / EN, escolha no Início ou em Sistema). Compila e atualiza todos os
# telemóveis ligados. Se não compilar, repõe o código anterior (companion/i18n/original) e reinstala.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/idioma.log) 2>&1
echo "=== Idioma — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -40 | tee companion/i18n/FAILED
  echo "ATENÇÃO: a versão bilingue não compilou; a repor o código anterior"
  mkdir -p companion/i18n/falhou && cp -R $A companion/i18n/falhou/src-$(date +%H%M%S)
  rm -f $A/ui/I18n.kt $A/ui/I18nStrings.kt
  tar xf companion/i18n/original/src-antes-i18n.tar -C $A
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  rm -f companion/i18n/FAILED; date > companion/i18n/OK; echo "Bilingue compilado e instalado"
fi
bash scripts/oracle-telefone-teste.command
echo "=== Idioma terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
