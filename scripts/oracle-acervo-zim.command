#!/bin/bash
# Oracle OS — retoma só as coleções ZIM (com troca automática de espelho se o servidor estiver lento),
# depois os APK de apoio e os pacotes assinados. O texto já está descarregado.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/dados"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/acervo.log") 2>&1
echo "=== Oracle acervo (ZIM) — $(date '+%F %T') ==="
# Pára a execução anterior (oracle-acervo.command) e o seu download, para não haver dois ao mesmo
# tempo; o ficheiro .part fica e é retomado. Primeiro o script, para não chegar a "Acervo concluído".
if pgrep -f "scripts/oracle-acervo\.command" >/dev/null; then
  echo "A parar a execução anterior do acervo (retoma do mesmo ponto)"
  pkill -f "scripts/oracle-acervo\.command"
fi
if pgrep -f "library-builder/fetch.py" >/dev/null; then
  pkill -f "library-builder/fetch.py"
  for i in 1 2 3 4 5 6 7 8 9 10; do pgrep -f "library-builder/fetch.py" >/dev/null || break; sleep 1; done
fi
if pgrep -f "library-builder/fetch.py" >/dev/null; then echo "ERRO: o download anterior não parou"; exit 1; fi
caffeinate -dimsu -w $$ &
PRIORITY="${ORACLE_MAX_PRIORITY:-3}"
CAT=companion/library-builder/survival-catalog.json
python3 companion/library-builder/fetch.py $CAT downloads/library --kinds zim --max-priority "$PRIORITY" >> "$LOG/acervo-zim.log" 2>&1
echo "zim: exit $?"
python3 companion/library-builder/fetch.py $CAT downloads/library --kinds apk >> "$LOG/apps.log" 2>&1
echo "apps: exit $?"
python3 companion/library-builder/build_knowledge_pack.py > "$LOG/pacotes.log" 2>&1
echo "pacotes: exit $?"
tail -5 "$LOG/pacotes.log"
echo "=== Acervo concluído $(date '+%F %T'). Pode fechar esta janela. ==="
