#!/bin/bash
# Oracle OS — monta já o acervo de texto (sem esperar pelos ZIM) e instala tudo no telemóvel.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"; mkdir -p logs/dados
caffeinate -dimsu -w $$ &
echo "=== Acervo de texto — $(date '+%F %T') ==="
python3 companion/library-builder/build_knowledge_pack.py --text-only 2>&1 | tee logs/dados/pacotes-texto.log
echo "pacote de texto: exit ${PIPESTATUS[0]}"
bash scripts/oracle-instalar.command
bash scripts/oracle-telefone-teste.command
