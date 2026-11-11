#!/bin/bash
# Oracle OS — Kiwix abre as coleções direto do Oracle, sem pedir permissão (as coleções passam para
# o armazenamento do Oracle e são entregues ao Kiwix só para leitura), mais traduções EN em falta.
# Compila e atualiza os telemóveis ligados; se não compilar, repõe a versão anterior. Depois testa.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/kiwix-direto.log) 2>&1
echo "=== Kiwix direto — $(date '+%F %T') ==="
bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -30
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  rm -f app/src/main/res/xml/oracle_file_paths.xml
  tar xf companion/fontes/src-antes-kiwix.tar
  cp companion/fontes/phone_install-antes-kiwix.py scripts/phone_install.py
  bash scripts/oracle-instalar.command
  code=$?
fi
bash scripts/oracle-telefone-teste.command
bash scripts/oracle-teste-en.command
echo "=== Kiwix direto terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
