#!/bin/bash
# Oracle OS — acrescenta ao acervo os manuais extra (português e inglês) e os guias Oracle,
# reconstrói o pacote de texto assinado, compila o app (rádio com diretório de 75 países) e
# atualiza todos os telemóveis ligados; depois testa. O download dos ZIM continua noutra janela.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/dados/acervo-extra.log) 2>&1
echo "=== Acervo extra — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-extra.json downloads/library-extra
echo "extra: exit $?"
python3 companion/library-builder/diretorio/gerar.py
python3 companion/library-builder/build_knowledge_pack.py --text-only --no-retry > logs/dados/pacotes-extra.log 2>&1
code=$?
echo "pacote de texto: exit $code"; tail -8 logs/dados/pacotes-extra.log
ls oracle-ssd
A=app/src/main/java/com/oracleos/app
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
c=$?
if [ $c -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -20
  echo "ATENÇÃO: o rádio novo não compilou; a repor RadioScreen anterior"
  cp companion/radio/RadioScreen-v1.kt $A/feature/radio/RadioScreen.kt
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
fi
bash scripts/oracle-telefone-teste.command
echo "=== Acervo extra concluído $(date '+%F %T'). Pode fechar esta janela. ==="
