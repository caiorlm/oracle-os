#!/usr/bin/env python3
"""Wait for pinned Planetiler, build London PMTiles, then sign a development SSD pack.

The source PBF and tool archive are checked before conversion. The map is published
only after a valid PMTiles header and checksum have been recorded.
"""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tarfile
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE_PART = ROOT / "downloads/tools/planetiler_snapshot_darwin_amd64.tar.gz.part"
ARCHIVE = ROOT / "downloads/tools/planetiler_snapshot_darwin_amd64.tar.gz"
ARCHIVE_BYTES = 126_385_377
ARCHIVE_SHA256 = "25e02617cfd9006139ae2ee03cf9bd2bf3659221fa170c69c2a4832bddcc191f"
PBF = ROOT / "downloads/maps/greater-london-261103.osm.pbf"
PBF_SHA256 = "243865504ba6d2d12eb0f9823f13e8bcc7fd45aa1cecf24b3c2818e2ed9f60f4"
OUTPUT = ROOT / "downloads/maps/greater-london.pmtiles"
STAGING = ROOT / "downloads/maps/greater-london.build.pmtiles"
TOOL_DIR = ROOT / "downloads/tools/planetiler"
MAP_PACK = ROOT / "oracle-ssd/oracle-london-map-dev-0.1.0"
PLAN = ROOT / "companion/map-pack-builder/london-pack-plan.json"
SCHEMA = ROOT / "companion/map-pack-builder/london-schema.yml"
PRIVATE_KEY = ROOT / ".oracle-keys/dev-private.pem"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def wait_for_archive() -> Path:
    while True:
        candidate = ARCHIVE if ARCHIVE.exists() else ARCHIVE_PART
        if candidate.exists():
            size = candidate.stat().st_size
            if size > ARCHIVE_BYTES:
                raise ValueError(f"Planetiler archive too large: {size}")
            if size == ARCHIVE_BYTES:
                actual = sha256(candidate)
                if actual != ARCHIVE_SHA256:
                    raise ValueError(f"Planetiler archive SHA-256 mismatch: {actual}")
                if candidate == ARCHIVE_PART:
                    os.replace(candidate, ARCHIVE)
                print("Planetiler archive verified", flush=True)
                return ARCHIVE
        print(f"Waiting for Planetiler archive: {candidate.stat().st_size if candidate.exists() else 0}/{ARCHIVE_BYTES}", flush=True)
        time.sleep(20)


def extract_tool(archive: Path) -> Path:
    TOOL_DIR.mkdir(parents=True, exist_ok=True)
    executable = TOOL_DIR / "planetiler"
    if executable.is_file():
        return executable
    with tarfile.open(archive, "r:gz") as source:
        members = source.getmembers()
        for member in members:
            path = Path(member.name)
            if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk():
                raise ValueError(f"Unsafe Planetiler archive entry: {member.name}")
        source.extractall(TOOL_DIR, members=members)
    matches = [path for path in TOOL_DIR.rglob("planetiler") if path.is_file()]
    if not matches:
        raise FileNotFoundError("Planetiler executable not found after extraction")
    executable = matches[0]
    executable.chmod(executable.stat().st_mode | 0o111)
    return executable


def record_map(path: Path) -> None:
    manifest_path = ROOT / "data/downloads-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    entry = {
        "id": "london-pmtiles-derived",
        "role": "offline-map-pack",
        "relativePath": str(path.relative_to(ROOT)),
        "bytesDownloaded": path.stat().st_size,
        "downloadedSha256": sha256(path),
        "status": "complete",
        "derivedFrom": "osm-greater-london-20261103",
        "generator": "planetiler 0.10.3-SNAPSHOT f7819f99478ba78b271f6f663a1fdb4683dc4836; archive sha256:" + ARCHIVE_SHA256,
        "license": "ODbL-1.0",
    }
    assets = manifest["assets"]
    assets[:] = [item for item in assets if item.get("id") != entry["id"]] + [entry]
    manifest["updatedAt"] = datetime.now(timezone.utc).isoformat()
    manifest["notes"] = [
        "Downloaded model and OSM source URLs are pinned to a Hugging Face commit or a dated Geofabrik extract.",
        "The London PMTiles is derived locally from the pinned OSM PBF using the checked Planetiler archive.",
        "Model license metadata is Apache-2.0. OSM data is ODbL 1.0; redistribution requires attribution and license compliance.",
    ]
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    archive = wait_for_archive()
    if sha256(PBF) != PBF_SHA256:
        raise ValueError("OSM source hash mismatch")
    tool = extract_tool(archive)
    if not OUTPUT.exists():
        STAGING.unlink(missing_ok=True)
        command = [
            str(tool),
            "generate-custom",
            f"--schema={SCHEMA}",
            f"--output={STAGING}",
            "--memory=4g",
            "--threads=4",
            "--force",
        ]
        print("Running:", " ".join(command), flush=True)
        subprocess.run(command, cwd=ROOT, check=True)
        with STAGING.open("rb") as stream:
            if stream.read(8) != b"PMTiles\x03":
                raise ValueError("Planetiler did not create a PMTiles v3 archive")
        os.replace(STAGING, OUTPUT)
    record_map(OUTPUT)
    print(f"PMTiles ready: {OUTPUT} ({OUTPUT.stat().st_size} bytes)", flush=True)
    if PRIVATE_KEY.exists() and not MAP_PACK.exists():
        subprocess.run([
            sys.executable, str(ROOT / "companion/desktop-pack-builder/pack_builder.py"), "build",
            "--plan", str(PLAN), "--source-root", str(ROOT), "--output", str(MAP_PACK),
            "--private", str(PRIVATE_KEY),
        ], check=True)
        print(f"Signed map pack ready: {MAP_PACK}", flush=True)


if __name__ == "__main__":
    main()
