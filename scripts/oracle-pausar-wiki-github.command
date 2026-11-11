#!/bin/bash
# Pausa a Wikipedia enquanto o gh descarrega e a publicação no GitHub envia os ficheiros.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec >> logs/github-login.txt 2>&1
pkill -STOP -f "only wikipedia-top1m-maxi" && echo "Wikipedia em pausa $(date '+%T')"
for i in $(seq 1 540); do [ -f logs/github/estado.txt ] && break; sleep 20; done
pkill -CONT -f "only wikipedia-top1m-maxi" && echo "Wikipedia retomada $(date '+%T')"
