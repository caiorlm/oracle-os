#!/bin/bash
# Oracle OS — volta a descarregar as enciclopédias médicas grandes (MDWiki, MedlinePlus, Gutenberg
# agricultura e náutica). O fetch.py agora aceita a cópia quando o checksum interno do ZIM confere.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec >> logs/dados/med-zim2.log 2>&1
echo "=== $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
while pgrep -f "oracle-fila.command" >/dev/null; do sleep 60; done
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-med.json downloads/library-med --only mdwiki,medlineplus,gutenberg-naval,gutenberg-agriculture,zimgit-post-disaster
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-cabras.json downloads/library-reconstrucao
python3 companion/library-builder/build_knowledge_pack.py --no-retry > logs/dados/pacotes-med-zim2.log 2>&1; echo "pacotes: exit $?"
bash scripts/oracle-instalar.command
echo "=== fim $(date '+%F %T') ==="
