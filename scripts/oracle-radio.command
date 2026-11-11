#!/bin/bash
# Oracle OS — adiciona o ecrã de Rádio de emergência (abre o rádio FM de fábrica e lista as
# frequências oficiais), compila e atualiza TODOS os telemóveis ligados; depois testa.
# Se não compilar, repõe os ficheiros anteriores (companion/radio/original) e instala essa versão.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/radio.log) 2>&1
echo "=== Rádio — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app; B=companion/radio/original
ORACLE_NO_ZIM=1 ORACLE_APPS=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -30 > companion/radio/FAILED
  echo "ATENÇÃO: o rádio não compilou; a repor a versão anterior"
  cp $B/MainActivity.kt $A/MainActivity.kt
  cp $B/DashboardScreen.kt $A/feature/dashboard/DashboardScreen.kt
  cp $B/EmergencyScreen.kt $A/feature/emergency/EmergencyScreen.kt
  cp $B/AndroidManifest.xml app/src/main/AndroidManifest.xml
  mkdir -p companion/radio/falhou && mv $A/feature/radio/RadioScreen.kt companion/radio/falhou/
  ORACLE_NO_ZIM=1 ORACLE_APPS=1 bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  date > companion/radio/OK; echo "Rádio compilado e instalado"
fi
echo "instalar: exit $code"
bash scripts/oracle-telefone-teste.command
echo "=== Rádio terminado $(date '+%F %T'). Pode fechar esta janela. ==="
