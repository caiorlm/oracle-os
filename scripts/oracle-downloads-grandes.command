#!/bin/bash
# Oracle OS — downloads grandes, por ordem: 4 modelos de IA maiores (Qwen3.5 2B, 4B, 9B Q3, 9B Q4;
# ~14 GB, SHA-256 confirmado com o Hugging Face) e a Wikipedia em inglês com imagens (top 1 milhão, ~46 GB).
# Retoma se parar. No fim cria os pacotes assinados. Pode deixar a correr (Mac ligado à corrente).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/dados"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/grandes.log") 2>&1
# Stop an earlier run (single connection) before resuming in parallel.
for pid in $(pgrep -f "oracle-downloads-grandes.command"); do [ "$pid" != "$$" ] && kill "$pid" 2>/dev/null; done
pkill -f "library-builder/fetch.py" 2>/dev/null; sleep 2
export ORACLE_PARALLEL=8
echo "=== Downloads grandes — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
for i in 1 2 3 4 5; do
  python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-ia.json downloads/models-ia >> "$LOG/grandes-modelos.log" 2>&1
  python3 -c "import json,sys;d=json.load(open('downloads/models-ia/fetch-status.json'));sys.exit(0 if all(e.get('status')=='complete' for e in d['entries'].values()) and len(d['entries'])>=4 else 1)" && break
done
echo "modelos: exit $?"; tail -3 "$LOG/grandes-modelos.log"
python3 companion/library-builder/build_model_pack.py
echo "pacote de modelos: exit $?"
for i in 1 2 3 4 5; do
  python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog.json downloads/library --only wikipedia-top1m-maxi >> "$LOG/grandes-wikipedia.log" 2>&1
  python3 -c "import json,sys;d=json.load(open('downloads/library/fetch-status.json'));sys.exit(0 if d['entries'].get('wikipedia-top1m-maxi',{}).get('status')=='complete' else 1)" && break
done
echo "wikipedia com imagens: exit $?"; tail -3 "$LOG/grandes-wikipedia.log"
python3 companion/library-builder/build_knowledge_pack.py --no-retry > "$LOG/pacotes-grandes.log" 2>&1
echo "pacotes: exit $?"; tail -4 "$LOG/pacotes-grandes.log"
echo "=== Downloads grandes concluídos $(date '+%F %T'). Pode fechar esta janela. ==="
