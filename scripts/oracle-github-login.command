#!/bin/bash
# Liga o Mac ao GitHub: descarrega o gh (GitHub CLI) oficial e abre o login no navegador.
# Você só precisa de colar o código que aparece aqui na página github.com/login/device.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
LOG=logs/github-login.txt
pkill -f "brew install gh" 2>/dev/null
echo "=== $(date) ===" > $LOG
mkdir -p tools/gh
if [ ! -x tools/gh/bin/gh ]; then
  URL=$(curl -fsSL https://api.github.com/repos/cli/cli/releases/latest | grep -o '"browser_download_url": *"[^"]*macOS_amd64.zip"' | sed 's/.*"\(https[^"]*\)"/\1/')
  echo "gh: $URL" | tee -a $LOG
  curl -fL --retry 5 -o /tmp/gh.zip "$URL" && unzip -q -o /tmp/gh.zip -d /tmp/gh-unz && cp -R /tmp/gh-unz/*/bin tools/gh/
fi
GH="$ROOT/tools/gh/bin/gh"
"$GH" --version | head -1 | tee -a $LOG
echo ""
echo "=================================================================="
echo " LOGIN NO GITHUB: copie o código que aparece abaixo, carregue Enter,"
echo " e no navegador cole o código e clique em Authorize."
echo "=================================================================="
"$GH" auth login --hostname github.com --git-protocol https --web --scopes "repo,workflow" 2>&1 | tee -a $LOG
"$GH" auth setup-git >> $LOG 2>&1
"$GH" auth status >> $LOG 2>&1
echo "login=$("$GH" api user --jq .login 2>/dev/null)" >> $LOG
echo "=== fim $(date) ===" >> $LOG
echo "Pronto. Pode fechar esta janela."
