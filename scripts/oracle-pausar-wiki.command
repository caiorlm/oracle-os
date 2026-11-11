#!/bin/bash
# Pausa o download da Wikipedia (SIGSTOP) enquanto o Godot descarrega, e retoma-o (SIGCONT) no fim.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec >> logs/instalar/godot.log 2>&1
echo "--- pausa da Wikipedia $(date '+%T')"
pkill -STOP -f "only wikipedia-top1m-maxi" && echo "Wikipedia em pausa"
for i in $(seq 1 360); do
  [ -f logs/instalar/godot-estado.txt ] && break
  pgrep -f oracle-godot-instalar.command >/dev/null || break
  sleep 20
done
pkill -CONT -f "only wikipedia-top1m-maxi" && echo "Wikipedia retomada $(date '+%T')"
