#!/bin/bash
# Oracle OS — instala JÁ no telemóvel as coleções ZIM que já estão descarregadas, com o leitor Kiwix
# e o Organic Maps, sem esperar pelas que faltam (a internet está lenta). O download das restantes
# continua noutra janela; no fim, oracle-finalizar instala o resto.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG="$ROOT/logs/dados"; mkdir -p "$LOG"
exec > >(tee -a "$LOG/zim-agora.log") 2>&1
echo "=== ZIM agora — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
CAT=companion/library-builder/survival-catalog.json
# APK numa pasta própria, para não mexer no registo do download em curso.
python3 companion/library-builder/fetch.py $CAT downloads/library-apps --kinds apk
echo "apps: exit $?"
mkdir -p downloads/library/apps
for apk in downloads/library-apps/apps/*.apk; do [ -f "$apk" ] && cp -n "$apk" downloads/library/apps/; done
ls -la downloads/library/apps
python3 companion/library-builder/build_knowledge_pack.py --no-retry --version "$(date -u +%Y.%m.%d).1"
echo "pacotes: exit $?"
ls oracle-ssd
ORACLE_APPS=1 bash scripts/oracle-instalar.command
echo "instalar: exit $?"
bash scripts/oracle-telefone-teste.command
echo "=== ZIM agora concluído $(date '+%F %T'). Pode fechar esta janela. ==="
