#!/bin/bash
# Oracle OS — acervo completo de medicina, resgate, cordas, navegação, pesca e água/mar:
# ~45 manuais em PDF ilustrados (MSF, OMS, CICV, Hesperian, US Army, FAO, USCG, INSARAG, SOBRASA, SAMU...),
# 12 guias Oracle novos (PT/EN) e 12 enciclopédias offline (MDWiki, MedlinePlus, medicina militar,
# pós-desastre, nós, água, alimentos, Gutenberg agricultura/pesca e marinharia).
# 1) PDFs + guias → pacote de texto → telemóveis. 2) Enciclopédias → pacote Kiwix → telemóveis.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/dados
exec > >(tee -a logs/dados/acervo-med.log) 2>&1
echo "=== Acervo medicina/resgate — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
CAT=companion/library-builder/survival-catalog-med.json
python3 companion/library-builder/fetch.py $CAT downloads/library-med --kinds pdf,html >> logs/dados/acervo-med-fetch.log 2>&1
echo "PDFs: exit $?"
python3 - <<'PY'
import json
s=json.load(open("downloads/library-med/fetch-status.json"))["entries"]
ok=[k for k,v in s.items() if v["status"]=="complete"]; bad={k:v["errors"][:1] for k,v in s.items() if v["status"]!="complete"}
print(f"PDF/HTML completos: {len(ok)}"); [print("  FALHOU", k, str(v)[:160]) for k,v in bad.items()]
PY
python3 companion/library-builder/build_knowledge_pack.py --text-only --no-retry > logs/dados/pacotes-med.log 2>&1
echo "pacote de texto: exit $?"; tail -6 logs/dados/pacotes-med.log
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
echo "--- Enciclopédias offline (pode demorar horas com a internet lenta) ---"
python3 companion/library-builder/fetch.py $CAT downloads/library-med --kinds zim >> logs/dados/acervo-med-fetch.log 2>&1
echo "ZIM: exit $?"
python3 - <<'PY'
import json
s=json.load(open("downloads/library-med/fetch-status.json"))["entries"]
for k,v in s.items():
    if v.get("kind")=="zim": print(f"  {k}: {v['status']}", (v.get('errors') or [''])[0][:120])
PY
python3 companion/library-builder/build_knowledge_pack.py --no-retry > logs/dados/pacotes-med-zim.log 2>&1
echo "pacotes: exit $?"; tail -6 logs/dados/pacotes-med-zim.log
bash scripts/oracle-instalar.command
echo "=== Acervo medicina/resgate concluído $(date '+%F %T'). Pode fechar esta janela. ==="
