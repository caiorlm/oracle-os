#!/usr/bin/env python3
"""Copy signed Oracle packs to the connected Android phone over adb and verify them there.

  python3 scripts/phone_install.py [--serial SERIAL] [--zim-reserve-gb 4] [--no-zim]

* Map and library packs go to the app's own storage
  (/sdcard/Android/data/com.oracleos.app/files/packs/<pack>/), where the app checks the
  Ed25519 signature and the hashes again before using them.
* ZIM collections go to /sdcard/Kiwix/ (the Kiwix reader looks there), largest-priority first,
  only while the phone keeps --zim-reserve-gb free. Each copied file is re-hashed on the phone
  with sha256sum; a mismatch is deleted and reported.
* kiwix-collections.json tells the app which collections are on the phone.
Nothing is installed or changed besides these files.
"""

from __future__ import annotations

import argparse
import json
import shlex
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.oracleos.app"
PACKS_ON_PHONE = f"/sdcard/Android/data/{PACKAGE}/files/packs"
# Collections live in Oracle's own storage so Oracle can hand them to Kiwix with a read-only
# content link (no "All files access" needed). Older installs used /sdcard/Kiwix; they are moved.
KIWIX_ON_PHONE = f"/sdcard/Android/data/{PACKAGE}/files/kiwix"
KIWIX_OLD = "/sdcard/Kiwix"
# Print pack (dist/impressao): in Oracle's folder for the app, and in Documents so any computer
# reaches it over USB even if the app is gone.
PRINT_LOCAL = ROOT / "dist" / "impressao"
PRINT_ON_PHONE = [f"/sdcard/Android/data/{PACKAGE}/files/impressao", "/sdcard/Documents/Oracle-Impressao"]


def log(message: str) -> None:
    print(message, flush=True)


class Adb:
    def __init__(self, serial: str | None) -> None:
        self.base = ["adb"] + (["-s", serial] if serial else [])

    def run(self, *args: str, check: bool = True) -> str:
        # A loose cable or a locked phone drops adb; wait for the device and retry instead of aborting.
        for attempt in range(1, 9):
            result = subprocess.run(self.base + list(args), capture_output=True, text=True)
            if result.returncode == 0 or not check:
                return result.stdout
            message = result.stderr.strip() or result.stdout.strip()
            if attempt == 8:
                raise RuntimeError(f"adb {' '.join(args)} falhou: {message}")
            log(f"  adb falhou ({message[:80]}); à espera do telemóvel, tentativa {attempt}/8…")
            subprocess.run(self.base + ["wait-for-device"], timeout=3600)
            time.sleep(5)
        return ""

    def shell(self, command: str, check: bool = True) -> str:
        return self.run("shell", command, check=check)

    def free_bytes(self, path: str) -> int:
        out = self.shell(f"df -k {shlex.quote(path)} | tail -1").split()
        return int(out[3]) * 1024

    def size(self, path: str) -> int | None:
        out = self.shell(f"stat -c %s {shlex.quote(path)} 2>/dev/null", check=False).strip()
        return int(out) if out.isdigit() else None

    def sha256(self, path: str) -> str | None:
        out = self.shell(f"sha256sum {shlex.quote(path)} 2>/dev/null", check=False).split()
        return out[0].lower() if out else None


def newest(prefix: str) -> Path | None:
    packs = sorted(p for p in (ROOT / "oracle-ssd").glob(prefix + "-*") if (p / "pack-manifest.json").is_file())
    return packs[-1] if packs else None


def push_verified(adb: Adb, local: Path, remote: str, sha256: str, size: int) -> bool:
    if adb.size(remote) == size and adb.sha256(remote) == sha256:
        log(f"  já no telefone e confere: {local.name}")
        return True
    adb.shell(f"mkdir -p {shlex.quote(str(Path(remote).parent))}")
    log(f"  a copiar {local.name} ({size / 1e6:.0f} MB)…")
    adb.run("push", str(local), remote)
    actual = adb.sha256(remote)
    if actual != sha256:
        adb.shell(f"rm -f {shlex.quote(remote)}", check=False)
        log(f"  ERRO: SHA-256 no telefone não confere para {local.name}; ficheiro removido")
        return False
    log(f"  SHA-256 confirmado no telefone: {local.name}")
    return True


def ram_mb(adb: Adb) -> int:
    for line in adb.shell("cat /proc/meminfo", check=False).splitlines():
        if line.startswith("MemTotal:"):
            return int(line.split()[1]) // 1024
    return 0


def push_pack(adb: Adb, pack: Path) -> bool:
    manifest = json.loads((pack / "pack-manifest.json").read_text())
    remote_dir = f"{PACKS_ON_PHONE}/{pack.name}"
    ram = ram_mb(adb)
    items = []
    for item in manifest["packages"]:
        # The phone reports a little less than its nominal RAM, so allow 10%.
        if item.get("minRamMb") and ram and ram < item["minRamMb"] * 0.9:
            log(f"  {Path(item['path']).name}: precisa de {item['minRamMb']} MB de RAM, telefone tem {ram} MB; não copiado")
            adb.shell(f"rm -f {shlex.quote(remote_dir + '/' + item['path'])}", check=False)
            continue
        items.append(item)
    needed = sum(item["bytes"] for item in items if adb.size(f"{remote_dir}/{item['path']}") != item["bytes"])
    free = adb.free_bytes("/sdcard")
    log(f"Pacote {pack.name}: {needed / 1e9:.2f} GB · livre no telefone {free / 1e9:.1f} GB")
    if needed > free - 1_000_000_000:
        log("  ERRO: espaço insuficiente no telefone para este pacote")
        return False
    ok = True
    for item in items:
        ok &= push_verified(adb, pack / item["path"], f"{remote_dir}/{item['path']}", item["sha256"], item["bytes"])
    # Manifest and signature go last, so the app never sees a manifest without its files.
    adb.shell(f"mkdir -p {shlex.quote(remote_dir + '/signatures')}")
    adb.run("push", str(pack / "signatures/manifest.sig"), f"{remote_dir}/signatures/manifest.sig")
    adb.run("push", str(pack / "pack-manifest.json"), f"{remote_dir}/pack-manifest.json")
    return ok


