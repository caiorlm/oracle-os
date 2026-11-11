#!/bin/bash
# Oracle OS — Bíblia (PT e EN), alongamentos e dores, torniquete, yoga, meditação, chakras; guias novos;
# bússola completa. Descarrega, refaz o pacote de texto, compila (repõe se falhar) e atualiza os telemóveis.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/dados logs/instalar
exec > >(tee -a logs/dados/corpo-espirito.log) 2>&1
echo "=== Corpo e espírito — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-corpo.json downloads/library-corpo >> logs/dados/corpo-espirito-fetch.log 2>&1
python3 -c "
import json
s=json.load(open('downloads/library-corpo/fetch-status.json'))['entries']
print('completos:', sum(v['status']=='complete' for v in s.values()), '/', len(s))
[print('  FALHOU', k, str(v.get('errors'))[:160]) for k,v in s.items() if v['status']!='complete']"
python3 companion/library-builder/build_knowledge_pack.py --text-only --no-retry > logs/dados/pacotes-corpo.log 2>&1
echo "pacote de texto: exit $?"; tail -3 logs/dados/pacotes-corpo.log
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong" -A2 logs/instalar/gradle.log | head -30
  cp logs/instalar/gradle.log logs/instalar/gradle-bussola-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/bussola-falhou && cp -R app/src/main companion/fontes/bussola-falhou/main-$(date +%H%M%S)
  rm -f app/src/main/java/com/oracleos/app/feature/maps/CompassActivity.kt
  tar xf companion/fontes/src-antes-bussola.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
fi
bash scripts/oracle-bussola-teste.command
echo "=== Corpo e espírito terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
