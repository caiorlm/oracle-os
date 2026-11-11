#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
{
echo "=== $(date) ==="
echo "credential.helper: $(git config --global --get credential.helper)"
echo "git-credentials existe: $([ -f ~/.git-credentials ] && echo sim || echo nao)"
[ -f ~/.git-credentials ] && grep -o "github.com" ~/.git-credentials | head -1
echo "gh config: $([ -d ~/.config/gh ] && echo sim || echo nao)"
echo "ssh github: $(ls ~/.ssh 2>/dev/null | tr '\n' ' ')"
echo "brew: $(which brew)"
# só o nome de utilizador guardado no Keychain para github.com (nunca a senha/token)
printf "protocol=https\nhost=github.com\n\n" | git credential-osxkeychain get 2>/dev/null | grep "^username=" || echo "sem credencial github.com no Keychain"
uname -m
} > logs/gh-check.txt 2>&1
