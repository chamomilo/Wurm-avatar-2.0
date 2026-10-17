"""Copy the CC0 adult skin endpoints without changing pixels, UVs or materials."""
from pathlib import Path
import hashlib
import json
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
rows = []
for sex in ("male", "female"):
    for preset, age in (("young", "young"), ("middle", "middleage"), ("old", "old")):
        directory = ROOT / f"assets/source/makehuman/system-assets/skins/{age}_caucasian_{sex}"
        source = directory / f"{age}_lightskinned_{sex}_diffuse.png"
        material = directory / f"{age}_caucasian_{sex}.mhmat"
        target = ROOT / "assets/runtime/body" / source.name
        with Image.open(source) as image:
            assert image.size == (2048, 2048) and image.mode == "RGB", source
        if target.exists() and preset == "young":
            assert target.read_bytes() == source.read_bytes(), "The approved young skin must remain exact"
        else:
            shutil.copyfile(source, target)
        material_rows = dict(line.split(maxsplit=1) for line in material.read_text().splitlines()
                             if line and not line.startswith("#") and len(line.split(maxsplit=1)) == 2)
        rows.append({"sex": sex, "preset": preset, "source": source.relative_to(ROOT).as_posix(),
                     "runtime": target.relative_to(ROOT).as_posix(), "size": [2048, 2048], "mode": "RGB",
                     "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                     "sourceMaterialDiffuseColor": material_rows["diffuseColor"]})
manifest = {"schema": 1, "license": "CC0-1.0", "pixelTransform": "none; exact source PNG bytes",
            "materialPolicy": "retain the approved Avatar material; do not apply source mhmat colours", "skins": rows}
(ROOT / "assets/metadata/age-skin-prototype.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print("AGE_SKIN_SOURCES_OK six exact CC0 endpoints, 2048x2048 RGB, unchanged young skins")
