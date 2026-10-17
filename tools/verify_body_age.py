"""Check preservation of all 0.13.0 targets; record hashes once from prior banks."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "assets/metadata/body-age-0140.json"


def read_bank(path):
    with path.open("rb") as stream:
        def integer():
            return struct.unpack(">i", stream.read(4))[0]
        def text():
            return stream.read(struct.unpack(">H", stream.read(2))[0]).decode("ascii")
        assert stream.read(4) == b"AVM1"
        body, source = text(), text()
        meshes = {}
        for _ in range(integer()):
            fingerprint, name, count = text(), text(), integer()
            targets = {}
            for _ in range(integer()):
                target = text()
                values = stream.read(count * 8)
                assert len(values) == count * 8
                targets[target] = hashlib.sha256(values).hexdigest()
            meshes[fingerprint] = {"name": name, "components": count, "targets": targets}
        assert not stream.read(1)
    return {"body": body, "sourceDaeSha256": source, "meshes": meshes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--record-baseline", type=Path)
    args = parser.parse_args()
    if args.record_baseline:
        banks = [read_bank(args.record_baseline / (sex + "-bodybuilder.avm")) for sex in ("male", "female")]
        METADATA.write_text(json.dumps({"schema": 1, "releasedVersion": "0.13.0", "banks": banks}, indent=2) + "\n", encoding="utf-8")
    for old in json.loads(METADATA.read_text(encoding="utf-8"))["banks"]:
        current = read_bank(ROOT / "assets/runtime/morph" / (old["body"] + ".avm"))
        assert old["sourceDaeSha256"] == current["sourceDaeSha256"]
        for fingerprint, mesh in old["meshes"].items():
            now = current["meshes"][fingerprint]
            assert mesh["name"] == now["name"] and mesh["components"] == now["components"]
            assert all(now["targets"][name] == sha for name, sha in mesh["targets"].items())
            expected = 4 if old["body"].startswith("male") else 12
            assert len(now["targets"]) == len(mesh["targets"]) + expected
        print("BODY_AGE_RELEASED_TARGETS_EXACT", old["body"])


if __name__ == "__main__":
    main()
