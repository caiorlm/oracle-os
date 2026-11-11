#!/bin/bash
# Oracle OS — manuais ilustrados de defesa pessoal (Army Combatives/jiu-jitsu, MCMAP, boxe clássico).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/dados/defesa.log) 2>&1
echo "=== Defesa pessoal — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-defesa.json downloads/library-defesa >> logs/dados/defesa-fetch.log 2>&1
python3 -c "
import json
s=json.load(open('downloads/library-defesa/fetch-status.json'))['entries']
print('completos:', sum(v['status']=='complete' for v in s.values()), '/', len(s))
[print('  FALHOU', k, str(v.get('errors'))[:160]) for k,v in s.items() if v['status']!='complete']"
echo "=== Defesa pessoal terminado $(date '+%F %T'). Pode fechar esta janela. ==="
