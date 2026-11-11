#!/bin/bash
# Publica o Oracle OS e o GENESIS no GitHub (repositório público oracle-os, GPL-3.0) com uma
# Release que traz os APKs prontos, o modelo de IA e os PDFs de impressão.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/github
for p in $(pgrep -f oracle-publicar.command); do [ "$p" != "$$" ] && kill "$p" 2>/dev/null; done
pkill -f "gh release" 2>/dev/null
exec > >(tee -a logs/github/publicar.log) 2>&1
echo "=== Publicar — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
GH="$ROOT/tools/gh/bin/gh"; [ -x "$GH" ] || GH="$(command -v gh)"; [ -x "$GH" ] || GH=/usr/local/bin/gh
"$GH" auth status || { echo "SEM LOGIN NO GITHUB"; exit 1; }
LOGIN=$("$GH" api user --jq .login); echo "conta: $LOGIN"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
# 1. APK do Oracle com os 71 guias incluídos
./gradlew --console=plain :app:assembleDebug > logs/github/gradle.log 2>&1 || { tail -30 logs/github/gradle.log; grep -E "^e: " logs/github/gradle.log; exit 1; }
mkdir -p dist/release
cp app/build/outputs/apk/debug/app-debug.apk dist/release/OracleOS.apk
cp dist/GENESIS-0.1.0.apk dist/release/GENESIS.apk
cp downloads/models/Qwen3.5-0.8B-Q4_K_M.gguf dist/release/
cp oracle-ssd/oracle-lite-model-dev-0.1.0/licenses/Qwen3.5-0.8B-LICENSE.txt dist/release/Qwen3.5-LICENSE.txt
cp dist/impressao/Oracle-Impressao-PT.pdf dist/impressao/Oracle-Print-EN.pdf dist/release/
(cd dist/release && shasum -a 256 OracleOS.apk GENESIS.apk Qwen3.5-0.8B-Q4_K_M.gguf Oracle-Impressao-PT.pdf Oracle-Print-EN.pdf > SHA256SUMS.txt && cat SHA256SUMS.txt)
ls -lh dist/release
# 2. Licença GPL-3.0 oficial e commit
[ -f LICENSE ] || "$GH" api /licenses/gpl-3.0 --jq .body > LICENSE
git config user.name "$LOGIN"
git config user.email "$LOGIN@users.noreply.github.com"
git add -A
git commit -q -m "Oracle OS 0.1.0 e GENESIS 0.1.0: publicação aberta (GPL-3.0)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01R6xbQXDCfg5yqbuuyl4adt" || true
git log --oneline | head -3
# Verificação de segredos antes de publicar
if git ls-files | grep -qE "^\.oracle-keys/|local\.properties|\.jks$|\.keystore$"; then echo "ABORTADO: ficheiro sensível no git"; exit 1; fi
# 3. Repositório público
if ! "$GH" repo view "$LOGIN/oracle-os" >/dev/null 2>&1; then
  "$GH" repo create oracle-os --public --description "Oracle OS — app Android 100% offline para sobreviver e recomeçar: IA local, mapas, guias, bússola, plantas, energia — e o jogo GENESIS." --homepage "https://github.com/$LOGIN/oracle-os/releases/latest"
fi
git remote remove origin 2>/dev/null
git remote add origin "https://github.com/$LOGIN/oracle-os.git"
git config http.postBuffer 524288000
git config http.version HTTP/1.1
for i in 1 2 3 4 5; do git push -u origin main && break; echo "push falhou ($i); a repetir"; sleep 15; done
"$GH" repo edit "$LOGIN/oracle-os" --add-topic offline,survival,android,llama-cpp,godot,preparedness,offline-first,kotlin 2>/dev/null
# 4. Release com os ficheiros prontos (o modelo de 508 MB demora com internet lenta)
"$GH" release view v0.1.0 -R "$LOGIN/oracle-os" >/dev/null 2>&1 || \
  "$GH" release create v0.1.0 -R "$LOGIN/oracle-os" --title "Oracle OS 0.1.0 + GENESIS 0.1.0" --notes-file docs/RELEASE-0.1.0.md \
    dist/release/OracleOS.apk dist/release/GENESIS.apk dist/release/SHA256SUMS.txt \
    dist/release/Oracle-Impressao-PT.pdf dist/release/Oracle-Print-EN.pdf
for f in Qwen3.5-LICENSE.txt Qwen3.5-0.8B-Q4_K_M.gguf; do
  for i in 1 2 3 4 5; do "$GH" release upload v0.1.0 -R "$LOGIN/oracle-os" "dist/release/$f" --clobber && break; echo "tentativa $i falhou; a repetir"; sleep 20; done
done
"$GH" release view v0.1.0 -R "$LOGIN/oracle-os" --json assets --jq '.assets[] | "\(.name) \(.size)"'
echo "URL: https://github.com/$LOGIN/oracle-os"
echo "PUBLICADO" > logs/github/estado.txt
echo "=== Publicado $(date '+%F %T'). Pode fechar esta janela. ==="
