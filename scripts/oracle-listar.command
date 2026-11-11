#!/bin/bash
# Oracle OS — só leitura: lista as Wikipedias com imagens disponíveis no Kiwix e os modelos Qwen3.5 GGUF
# (tamanho e SHA-256) no Hugging Face. Resultado em logs/dados/listagem.txt
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
OUT=logs/dados/listagem.txt
python3 - > "$OUT" 2>&1 <<'PY'
import urllib.request, re, json, ssl
ctx = ssl.create_default_context()
def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "OracleOS-pack-builder/0.2"})
    return urllib.request.urlopen(req, timeout=60, context=ctx).read().decode("utf-8", "replace")
html = get("https://download.kiwix.org/zim/wikipedia/")
for m in re.finditer(r'href="(wikipedia_(?:en|pt)_[a-z0-9_]*(?:maxi|nopic|mini)_\d{4}-\d{2}\.zim)".*?(\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s+([\d.]+[KMGT]?)', html):
    print("ZIM", m.group(1), m.group(3))
for repo in ["unsloth/Qwen3.5-0.8B-GGUF","unsloth/Qwen3.5-2B-GGUF","unsloth/Qwen3.5-4B-GGUF","unsloth/Qwen3.5-9B-GGUF"]:
    try:
        tree = json.loads(get(f"https://huggingface.co/api/models/{repo}/tree/main"))
        for f in tree:
            if re.search(r"(Q3_K_M|Q4_K_M|Q5_K_M|Q6_K|Q8_0)\.gguf$", f["path"]):
                print("GGUF", repo, f["path"], f.get("size"), (f.get("lfs") or {}).get("oid"))
    except Exception as e:
        print("GGUF", repo, "erro", e)
PY
cat "$OUT" | tail -80
echo "=== Listagem concluída. Pode fechar esta janela. ==="
