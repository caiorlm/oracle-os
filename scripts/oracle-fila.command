#!/bin/bash
# Oracle OS — fila completa, por prioridade, retomável:
#  A) fontes falhadas por outras fontes + pacote "Ofícios e reconstrução" (PDF/HTML) → acervo → telemóveis
#  B) espera o acervo médico; retoma MedlinePlus e pós-desastre
#  C) Gutenberg tecnologia (ofícios com ilustrações) → modelos de IA 4B/9B → Wikipédia com imagens (Realme)
#     → Wiktionary e Wikisource
#  D) pacotes finais e instalação em todos os telemóveis ligados
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/dados
for pid in $(pgrep -f "oracle-downloads-grandes.command|oracle-fila.command"); do [ "$pid" != "$$" ] && kill "$pid" 2>/dev/null; done
exec > >(tee -a logs/dados/fila.log) 2>&1
echo "=== Fila — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
export ORACLE_PARALLEL=8
LB=companion/library-builder
status() { python3 -c "
import json,sys
s=json.load(open(sys.argv[1]))['entries']
print('  completos:', sum(v['status']=='complete' for v in s.values()), '/', len(s))
[print('   FALHOU', k, str(v.get('errors'))[:140]) for k,v in s.items() if v['status']!='complete']" "$1"; }
echo "--- A) fontes falhadas + reconstrução $(date +%T)"
python3 $LB/fetch.py $LB/survival-catalog-retry.json downloads/library-retry >> logs/dados/fila-fetch.log 2>&1; status downloads/library-retry/fetch-status.json
python3 $LB/fetch.py $LB/survival-catalog-reconstrucao.json downloads/library-reconstrucao >> logs/dados/fila-fetch.log 2>&1; status downloads/library-reconstrucao/fetch-status.json
python3 $LB/build_knowledge_pack.py --text-only --no-retry > logs/dados/pacotes-fila.log 2>&1; echo "pacote de texto: exit $?"
python3 -c "
import json,glob
t={}
for f in sorted(glob.glob('companion/library-builder/survival-catalog*.json')):
    for e in json.load(open(f))['entries']: t[e['id']]=e.get('title',e['id'])
t['oracle-guias']='Guias Oracle'
json.dump(t,open('app/src/main/assets/acervo/entries.json','w'),ensure_ascii=False,indent=0)"
(cd companion/i18n && python3 gerar_kotlin.py >/dev/null)
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command > logs/dados/fila-instalar-A.log 2>&1; echo "instalação A: exit $?"; grep -a "BUILD\|arrancou" logs/dados/fila-instalar-A.log | tail -3
echo "--- B) à espera do acervo médico $(date +%T)"
while pgrep -f "oracle-acervo-med.command" >/dev/null; do sleep 60; done
python3 $LB/fetch.py $LB/survival-catalog-med.json downloads/library-med --kinds zim >> logs/dados/acervo-med-fetch.log 2>&1; status downloads/library-med/fetch-status.json
echo "--- C) Gutenberg tecnologia $(date +%T)"
python3 $LB/fetch.py $LB/survival-catalog.json downloads/library --only gutenberg-technology >> logs/dados/fila-fetch.log 2>&1
echo "--- C) modelos de IA $(date +%T)"
for i in 1 2 3; do
  python3 $LB/fetch.py $LB/survival-catalog-ia.json downloads/models-ia >> logs/dados/grandes-modelos.log 2>&1
  python3 -c "import json,sys;d=json.load(open('downloads/models-ia/fetch-status.json'));sys.exit(0 if all(e.get('status')=='complete' for e in d['entries'].values()) and len(d['entries'])>=4 else 1)" && break
done
status downloads/models-ia/fetch-status.json
python3 $LB/build_model_pack.py; echo "pacote de modelos: exit $?"
echo "--- C) Wikipédia com imagens $(date +%T)"
for i in 1 2 3; do
  python3 $LB/fetch.py $LB/survival-catalog.json downloads/library --only wikipedia-top1m-maxi >> logs/dados/grandes-wikipedia.log 2>&1
  python3 -c "import json,sys;d=json.load(open('downloads/library/fetch-status.json'));sys.exit(0 if d['entries'].get('wikipedia-top1m-maxi',{}).get('status')=='complete' else 1)" && break
done
echo "--- C) Wiktionary e Wikisource $(date +%T)"
python3 $LB/fetch.py $LB/survival-catalog.json downloads/library --only wiktionary-en,wikisource-en >> logs/dados/fila-fetch.log 2>&1
status downloads/library/fetch-status.json
echo "--- D) pacotes finais e instalação $(date +%T)"
python3 $LB/build_knowledge_pack.py --no-retry > logs/dados/pacotes-final.log 2>&1; echo "pacotes: exit $?"
bash scripts/oracle-instalar.command > logs/dados/fila-instalar-D.log 2>&1; echo "instalação final: exit $?"
grep -a "Kiwix:\|não copiado\|sem espaço\|arrancou" logs/dados/fila-instalar-D.log | tail -12
echo "=== Fila concluída $(date '+%F %T'). Pode fechar esta janela. ==="
