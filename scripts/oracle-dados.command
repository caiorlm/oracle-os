#!/bin/bash
# Oracle OS — gera os dados offline no Mac: mapa da Grã-Bretanha e acervo de sobrevivência.
# Abra com dois cliques ou corra no Terminal:  bash ~/Desktop/ORACLE-OS/scripts/oracle-dados.command
# Pode fechar e voltar a abrir: os downloads retomam onde pararam e os passos já feitos são saltados.
# Variáveis opcionais: ORACLE_MAX_PRIORITY=2 (menos ZIM), =4 (inclui Gutenberg, Wiktionary, Wikisource).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOG="$ROOT/logs/dados"
mkdir -p "$LOG"
exec > >(tee -a "$LOG/dados.log") 2>&1
echo "=== Oracle dados — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &

free_gb=$(df -g "$ROOT" | awk 'NR==2{print $4}')
mem_gb=$(( $(sysctl -n hw.memsize) / 1073741824 ))
echo "Disco livre: ${free_gb} GB · RAM: ${mem_gb} GB · CPUs: $(sysctl -n hw.ncpu)"
PRIORITY="${ORACLE_MAX_PRIORITY:-3}"
if [ "$PRIORITY" -ge 3 ] && [ "$free_gb" -lt 140 ]; then PRIORITY=2; echo "Pouco disco: limito o acervo à prioridade 2"; fi
if [ "$free_gb" -lt 40 ]; then echo "ERRO: menos de 40 GB livres"; exit 1; fi

echo "--- Python: $(python3 --version)"
python3 -m pip install --user --quiet --disable-pip-version-check "pypdf>=4" certifi cryptography 2>&1 | tail -2 \
  || python3 -m pip install --user --break-system-packages --quiet "pypdf>=4" certifi cryptography 2>&1 | tail -2
python3 - <<'PY'
import sqlite3, pypdf, cryptography
db = sqlite3.connect(":memory:")
db.execute("CREATE VIRTUAL TABLE t USING fts5(x, tokenize='unicode61 remove_diacritics 2')")
print("pypdf", pypdf.__version__, "· cryptography", cryptography.__version__, "· SQLite", sqlite3.sqlite_version, "com FTS5")
PY
[ $? -eq 0 ] || { echo "ERRO: Python sem pypdf/cryptography/FTS5"; exit 1; }

echo "--- [1/4] Mapa da Grã-Bretanha (log: logs/dados/mapa.log)"
( python3 companion/map-pack-builder/build_gb_map.py > "$LOG/mapa.log" 2>&1; echo $? > "$LOG/mapa.exit" ) &
MAP_PID=$!

echo "--- [2/4] Acervo: documentos de texto (log: logs/dados/acervo-texto.log)"
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog.json downloads/library \
  --max-priority 1 > "$LOG/acervo-texto.log" 2>&1
echo "texto: exit $?"

echo "--- [3/4] Acervo: coleções ZIM até prioridade $PRIORITY (log: logs/dados/acervo-zim.log)"
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog.json downloads/library \
  --kinds zim --max-priority "$PRIORITY" > "$LOG/acervo-zim.log" 2>&1
echo "zim: exit $?"
python3 companion/library-builder/fetch.py companion/library-builder/survival-catalog.json downloads/library \
  --kinds apk > "$LOG/apps.log" 2>&1
echo "apps (só download, sem instalar): exit $?"

echo "--- [4/4] Base pesquisável e pacotes assinados (log: logs/dados/pacotes.log)"
python3 companion/library-builder/build_knowledge_pack.py > "$LOG/pacotes.log" 2>&1
echo "pacotes: exit $?"

echo "--- a aguardar o mapa…"
wait $MAP_PID
echo "mapa: exit $(cat "$LOG/mapa.exit" 2>/dev/null)"
python3 - <<'PY' | tee "$LOG/resumo.txt"
import json, pathlib
root = pathlib.Path(".")
st = json.loads((root / "downloads/library/fetch-status.json").read_text())
ok = [e for e in st["entries"].values() if e["status"] == "complete"]
part = [e for e in st["entries"].values() if e["status"] == "partial"]
bad = [e for e in st["entries"].values() if e["status"] == "failed"]
size = sum(f.get("bytes", 0) for e in st["entries"].values() for f in e["files"])
print(f"Acervo: {len(ok)} completas, {len(part)} parciais, {len(bad)} falhadas, {size/1e9:.1f} GB")
for e in bad: print("  FALHOU", e["id"], "|", "; ".join(e["errors"])[:200])
for p in sorted((root / "oracle-ssd").glob("*/pack-manifest.json")):
    m = json.loads(p.read_text()); print(f"Pacote {p.parent.name}: {len(m['packages'])} ficheiros, {sum(x['bytes'] for x in m['packages'])/1e9:.2f} GB")
PY
echo "=== Concluído $(date '+%F %T'). Pode fechar esta janela. ==="
