#!/bin/bash
# Oracle OS — só o mapa da Grã-Bretanha (Escócia, Inglaterra, País de Gales). Retoma passos já feitos.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"; mkdir -p logs/dados
caffeinate -dimsu -w $$ &
python3 companion/map-pack-builder/build_gb_map.py 2>&1 | tee logs/dados/mapa.log
echo $? > logs/dados/mapa.exit
echo "=== Mapa concluído $(date '+%F %T'). Pode fechar esta janela. ==="
