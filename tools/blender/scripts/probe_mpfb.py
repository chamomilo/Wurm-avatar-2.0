import bpy
import importlib
import sys


MODULE = "bl_ext.system.mpfb"
result = bpy.ops.preferences.addon_enable(module=MODULE)
print("AVATAR2_M PFB_ENABLE", result)
print("AVATAR2_ADDONS", sorted(bpy.context.preferences.addons.keys()))


def dynamic_import(suffix, symbol):
    for module_name in list(sys.modules):
        if module_name.endswith(suffix):
            module = importlib.import_module(module_name)
            return getattr(module, symbol)
    raise RuntimeError("MPFB module not found: " + suffix)


HumanService = dynamic_import("mpfb.services.humanservice", "HumanService")
TargetService = dynamic_import("mpfb.services.targetservice", "TargetService")

macro = TargetService.get_default_macro_info_dict()
macro["gender"] = 1.0
macro["muscle"] = 0.9
macro["weight"] = 0.58
macro["height"] = 0.5
human = HumanService.create_human(
    mask_helpers=True,
    detailed_helpers=True,
    extra_vertex_groups=True,
    feet_on_ground=True,
    scale=0.1,
    macro_detail_dict=macro,
)
rig = HumanService.add_builtin_rig(human, "game_engine")

print("AVATAR2_HUMAN", human.name, len(human.data.vertices), len(human.data.polygons))
print("AVATAR2_RIG", rig.name, len(rig.data.bones))
print("AVATAR2_BONES", ",".join(sorted(bone.name for bone in rig.data.bones)))
bpy.ops.wm.save_as_mainfile(filepath=bpy.path.abspath("//assets/working/body/mpfb-probe.blend"))
