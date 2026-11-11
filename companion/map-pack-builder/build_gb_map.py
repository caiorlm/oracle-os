#!/usr/bin/env python3
"""Download Scotland, England and Wales from Geofabrik, build one PMTiles, sign an SSD pack.

  python3 companion/map-pack-builder/build_gb_map.py

Each PBF is verified against the Geofabrik .md5 sidecar, its SHA-256 is recorded, and the
schema is pinned to those three files. The PMTiles is accepted only with a valid v3 header,
then copied into oracle-ssd/oracle-gb-map-<date>/ through pack_builder.py (Ed25519 + SHA-256).
Re-running skips finished steps.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "companion/library-builder"))
import fetch  # noqa: E402

REGIONS = ["scotland", "england", "wales"]
GEOFABRIK = "https://download.geofabrik.de/europe/united-kingdom/{region}-latest.osm.pbf"
MAPS = ROOT / "downloads/maps"
SOURCES = MAPS / "gb-sources"
OUTPUT = MAPS / "great-britain.pmtiles"
STAGING = MAPS / "great-britain.build.pmtiles"
TEMPLATE = ROOT / "companion/map-pack-builder/gb-schema.template.yml"
SCHEMA = ROOT / "companion/map-pack-builder/gb-schema.yml"
STATUS = MAPS / "gb-map-status.json"
PLANETILER = ROOT / "downloads/tools/planetiler/planetiler"
PRIVATE_KEY = ROOT / ".oracle-keys/dev-private.pem"
PUBLIC_KEY = ROOT / "data/keys/oracle-dev-public.pem"
PACK_BUILDER = ROOT / "companion/desktop-pack-builder/pack_builder.py"


def memory_flag() -> str:
    try:
        total = int(subprocess.check_output(["sysctl", "-n", "hw.memsize"]).strip())
    except Exception:
        total = 8 << 30
    gigabytes = max(2, min(12, int(total / (1 << 30) * 0.5)))
    return f"--memory={gigabytes}g"


def threads_flag() -> str:
    return f"--threads={max(2, (os.cpu_count() or 4) - 1)}"


def main() -> int:
    SOURCES.mkdir(parents=True, exist_ok=True)
    status = json.loads(STATUS.read_text()) if STATUS.exists() else {}
    pbfs = {}
    for region in REGIONS:
        fetch.log(f"[mapa] {region}")
        url = GEOFABRIK.format(region=region)
        target = fetch.resolve({"resolver": "geofabrik", "url": url})[0]
        record = fetch.fetch_target({"id": region}, target, SOURCES)
        if not record.get("publisherChecksum"):
            raise RuntimeError(f"MD5 da Geofabrik indisponível para {region}; não prossigo sem verificação")
        pbfs[region] = record
        fetch.log(f"  {region}: {record['bytes'] / 1e6:.0f} MB sha256 {record['sha256'][:16]}…")

    # Planetiler reads a single OSM file per run, so each region becomes its own PMTiles;
    # the app draws them together (MapStyle supports several sources).
    template = json.loads(TEMPLATE.read_text(encoding="utf-8"))
    outputs = {}
    for region in REGIONS:
        output = MAPS / f"great-britain-{region}.pmtiles"
        staging = MAPS / f"great-britain-{region}.build.pmtiles"
        done = status.get("regions", {}).get(region, {})
        if output.exists() and done.get("sourceSha256") == pbfs[region]["sha256"] and done.get("pmtilesSha256"):
            fetch.log(f"PMTiles de {region} já gerado para esta fonte")
            outputs[region] = output
            continue
        schema = dict(template)
        schema["sources"] = {"osm": {"type": "osm", "local_path": os.path.relpath(pbfs[region]["path"], ROOT)}}
        schema_path = ROOT / f"companion/map-pack-builder/gb-schema-{region}.yml"
        schema_path.write_text(json.dumps(schema, ensure_ascii=False, indent=1), encoding="utf-8")
        if not PLANETILER.is_file():
            raise FileNotFoundError(f"Planetiler ausente em {PLANETILER}")
        staging.unlink(missing_ok=True)
        command = [str(PLANETILER), "generate-custom", f"--schema={schema_path}", f"--output={staging}",
                   memory_flag(), threads_flag(), "--force", "--maxzoom=14"]
        fetch.log(f"A gerar PMTiles de {region}: " + " ".join(command))
        subprocess.run(command, cwd=ROOT, check=True)
        with staging.open("rb") as stream:
            if stream.read(8) != b"PMTiles\x03":
                raise ValueError("Planetiler não criou um PMTiles v3")
        os.replace(staging, output)
        status.setdefault("regions", {})[region] = {
            "source": {k: pbfs[region][k] for k in ("url", "bytes", "sha256", "publisherChecksum")},
            "sourceSha256": pbfs[region]["sha256"],
            "pmtiles": str(output.relative_to(ROOT)),
            "pmtilesBytes": output.stat().st_size,
            "pmtilesSha256": fetch.sha256_file(output),
            "builtAt": datetime.now(timezone.utc).isoformat(),
            "license": "ODbL-1.0",
        }
        STATUS.write_text(json.dumps(status, indent=2) + "\n")
        fetch.log(f"PMTiles de {region} pronto: {output.stat().st_size / 1e6:.0f} MB")
        outputs[region] = output

    version = datetime.now(timezone.utc).strftime("%Y.%m.%d")
    pack = ROOT / f"oracle-ssd/oracle-gb-map-{version}"
    if pack.exists():
        fetch.log(f"Pacote já existe: {pack.name}")
    else:
        plan = {
            "packId": "oracle-gb-map",
            "version": version,
            "minAndroidApi": 28,
            "keyId": "oracle-dev-2026",
            "packages": [
                *[{"id": f"great-britain-{region}-pmtiles", "type": "map", "source": str(outputs[region].relative_to(ROOT)),
                   "path": f"maps/great-britain-{region}.pmtiles"} for region in REGIONS],
                {"id": "osm-attribution", "type": "license", "source": "data/licenses/OSM-ODbL-NOTICE.txt",
                 "path": "licenses/OSM-ODbL-NOTICE.txt"},
            ],
        }
        plan_path = ROOT / "companion/map-pack-builder/gb-pack-plan.json"
        plan_path.write_text(json.dumps(plan, indent=2) + "\n")
        subprocess.run([sys.executable, str(PACK_BUILDER), "build", "--plan", str(plan_path), "--source-root", str(ROOT),
                        "--output", str(pack), "--private", str(PRIVATE_KEY)], check=True)
        subprocess.run([sys.executable, str(PACK_BUILDER), "verify", "--pack", str(pack), "--public", str(PUBLIC_KEY)],
                       check=True)
        fetch.log(f"Pacote assinado: {pack}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
