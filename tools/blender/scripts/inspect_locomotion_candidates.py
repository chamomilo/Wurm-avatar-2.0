"""Print posture landmarks for candidate CC0 locomotion clips."""

import bpy
import os
import sys


args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) != 1:
    raise RuntimeError("usage: inspect_locomotion_candidates.py -- source.fbx")

bpy.ops.import_scene.fbx(filepath=os.path.abspath(args[0]),
                         automatic_bone_orientation=False)
rig = next(obj for obj in bpy.context.scene.objects if obj.type == "ARMATURE")
candidates = (
    "A_TPose", "Idle_Loop", "Idle_Talking_Loop",
    "Walk_Loop", "Walk_Formal_Loop", "Jog_Fwd_Loop", "Sprint_Loop")

for fragment in candidates:
    action = next(action for action in bpy.data.actions
                  if action.name.endswith("|" + fragment))
    rig.animation_data_create()
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    frames = sorted({int(action.frame_range[0]),
                     int((action.frame_range[0] + action.frame_range[1]) / 2),
                     int(action.frame_range[1])})
    for frame in frames:
        bpy.context.scene.frame_set(frame)
        values = []
        for name in ("pelvis", "spine_03", "neck_01", "Head",
                     "hand_l", "hand_r", "foot_l", "foot_r"):
            point = rig.pose.bones[name].head
            values.append("%s=[%.3f,%.3f,%.3f]" %
                          (name, point.x, point.y, point.z))
        print("AVATAR2_POSTURE clip=%s frame=%d %s" %
              (fragment, frame, " ".join(values)))
