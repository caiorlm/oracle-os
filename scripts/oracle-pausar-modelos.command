#!/bin/bash
# Pausa os downloads grandes (modelos de IA e Wikipedia). As partes ficam guardadas;
# para retomar, abra scripts/oracle-downloads-grandes.command.
for pid in $(pgrep -f "oracle-downloads-grandes.command"); do kill "$pid" 2>/dev/null; done
pkill -f "survival-catalog-ia.json" 2>/dev/null
pkill -f "only wikipedia-top1m-maxi" 2>/dev/null
sleep 2; pgrep -fl "fetch.py" || true
echo "$(date '+%F %T') downloads grandes pausados" | tee -a "$(cd "$(dirname "$0")/.." && pwd)/logs/dados/grandes.log"
echo "Pode fechar esta janela."
