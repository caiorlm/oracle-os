#!/bin/bash
# Oracle OS — adiciona a Conversa com a IA local (separador IA → Conversar), compila e atualiza
# todos os telemóveis ligados. Se não compilar, repõe os ficheiros anteriores e reinstala essa versão.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/conversa.log) 2>&1
echo "=== Conversa — $(date '+%F %T') ==="
A=app/src/main/java/com/oracleos/app; B=companion/conversa/original
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -30 > companion/conversa/FAILED
  cat companion/conversa/FAILED
  echo "ATENÇÃO: a conversa não compilou; a repor a versão anterior"
  cp $B/ChatScreen.kt $A/feature/chat/ChatScreen.kt
  cp $B/OracleControllerImpl.kt $A/OracleControllerImpl.kt
  mkdir -p companion/conversa/falhou && mv $A/feature/chat/Conversation.kt companion/conversa/falhou/
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
elif [ $code -eq 0 ]; then
  rm -f companion/conversa/FAILED; date > companion/conversa/OK; echo "Conversa compilada e instalada"
fi
echo "=== Conversa terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
