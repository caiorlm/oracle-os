#!/bin/bash
# Oracle OS — identificação de plantas pela foto, offline: Google AIY Vision Plants V1 (2102 espécies,
# iNaturalist, Apache-2.0 — o modelo usado pelo projeto nature-id no GitHub) + descrição, usos,
# comestibilidade, toxicidade e cuidados da Wikipédia offline. Compila, instala e testa com fotos de
# exemplo guardadas na pasta do Oracle (não usa a galeria). Se não compilar, repõe a versão anterior.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/instalar downloads/plants/aiy
exec > >(tee -a logs/instalar/plantas.log) 2>&1
echo "=== Plantas (AIY) — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
cd downloads/plants/aiy
[ -s plants.tar.gz ] || curl -fL --retry 5 -o plants.tar.gz "https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-plants-v1/1/download"
[ -s labelmap.csv ] || curl -fL --retry 5 -o labelmap.csv "https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_plants_V1_labelmap.csv"
tar xzf plants.tar.gz && ls -la
cd "$ROOT"
python3 - <<'PY'
import csv, os, shutil
rows = {int(r["id"]): r["name"] for r in csv.DictReader(open("downloads/plants/aiy/labelmap.csv"))}
os.makedirs("app/src/main/assets/plants", exist_ok=True)
for old in ("plantnet.tflite",):
    p = "app/src/main/assets/plants/" + old
    if os.path.exists(p): shutil.move(p, "downloads/plants/" + old + ".fora-do-apk")
open("app/src/main/assets/plants/labels.txt", "w").write("\n".join(rows.get(i, "") for i in range(max(rows) + 1)) + "\n")
shutil.copy("downloads/plants/aiy/1.tflite", "app/src/main/assets/plants/plants.tflite")
print(f"{len(rows)} rótulos; modelo {os.path.getsize('app/src/main/assets/plants/plants.tflite')/1e6:.1f} MB")
PY
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
code=$?
if [ $code -ne 0 ] && grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: |What went wrong|Could not" -A2 logs/instalar/gradle.log | head -40
  cp logs/instalar/gradle.log logs/instalar/gradle-plantas-falhou.log
  echo "ATENÇÃO: não compilou; a repor a versão anterior"
  mkdir -p companion/fontes/plantas-falhou && cp -R app/src/main companion/fontes/plantas-falhou/main-$(date +%H%M%S)
  cp app/build.gradle.kts companion/fontes/plantas-falhou/
  rm -rf app/src/main/java/com/oracleos/app/feature/plants app/src/main/assets/plants
  tar xf companion/fontes/src-antes-plantas.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command
  code=$?
fi
bash scripts/oracle-plantas-teste2.command
echo "=== Plantas terminado (exit $code) $(date '+%F %T'). Pode fechar esta janela. ==="
