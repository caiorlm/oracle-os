#!/usr/bin/env python3
"""Download pinned ORACLE OS assets with resume, checksums and atomic install.

Run from any directory: python3 scripts/download-models.py [--jobs 3]
The manifest is updated atomically with live status; final files appear only after
size and checksum verification. A failed/interrupted transfer stays as ``.part``.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import threading
import time
from datetime import datetime, timezone
from urllib import error, request


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "data" / "downloads-manifest.json"
LOCK = threading.Lock()
USER_AGENT = "ORACLE-OS-asset-downloader/1.0 (+https://github.com/ggml-org/llama.cpp)"
CHUNK_BYTES = 1024 * 1024
MAX_ATTEMPTS = 5


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def read_manifest() -> dict:
    with MANIFEST.open(encoding="utf-8") as handle:
        return json.load(handle)


def save_manifest(manifest: dict) -> None:
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    manifest["updatedAt"] = now()
    temporary = MANIFEST.with_suffix(MANIFEST.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, MANIFEST)


def update(manifest: dict, asset: dict, **fields: object) -> None:
    with LOCK:
        asset.update(fields)
        save_manifest(manifest)


def target_path(asset: dict) -> Path:
    relative = Path(asset["relativePath"])
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError(f"Invalid asset path: {relative}")
    target = (ROOT / relative).resolve()
    if not target.is_relative_to((ROOT / "downloads").resolve()):
        raise ValueError(f"Asset must be under downloads/: {relative}")
    return target


def hashes(path: Path) -> tuple[str, str]:
    sha256 = hashlib.sha256()
    md5 = hashlib.md5()  # Geofabrik publishes MD5 for its dated extract.
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(4 * CHUNK_BYTES), b""):
            sha256.update(chunk)
            md5.update(chunk)
    return sha256.hexdigest(), md5.hexdigest()


def validate(path: Path, asset: dict) -> str:
    expected_size = asset.get("expectedBytes")
    if expected_size is not None and path.stat().st_size != expected_size:
        raise ValueError(f"size {path.stat().st_size} != expected {expected_size}")
    sha256, md5 = hashes(path)
    if asset.get("expectedSha256") and sha256 != asset["expectedSha256"].lower():
        raise ValueError(f"SHA-256 mismatch for {asset['id']}")
    if asset.get("sourceMd5") and md5 != asset["sourceMd5"].lower():
        raise ValueError(f"source MD5 mismatch for {asset['id']}")
    return sha256


def fetch_once(asset: dict, part: Path, progress) -> None:
    current_size = part.stat().st_size if part.exists() else 0
    expected_size = asset.get("expectedBytes")
    if expected_size and current_size > expected_size:
        part.unlink()
        current_size = 0
    headers = {"User-Agent": USER_AGENT}
    if current_size:
        headers["Range"] = f"bytes={current_size}-"
    req = request.Request(asset["url"], headers=headers)
    try:
        response = request.urlopen(req, timeout=90)
    except error.HTTPError as exc:
        if exc.code == 416 and expected_size == current_size:
            return  # All bytes arrived before an interruption; verify below.
        raise
    with response:
        code = response.status
        if current_size:
            if code == 206:
                content_range = response.headers.get("Content-Range", "")
                match = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+|\*)", content_range)
                if not match or int(match.group(1)) != current_size:
                    raise ValueError(f"unexpected Content-Range: {content_range!r}")
                mode = "ab"
            elif code == 200:
                # Server ignored Range. Restart instead of appending duplicate bytes.
                current_size = 0
                mode = "wb"
            else:
                raise ValueError(f"unexpected HTTP status {code} while resuming")
        else:
            if code not in (200, 206):
                raise ValueError(f"unexpected HTTP status {code}")
            mode = "wb"
        with part.open(mode) as output:
            downloaded = current_size
            last_reported = current_size
            last_report_at = time.monotonic()
            while chunk := response.read(CHUNK_BYTES):
                output.write(chunk)
                downloaded += len(chunk)
                if downloaded - last_reported >= 8 * CHUNK_BYTES or time.monotonic() - last_report_at >= 5:
                    progress(downloaded)
                    last_reported = downloaded
                    last_report_at = time.monotonic()
            output.flush()
            os.fsync(output.fileno())
            progress(downloaded)


def download(manifest: dict, asset: dict) -> tuple[str, str]:
    target = target_path(asset)
    target.parent.mkdir(parents=True, exist_ok=True)
    part = target.with_name(target.name + ".part")
    if target.exists():
        try:
            digest = validate(target, asset)
            update(manifest, asset, status="complete", bytesDownloaded=target.stat().st_size,
                   downloadedSha256=digest, checkedAt=now(), error=None)
            return asset["id"], "already complete"
        except (OSError, ValueError) as exc:
            update(manifest, asset, status="invalid", error=str(exc), checkedAt=now())
            return asset["id"], f"INVALID existing file: {exc}"
    for attempt in range(1, MAX_ATTEMPTS + 1):
        update(manifest, asset, status="downloading",
               bytesDownloaded=part.stat().st_size if part.exists() else 0,
               checkedAt=now(), error=None)
        try:
            if not (part.exists() and part.stat().st_size == asset.get("expectedBytes")):
                fetch_once(asset, part, lambda size: update(manifest, asset, bytesDownloaded=size,
                                                            checkedAt=now()))
            digest = validate(part, asset)
            os.replace(part, target)
            update(manifest, asset, status="complete", bytesDownloaded=target.stat().st_size,
                   downloadedSha256=digest, checkedAt=now(), error=None)
            return asset["id"], "complete"
        except (OSError, ValueError, error.URLError) as exc:
            size = part.stat().st_size if part.exists() else 0
            # A full-size file with a bad hash cannot be repaired by Range resume.
            if isinstance(exc, ValueError) and size == asset.get("expectedBytes") and part.exists():
                part.unlink()
                size = 0
            update(manifest, asset, status="retrying" if attempt < MAX_ATTEMPTS else "failed",
                   bytesDownloaded=size, checkedAt=now(), error=f"attempt {attempt}: {exc}")
            if attempt == MAX_ATTEMPTS:
                return asset["id"], f"FAILED: {exc}"
            time.sleep(min(2 ** attempt, 16))
    raise AssertionError("unreachable")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=3, help="parallel downloads (default: 3)")
    parser.add_argument("--asset", action="append", help="asset id; repeat to select several")
    parser.add_argument("--status", action="store_true", help="show manifest and .part progress")
    args = parser.parse_args()
    if args.jobs < 1 or args.jobs > 8:
        parser.error("--jobs must be between 1 and 8")
    manifest = read_manifest()
    assets = manifest["assets"]
    if args.asset:
        assets = [asset for asset in assets if asset["id"] in args.asset]
        missing = set(args.asset) - {asset["id"] for asset in assets}
        if missing:
            parser.error(f"unknown asset ids: {', '.join(sorted(missing))}")
    if args.status:
        for asset in assets:
            target = target_path(asset)
            part = target.with_name(target.name + ".part")
            actual = target.stat().st_size if target.exists() else part.stat().st_size if part.exists() else 0
            expected = asset.get("expectedBytes") or 0
            pct = f"{actual / expected:.1%}" if expected else "?"
            print(f"{asset['id']}: {asset['status']} {actual}/{expected} bytes ({pct})")
        return 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        results = list(pool.map(lambda asset: download(manifest, asset), assets))
    for asset_id, result in results:
        print(f"{asset_id}: {result}", flush=True)
    return 1 if any(result.startswith(("FAILED", "INVALID")) for _, result in results) else 0


if __name__ == "__main__":
    sys.exit(main())