def push_zims(adb: Adb, reserve_gb: float) -> list[dict]:
    pack = newest("oracle-kiwix-library")
    if pack is None:
        log("Sem pacote Kiwix: nenhuma coleção ZIM descarregada ainda")
        return []
    manifest = json.loads((pack / "pack-manifest.json").read_text())
    catalog = {}
    for name in ("survival-catalog.json", "survival-catalog-med.json"):
        source = ROOT / "companion/library-builder" / name
        if source.is_file():
            catalog.update({e["id"]: e for e in json.loads(source.read_text())["entries"]})
    items = sorted(manifest["packages"], key=lambda i: (catalog.get(i["id"], {}).get("priority", 9), i["bytes"]))
    collections = []
    for item in items:
        local = pack / item["path"]
        remote = f"{KIWIX_ON_PHONE}/{Path(item['path']).name}"
        entry = catalog.get(item["id"], {})
        record = {"id": item["id"], "title": entry.get("title", item["id"]), "file": Path(item["path"]).name,
                  "bytes": item["bytes"], "category": entry.get("category"), "onPhone": False, "verifiedOnPhone": False}
        old = f"{KIWIX_OLD}/{Path(item['path']).name}"
        if adb.size(remote) != item["bytes"] and adb.size(old) == item["bytes"]:
            adb.shell(f"mkdir -p {shlex.quote(KIWIX_ON_PHONE)} && mv {shlex.quote(old)} {shlex.quote(remote)}", check=False)
            log(f"  movido para o armazenamento do Oracle: {record['file']}")
        already = adb.size(remote) == item["bytes"]
        free = adb.free_bytes("/sdcard")
        if not already and item["bytes"] > free - reserve_gb * 1e9:
            log(f"  sem espaço para {record['file']} ({item['bytes'] / 1e9:.1f} GB); fica no Mac/SSD")
        elif push_verified(adb, local, remote, item["sha256"], item["bytes"]):
            record["onPhone"] = record["verifiedOnPhone"] = True
        collections.append(record)
    return collections


def push_print_pack(adb: "Adb") -> None:
    if not (PRINT_LOCAL / "index.json").is_file():
        log("Pacote de impressão ainda não gerado (dist/impressao)")
        return
    files = [f for f in PRINT_LOCAL.rglob("*") if f.is_file()]
    total = sum(f.stat().st_size for f in files)
    for remote in PRINT_ON_PHONE:
        copied = 0
        for f in files:
            target = f"{remote}/{f.relative_to(PRINT_LOCAL).as_posix()}"
            if adb.size(target) == f.stat().st_size and not f.name.endswith(".json"):
                continue
            adb.shell(f"mkdir -p {shlex.quote(target.rsplit('/', 1)[0])}", check=False)
            adb.run("push", str(f), target)
            copied += 1
        log(f"Impressão: {len(files)} PDFs/ficheiros ({total / 1e6:.0f} MB) em {remote} · {copied} atualizados")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial")
    parser.add_argument("--zim-reserve-gb", type=float, default=4.0)
    parser.add_argument("--no-zim", action="store_true")
    args = parser.parse_args()
    adb = Adb(args.serial)
    model = adb.shell("getprop ro.product.model").strip()
    release = adb.shell("getprop ro.build.version.release").strip()
    log(f"Telefone: {model} · Android {release} · livre {adb.free_bytes('/sdcard') / 1e9:.1f} GB")
    adb.shell(f"mkdir -p {PACKS_ON_PHONE}")

    ok = True
    for prefix in ("oracle-gb-map", "oracle-survival-library", "oracle-lite-model-dev", "oracle-models", "oracle-places"):
        pack = newest(prefix)
        if pack is None:
            log(f"Ainda não existe pacote {prefix}-*")
            if prefix not in ("oracle-models", "oracle-places"):
                ok = False
            continue
        pushed = push_pack(adb, pack)
        ok &= pushed
        if pushed:
            # Older versions of the same pack would be scanned and verified for nothing.
            listing = adb.shell(f"ls {PACKS_ON_PHONE}", check=False).split()
            for old in listing:
                if old.startswith(prefix + "-") and old != pack.name:
                    adb.shell(f"rm -rf {shlex.quote(PACKS_ON_PHONE + '/' + old)}", check=False)
                    log(f"  versão antiga removida do telefone: {old}")

    try:
        push_print_pack(adb)
    except Exception as exc:  # paper copies are a bonus; never block the install
        log(f"Pacote de impressão: aviso {exc}")

    if not args.no_zim:
        collections = push_zims(adb, args.zim_reserve_gb)
        if collections:
            listing = ROOT / "logs/instalar/kiwix-collections.json"
            listing.parent.mkdir(parents=True, exist_ok=True)
            listing.write_text(json.dumps(collections, indent=2, ensure_ascii=False) + "\n")
            adb.run("push", str(listing), f"{PACKS_ON_PHONE}/kiwix-collections.json")
            on_phone = [c for c in collections if c["onPhone"]]
            log(f"Kiwix: {len(on_phone)}/{len(collections)} coleções no telefone, "
                f"{sum(c['bytes'] for c in on_phone) / 1e9:.1f} GB")
    log("Pacotes copiados." if ok else "Terminou com avisos (ver acima).")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
