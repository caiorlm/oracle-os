import json
import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives import serialization

from pack_builder import build, init_key, verify


class PackBuilderTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "downloads" / "models").mkdir(parents=True)
        (self.root / "downloads" / "models" / "tiny.gguf").write_bytes(b"GGUFtest bytes")
        self.plan_path = self.root / "plan.json"
        self.plan_path.write_text(json.dumps({
            "packId": "dev-pack", "version": "0.1", "minAndroidApi": 28,
            "packages": [{"id": "model", "type": "gguf", "source": "downloads/models/tiny.gguf", "path": "models/tiny.gguf"}],
        }), encoding="utf-8")
        self.private = self.root / "private.pem"
        self.public = self.root / "public.pem"
        init_key(self.private, self.public)
        self.pack = self.root / "pack"

    def tearDown(self):
        self.temporary.cleanup()

    def public_key(self):
        return serialization.load_pem_public_key(self.public.read_bytes())

    def test_build_detects_file_tampering(self):
        build(self.plan_path, self.root, self.pack, self.private)
        self.assertEqual(verify(self.pack, self.public_key())["packId"], "dev-pack")
        with (self.pack / "models" / "tiny.gguf").open("ab") as stream:
            stream.write(b"tampered")
        with self.assertRaisesRegex(ValueError, "Corrupt package"):
            verify(self.pack, self.public_key())

    def test_detects_manifest_tampering(self):
        build(self.plan_path, self.root, self.pack, self.private)
        manifest = self.pack / "pack-manifest.json"
        manifest.write_bytes(manifest.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "Invalid manifest signature"):
            verify(self.pack, self.public_key())

    def test_rejects_path_escape(self):
        plan = json.loads(self.plan_path.read_text(encoding="utf-8"))
        plan["packages"][0]["path"] = "../tiny.gguf"
        self.plan_path.write_text(json.dumps(plan), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Unsafe pack path"):
            build(self.plan_path, self.root, self.pack, self.private)


if __name__ == "__main__":
    unittest.main()
