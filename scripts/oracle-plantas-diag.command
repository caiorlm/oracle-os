#!/bin/bash
# Diagnóstico: obter o modelo AIY plants (2102 espécies) e ver o HTML de um artigo da Wikipédia offline.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
exec > >(tee logs/instalar/plantas-diag.log) 2>&1
mkdir -p downloads/plants/aiy && cd downloads/plants/aiy
echo "--- tfhub redirect"
curl -sIL "https://tfhub.dev/google/lite-model/aiy/vision/classifier/plants_V1/3?lite-format=tflite" | grep -i "^HTTP\|^location\|content-type\|content-length" | head -20
echo "--- kaggle api"
curl -L -sS -o plants.tar.gz -w "%{http_code} %{content_type} %{size_download}\n" "https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-plants-v1/1/download"
file plants.tar.gz; tar tzf plants.tar.gz 2>/dev/null | head
echo "--- labelmap"
curl -L -sS -o labelmap.csv -w "%{http_code} %{size_download}\n" "https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_plants_V1_labelmap.csv"
head -5 labelmap.csv; wc -l labelmap.csv; grep -i "Bellis perennis\|Taraxacum officinale\|Quercus robur\|Urtica dioica" labelmap.csv
cd "$ROOT"
echo "--- libzim"
python3 -c "import libzim" 2>/dev/null || python3 -m pip install --user --quiet libzim
python3 - <<'PY'
from libzim.reader import Archive
import glob
for f in sorted(glob.glob("downloads/library/**/wikipedia_pt*.zim", recursive=True))[:1] + sorted(glob.glob("downloads/library/**/wikipedia_en_top1m*.zim", recursive=True))[:1]:
    a = Archive(f)
    for path in ("Nymphaea_alba", "Taraxacum_officinale", "A/Nymphaea_alba"):
        if a.has_entry_by_path(path):
            e = a.get_entry_by_path(path); it = e.get_item()
            html = bytes(it.content).decode("utf-8", "replace")
            print("=====", f, path, it.title, len(html))
            print(html[:6000])
            break
PY
