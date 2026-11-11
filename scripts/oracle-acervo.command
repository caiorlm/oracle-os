#!/bin/bash
# Oracle OS — só o acervo: texto, coleções ZIM, APKs de apoio e pacotes assinados. Retoma downloads.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/dados"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/acervo.log") 2>&1
echo "=== Oracle acervo — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
PRIORITY="${ORACLE_MAX_PRIORITY:-3}"
CAT=companion/library-builder/survival-catalog.json
python3 companion/library-builder/fetch.py $CAT downloads/library --max-priority 1 >> "$LOG/acervo-texto.log" 2>&1
echo "texto: exit $?"
python3 companion/library-builder/fetch.py $CAT downloads/library --kinds zim --max-priority "$PRIORITY" >> "$LOG/acervo-zim.log" 2>&1
echo "zim: exit $?"
python3 companion/library-builder/fetch.py $CAT downloads/library --kinds apk >> "$LOG/apps.log" 2>&1
echo "apps: exit $?"
python3 companion/library-builder/build_knowledge_pack.py > "$LOG/pacotes.log" 2>&1
echo "pacotes: exit $?"
tail -5 "$LOG/pacotes.log"
echo "=== Acervo concluído $(date '+%F %T'). Pode fechar esta janela. ==="
