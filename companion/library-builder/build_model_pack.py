#!/usr/bin/env python3
"""Builds the signed pack oracle-models-<version> from the GGUF files fetched with
survival-catalog-ia.json into downloads/models-ia (only entries marked complete, whose SHA-256
matched Hugging Face). Each GGUF keeps minRamMb so the phone can skip models it cannot run."""
import json, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
status = json.loads((ROOT / "downloads/models-ia/fetch-status.json").read_text())
catalog = {e["id"]: e for e in json.loads((Path(__file__).with_name("survival-catalog-ia.json")).read_text())["entries"]}
packages = []
for entry_id, entry in status["entries"].items():
    if entry.get("status") != "complete" or entry_id not in catalog:
        continue
    path = Path(entry["files"][0]["path"])
    if not path.is_absolute():
        path = (ROOT / "downloads/models-ia" / path) if (ROOT / "downloads/models-ia" / path).exists() else ROOT / path
    path = path.resolve()
    packages.append({"id": entry_id, "type": "gguf", "source": str(path.relative_to(ROOT)), "path": f"models/{path.name}",
                     "minRamMb": catalog[entry_id]["minRamMb"]})
if not packages:
    print("Nenhum modelo descarregado ainda"); sys.exit(1)
packages.append({"id": "qwen35-license", "type": "license", "source": "data/licenses/Qwen3.5-0.8B-LICENSE.txt",
                 "path": "licenses/Qwen3.5-LICENSE.txt"})
version = datetime.now(timezone.utc).strftime("%Y.%m.%d.%H%M")
plan = {"packId": "oracle-models", "version": version, "minAndroidApi": 28, "keyId": "oracle-dev-2026", "packages": packages}
plan_path = ROOT / "downloads/models-ia/oracle-models-plan.json"
plan_path.write_text(json.dumps(plan, indent=2) + "\n")
out = ROOT / f"oracle-ssd/oracle-models-{version}"
subprocess.run([sys.executable, str(ROOT / "companion/desktop-pack-builder/pack_builder.py"), "build", "--plan", str(plan_path),
                "--source-root", str(ROOT), "--output", str(out), "--private", str(ROOT / ".oracle-keys/dev-private.pem")], check=True)
print(f"Pacote assinado: {out} ({len(packages) - 1} modelos)")
