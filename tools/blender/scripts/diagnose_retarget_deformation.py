"""Create one-frame diagnostic actions with selected retarget chains at rest."""

import bpy
import os
import sys


PRESETS = {
    "rest-arms": (
        "clavicle_", "upperarm_", "lowerarm_", "hand_", "thumb_",
        "index_", "middle_", "ring_", "pinky_",
    ),
    "rest-upper-trunk": (
        "spine_03", "neck_01", "head", "clavicle_", "upperarm_",
        "lowerarm_", "hand_", "thumb_", "index_", "middle_", "ring_",
        "pinky_",
    ),
}


args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) != 3 or args[2] not in PRESETS:
    raise RuntimeError(
        "usage: diagnose_retarget_deformation.py -- input.blend output.blend "
        "rest-arms|rest-upper-trunk")

source_path, output_path, preset = args
bpy.ops.wm.open_mainfile(filepath=os.path.abspath(source_path))
armature = next(obj for obj in bpy.context.scene.objects
                if obj.type == "ARMATURE" and obj.animation_data)
action = bpy.data.actions["Avatar2_idle"].copy()
action.name = "Diagnostic_idle_" + preset.replace("-", "_")
blocked = PRESETS[preset]
for curve in list(action.fcurves):
    if any(('pose.bones["' + name) in curve.data_path for name in blocked):
        action.fcurves.remove(curve)

armature.animation_data.action = action
for pose_bone in armature.pose.bones:
    pose_bone.matrix_basis.identity()
bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(output_path))
print("AVATAR2_DIAGNOSTIC_ACTION=" + action.name)
