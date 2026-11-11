#!/bin/bash
# Oracle OS — backup completo antes de começar o jogo GENESIS: commit + tag no git e um arquivo .tar.gz
# com o código-fonte, scripts, guias, chaves e o APK atual em ~/Desktop/backups.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee -a logs/instalar/backup.log) 2>&1
STAMP=$(date +%Y-%m-%d_%H%M)
echo "=== Backup $STAMP ==="
git add -A
git -c user.name="Caio" -c user.email="caio@oracle-os.local" commit -q -m "Oracle OS: estado antes do jogo GENESIS (intro 3D, energia, impressão, acervo 399 docs, 71 guias)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01R6xbQXDCfg5yqBUuYL4adT" && echo "commit feito"
git tag -f "backup-antes-genesis-$STAMP" && echo "tag backup-antes-genesis-$STAMP"
git log --oneline | head -3
mkdir -p "$HOME/Desktop/backups"
OUT="$HOME/Desktop/backups/ORACLE-OS_backup_${STAMP}_antes-genesis.tar.gz"
cp app/build/outputs/apk/debug/app-debug.apk "dist/OracleOS-0.1.0-debug.apk"
tar czf "$OUT" -C "$HOME/Desktop" \
  --exclude='ORACLE-OS/downloads' --exclude='ORACLE-OS/oracle-ssd' --exclude='ORACLE-OS/logs' \
  --exclude='*/build' --exclude='ORACLE-OS/.gradle' --exclude='ORACLE-OS/.kotlin' --exclude='*/.cxx' \
  --exclude='ORACLE-OS/third_party/llama.cpp' --exclude='ORACLE-OS/companion/fontes/*.tar' \
  --exclude='ORACLE-OS/_to_delete' --exclude='*/__pycache__' ORACLE-OS
shasum -a 256 "$OUT" | tee "$OUT.sha256"
ls -lh "$OUT"
echo "=== Backup terminado $(date '+%F %T'). Pode fechar esta janela. ==="
