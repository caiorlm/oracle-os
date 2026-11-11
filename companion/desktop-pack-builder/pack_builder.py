#!/usr/bin/env python3
"""Build and verify immutable Oracle SSD packs with Ed25519 and SHA-256.

Only data file types listed in the plan are accepted. Android still needs its own
verifier and explicit SAF approval before any pack can be installed on a device.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import stat
import tempfile
from pathlib import Path
from typing import Any

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

SCHEMA_VERSION = 1
CHUNK_BYTES = 1024 * 1024
ALLOWED_TYPES = {"gguf", "knowledge", "index", "map", "license", "zim", "places"}
ALLOWED_SUFFIXES = {
    "gguf": {".gguf"},
    "knowledge": {".txt", ".md", ".pdf"},
    "index": {".db", ".sqlite", ".sqlite3"},
    "map": {".pmtiles", ".mbtiles"},
    "license": {".txt", ".md"},
    "zim": {".zim"},
    "places": {".db"},
}


def safe_relative(value: str) -> Path:
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts or "." in path.parts:
        raise ValueError(f"Unsafe pack path: {value!r}")
    if any(part in ("", "\\") for part in path.parts):
        raise ValueError(f"Unsafe pack path: {value!r}")
    return path


def safe_file(base: Path, relative: str) -> Path:
    path = base / safe_relative(relative)
    if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(base.resolve()):
        raise ValueError(f"Missing, symlinked, or escaped input: {relative}")
    return path


def digest_and_size(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_BYTES), b""):
            size += len(chunk)
            digest.update(chunk)
    return digest.hexdigest(), size


def place_file(source: Path, target: Path) -> None:
    """Hard-links large immutable inputs when on the same volume; copies otherwise."""
    try:
        os.link(source, target)
    except OSError:
        shutil.copyfile(source, target)


def load_plan(path: Path) -> dict[str, Any]:
    plan = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(plan, dict) or not isinstance(plan.get("packages"), list):
        raise ValueError("Plan must contain a packages array")
    if not plan["packages"]:
        raise ValueError("Plan contains no packages")
    if not isinstance(plan.get("packId"), str) or not plan["packId"].strip():
        raise ValueError("packId is required")
    if not isinstance(plan.get("version"), str) or not plan["version"].strip():
        raise ValueError("version is required")
    if not isinstance(plan.get("minAndroidApi"), int) or plan["minAndroidApi"] < 28:
        raise ValueError("minAndroidApi must be at least 28")
    return plan


def init_key(private_path: Path, public_path: Path) -> None:
    if private_path.exists() or public_path.exists():
        raise FileExistsError("Key already exists; refusing to replace it")
    private_path.parent.mkdir(parents=True, exist_ok=True)
    public_path.parent.mkdir(parents=True, exist_ok=True)
    key = Ed25519PrivateKey.generate()
    private_bytes = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    fd = os.open(private_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(fd, "wb") as stream:
        stream.write(private_bytes)
    public_path.write_bytes(
        key.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    )


def build(plan_path: Path, source_root: Path, output: Path, private_key_path: Path) -> None:
    plan = load_plan(plan_path)
    if output.exists():
        raise FileExistsError(f"Pack already exists: {output}")
    source_root = source_root.resolve()
    key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise TypeError("Expected an Ed25519 private key")
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix="oracle-pack-", dir=output.parent))
    try:
        packages = []
        seen_ids: set[str] = set()
        seen_paths: set[str] = set()
        for item in plan["packages"]:
            package_id = item["id"]
            item_type = item["type"]
            target_name = item["path"]
            source_name = item["source"]
            if item_type not in ALLOWED_TYPES:
                raise ValueError(f"Unsupported pack type: {item_type}")
            if not isinstance(package_id, str) or not package_id or package_id in seen_ids:
                raise ValueError(f"Invalid or duplicate package id: {package_id!r}")
            target_relative = safe_relative(target_name)
            if target_relative.suffix.lower() not in ALLOWED_SUFFIXES[item_type]:
                raise ValueError(f"Invalid suffix for {item_type}: {target_name}")
            if target_name in seen_paths or target_relative.parts[0] == "signatures":
                raise ValueError(f"Invalid or duplicate target path: {target_name}")
            source = safe_file(source_root, source_name)
            target = stage / target_relative
            target.parent.mkdir(parents=True, exist_ok=True)
            place_file(source, target)
            sha256, size = digest_and_size(target)
            package = {"id": package_id, "type": item_type, "path": target_name, "sha256": sha256, "bytes": size}
            if "minRamMb" in item:
                package["minRamMb"] = int(item["minRamMb"])
            packages.append(package)
            seen_ids.add(package_id)
            seen_paths.add(target_name)

        manifest = {
            "schemaVersion": SCHEMA_VERSION,
            "packId": plan["packId"],
            "version": plan["version"],
            "minAndroidApi": plan["minAndroidApi"],
            "packages": packages,
            "signature": {"algorithm": "Ed25519", "file": "signatures/manifest.sig", "keyId": plan.get("keyId", "oracle-dev")},
        }
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")
        (stage / "pack-manifest.json").write_bytes(manifest_bytes)
        signature_file = stage / "signatures" / "manifest.sig"
        signature_file.parent.mkdir(parents=True, exist_ok=True)
        signature_file.write_bytes(key.sign(manifest_bytes))
        verify(stage, key.public_key())
        stage.rename(output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def verify(pack_dir: Path, public_key: Ed25519PublicKey) -> dict[str, Any]:
    pack_dir = pack_dir.resolve()
    manifest_path = safe_file(pack_dir, "pack-manifest.json")
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported pack schema")
    signature = manifest.get("signature")
    if not isinstance(signature, dict) or signature.get("algorithm") != "Ed25519":
        raise ValueError("Missing Ed25519 signature metadata")
    signature_path = safe_file(pack_dir, signature.get("file", ""))
    try:
        public_key.verify(signature_path.read_bytes(), manifest_bytes)
    except InvalidSignature as error:
        raise ValueError("Invalid manifest signature") from error
    packages = manifest.get("packages")
    if not isinstance(packages, list) or not packages:
        raise ValueError("Empty pack")
    seen_ids: set[str] = set()
    seen_paths: set[str] = set()
    for item in packages:
        package_id = item["id"]
        relative = item["path"]
        item_type = item["type"]
        if item_type not in ALLOWED_TYPES or Path(relative).suffix.lower() not in ALLOWED_SUFFIXES[item_type]:
            raise ValueError(f"Unsupported package type/path: {package_id}")
        if package_id in seen_ids or relative in seen_paths:
            raise ValueError("Duplicate package id or path")
        file_path = safe_file(pack_dir, relative)
        sha256, size = digest_and_size(file_path)
        if sha256 != item["sha256"] or size != item["bytes"]:
            raise ValueError(f"Corrupt package: {package_id}")
        seen_ids.add(package_id)
        seen_paths.add(relative)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    keys = sub.add_parser("init-key")
    keys.add_argument("--private", type=Path, required=True)
    keys.add_argument("--public", type=Path, required=True)
    builder = sub.add_parser("build")
    builder.add_argument("--plan", type=Path, required=True)
    builder.add_argument("--source-root", type=Path, required=True)
    builder.add_argument("--output", type=Path, required=True)
    builder.add_argument("--private", type=Path, required=True)
    checker = sub.add_parser("verify")
    checker.add_argument("--pack", type=Path, required=True)
    checker.add_argument("--public", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "init-key":
        init_key(args.private, args.public)
        print(f"Generated key pair: {args.public}")
    elif args.command == "build":
        build(args.plan, args.source_root, args.output, args.private)
        print(f"Built and verified pack: {args.output}")
    else:
        key = serialization.load_pem_public_key(args.public.read_bytes())
        if not isinstance(key, Ed25519PublicKey):
            raise TypeError("Expected an Ed25519 public key")
        manifest = verify(args.pack, key)
        print(f"Verified {manifest['packId']} {manifest['version']} ({len(manifest['packages'])} packages)")


if __name__ == "__main__":
    main()
