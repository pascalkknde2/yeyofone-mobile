import hashlib
import importlib.util
from pathlib import Path
import tempfile
import struct
import unittest

spec = importlib.util.spec_from_file_location("build_native", Path(__file__).with_name("build-native.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)

class SourceVerificationTests(unittest.TestCase):
    def test_darwin_padding_normalization_preserves_symbols_and_object_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            table = struct.pack("<I", 8) + struct.pack("<II", 0, 128) + struct.pack("<I", 8)
            hashes = []
            for padding in [b"abc", b"xyz"]:
                payload = table + b"name\0" + padding
                name = b"__.SYMDEF SORTED\0"
                header = (("#1/" + str(len(name))).ljust(16) + "0".ljust(12) + "0".ljust(6) + "0".ljust(6) + "644".ljust(8) + str(len(name)+len(payload)).ljust(10) + "`\n").encode()
                path = Path(directory) / "test.a"
                path.write_bytes(b"!<arch>\n" + header + name + payload + b"object-bytes")
                build.normalize_darwin_archive(path)
                self.assertTrue(path.read_bytes().endswith(b"name\0\0\0\0object-bytes"))
                hashes.append(build.digest(path))
            self.assertEqual(hashes[0], hashes[1])

    def test_verified_cache_works_without_network(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            archive = cache / "source.tar.gz"
            archive.write_bytes(b"verified-source")
            source = {"directory": "source", "sha256": hashlib.sha256(b"verified-source").hexdigest()}
            self.assertEqual(build.fetch(source, cache, True), archive)

    def test_tampered_cache_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            (cache / "source.tar.gz").write_bytes(b"tampered")
            source = {"directory": "source", "sha256": hashlib.sha256(b"verified-source").hexdigest()}
            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                build.fetch(source, cache, True)

    def test_offline_missing_source_does_not_download(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, "Missing cached source"):
                build.fetch({"directory": "missing"}, Path(directory), True)

if __name__ == "__main__": unittest.main()
