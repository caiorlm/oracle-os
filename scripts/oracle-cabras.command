#!/bin/bash
# Oracle OS — manuais alternativos de cabras (o do CVM deixou de existir: 404).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog-cabras.json downloads/library-reconstrucao >> logs/dados/cabras-fetch.log 2>&1
echo "cabras: exit $?" >> logs/dados/cabras-fetch.log
