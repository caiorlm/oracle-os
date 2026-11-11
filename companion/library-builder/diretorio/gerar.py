"""Gera guias/diretorio-paises.md e app/src/main/assets/radio/emergency-stations.json."""
import json, runpy
from pathlib import Path
HERE = Path(__file__).parent
ROOT = HERE.parents[2]
C = runpy.run_path(str(HERE / "paises.py"))["C"]

md = ["# Diretório de emergência por país — números, alertas, rádio pública e proteção civil",
      "",
      "Guia Oracle OS / Country emergency directory. Dados reunidos em novembro de 2026 (Wikipedia: lista de números de emergência, Cell Broadcast, membros da UER/EBU, defesa civil por país; sites oficiais). Os números mudam: confirme quando puder. Em toda a União Europeia o **112** funciona. Em perigo, ligue primeiro; depois sintonize a rádio pública.",
      "",
      "Em cada país: **Emergência** (números), **Alertas** (sistema de avisos no telemóvel/sirenes), **Rádio** (serviço público que difunde avisos — sintonize em FM ou AM; a frequência muda por região, procure o emissor no Wikipedia offline), **Proteção civil** (autoridade oficial).",
      ""]
region = None
for name, reg, numbers, alerts, radio, civil in C:
    if reg != region:
        region = reg
        md += [f"## {reg}", ""]
    md.append(f"### {name}")
    md.append(f"- Emergência / Emergency: {numbers}")
    if alerts: md.append(f"- Alertas / Alerts: {alerts}")
    if radio: md.append(f"- Rádio pública / Public radio: {radio}")
    if civil: md.append(f"- Proteção civil / Civil protection: {civil}")
    md.append("")
(HERE.parent / "guias" / "diretorio-paises.md").write_text("\n".join(md), encoding="utf-8")

asset = ROOT / "app/src/main/assets/radio/emergency-stations.json"
data = json.loads(asset.read_text(encoding="utf-8"))
detailed = {c["country"]: c for c in data["countries"] if c["country"].startswith("Reino Unido") or c["country"] == "Portugal"}
countries = list(detailed.values())
for c in countries:
    if c["country"].startswith("Reino Unido"):
        c["numbers"] = next(x[2] for x in C if x[0] == "Reino Unido")
        c["alerts"] = "Emergency Alerts (telemóvel)"
    if c["country"] == "Portugal":
        c["numbers"] = next(x[2] for x in C if x[0] == "Portugal")
for name, reg, numbers, alerts, radio, civil in C:
    if name == "Portugal" or name == "Reino Unido":
        continue
    authority = " · ".join(x for x in [f"Proteção civil: {civil}" if civil else "", f"Rádio pública: {radio}" if radio else ""] if x)
    stations = [{"name": radio, "band": "FM / AM", "freq": "ver região", "where": "Frequência muda por região — procure o emissor no Wikipedia offline", "primary": True}] if radio else []
    countries.append({"country": name, "region": reg, "authority": authority or "Ouça as rádios nacionais e locais.",
                      "numbers": numbers, "alerts": alerts, "stations": stations})
data["countries"] = countries
data["updated"] = "2026-11-09"
asset.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(len(countries), "países no JSON;", len(C), "no diretório")
