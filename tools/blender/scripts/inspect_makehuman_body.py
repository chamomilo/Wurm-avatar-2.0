"""Create one untouched MPFB body for visual and skeleton diagnostics."""

import bpy
import importlib
import os
import sys


MODULE = "bl_ext.system.mpfb"
PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SYSTEM_ASSETS = os.path.join(PROJECT, "assets", "source", "makehuman", "system-assets")
OUTPUT = os.path.join(PROJECT, "assets", "working", "body", "makehuman-source.blend")


def dynamic_import(suffix, symbol):
    for module_name in list(sys.modules):
        if module_name.endswith(suffix):
            return getattr(importlib.import_module(module_name), symbol)
    raise RuntimeError("MPFB module not found: " + suffix)


bpy.ops.preferences.addon_enable(module=MODULE)
HumanService = dynamic_import("mpfb.services.humanservice", "HumanService")
TargetService = dynamic_import("mpfb.services.targetservice", "TargetService")

macro = TargetService.get_default_macro_info_dict()
macro.update({"gender": 1.0, "muscle": 0.96, "weight": 0.72, "height": 0.5})
basemesh = HumanService.create_human(
    mask_helpers=True,
    detailed_helpers=True,
    extra_vertex_groups=True,
    feet_on_ground=True,
    scale=0.1,
    macro_detail_dict=macro,
)
rig = HumanService.add_builtin_rig(basemesh, "game_engine")
skin_path = os.path.join(
    SYSTEM_ASSETS, "skins", "young_caucasian_male", "young_caucasian_male.mhmat")
HumanService.set_character_skin(skin_path, basemesh, skin_type="GAMEENGINE")

print("AVATAR2_BASEMESH", len(basemesh.data.vertices), len(basemesh.data.polygons))
for name in ("pelvis", "spine_01", "spine_02", "spine_03", "neck_01", "head",
             "thigh_l", "calf_l", "foot_l", "clavicle_l", "upperarm_l", "lowerarm_l"):
    bone = rig.data.bones.get(name)
    if bone:
        print("AVATAR2_SOURCE_BONE", name, tuple(round(v, 5) for v in bone.head_local),
              tuple(round(v, 5) for v in bone.tail_local))

os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=OUTPUT)
print("AVATAR2_SOURCE_BLEND=" + OUTPUT)
