import bpy
import importlib
import json
import os
import sys


MODULE = "bl_ext.system.mpfb"
bpy.ops.preferences.addon_enable(module=MODULE)


def dynamic_import(suffix, symbol):
    for module_name in list(sys.modules):
        if module_name.endswith(suffix):
            module = importlib.import_module(module_name)
            return getattr(module, symbol)
    raise RuntimeError("MPFB module not found: " + suffix)


HumanService = dynamic_import("mpfb.services.humanservice", "HumanService")
TargetService = dynamic_import("mpfb.services.targetservice", "TargetService")

macro = TargetService.get_default_macro_info_dict()
macro.update({"gender": 1.0, "muscle": 0.9, "weight": 0.58, "height": 0.5})
body = HumanService.create_human(
    mask_helpers=True,
    detailed_helpers=True,
    extra_vertex_groups=True,
    feet_on_ground=True,
    scale=0.1,
    macro_detail_dict=macro,
)

project = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
proxy_path = os.path.join(
    project, "assets", "source", "makehuman", "system-assets", "proxymeshes",
    "male_muscle_13290", "male_muscle_13290.proxy")
rig = HumanService.add_builtin_rig(body, "game_engine")
proxy = HumanService.add_mhclo_asset(
    proxy_path, body, asset_type="Proxymeshes", subdiv_levels=0, material_type="NONE")


def bounds(obj):
    points = [obj.matrix_world @ corner.co for corner in obj.data.vertices]
    return {
        "min": [min(p[i] for p in points) for i in range(3)],
        "max": [max(p[i] for p in points) for i in range(3)],
    }


payload = {
    "basemesh": {"name": body.name, "vertices": len(body.data.vertices), "bounds": bounds(body)},
    "proxy": {"name": proxy.name, "vertices": len(proxy.data.vertices), "bounds": bounds(proxy)},
    "bones": [
        {
            "name": bone.name,
            "parent": bone.parent.name if bone.parent else None,
            "head": list(bone.head_local),
            "tail": list(bone.tail_local),
        }
        for bone in rig.data.bones
    ],
    "proxy_group_names": [group.name for group in proxy.vertex_groups],
    "proxy_groups": {
        group.name: sum(1 for vertex in proxy.data.vertices
                        if any(link.group == group.index and link.weight > 0.0001
                               for link in vertex.groups))
        for group in proxy.vertex_groups
        if group.name in {bone.name for bone in rig.data.bones}
    },
}
print("AVATAR2_M PFB_INSPECT=" + json.dumps(payload, separators=(",", ":")))
print("AVATAR2_PROXY_GROUPS=" + ",".join(group.name for group in proxy.vertex_groups))
