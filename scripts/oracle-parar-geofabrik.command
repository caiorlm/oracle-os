#!/bin/bash
# Pára o download lento dos mapas da Irlanda/Portugal (fica guardado para retomar depois).
for i in 1 2 3 4 5 6; do pkill -f "download.geofabrik.de" && echo "parado"; sleep 5; done
echo "Pode fechar esta janela."
