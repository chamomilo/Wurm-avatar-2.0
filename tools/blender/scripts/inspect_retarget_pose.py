"""Compare source and retargeted pose matrices at one frame."""

import bpy
import os
import sys
from mathutils import Vector


args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) not in (3, 4):
    raise RuntimeError(
        "usage: inspect_retarget_pose.py -- target.blend source.fbx frame [idle|walk|run]")
target_path, source_path, frame = args[0], args[1], int(args[2])
clip = args[3] if len(args) == 4 else "walk"
bpy.ops.wm.open_mainfile(filepath=os.path.abspath(target_path))
target = next(obj for obj in bpy.context.scene.objects
              if obj.type == "ARMATURE" and obj.name.startswith("Avatar2NativeRig"))
bpy.ops.import_scene.fbx(filepath=os.path.abspath(source_path),
                         automatic_bone_orientation=False)
source = next(obj for obj in bpy.context.selected_objects if obj.type == "ARMATURE")
source_fragment = {"idle": "Idle_Loop", "walk": "Walk_Loop",
                   "run": "Jog_Fwd_Loop"}[clip]
source_action = next(action for action in bpy.data.actions
                     if action.name.endswith("|" + source_fragment))
source.animation_data.action = source_action
if source_action.slots:
    source.animation_data.action_slot = source_action.slots[0]
target.animation_data.action = bpy.data.actions["Avatar2_" + clip]
bpy.context.scene.frame_set(frame)
action = target.animation_data.action
for curve in action.fcurves:
    if any(('pose.bones["' + bone + '"]') in curve.data_path
           for bone in ("Root", "pelvis", "spine_01", "spine_02", "spine_03")):
        print("AVATAR2_FCURVE", curve.data_path, curve.array_index,
              len(curve.keyframe_points),
              round(curve.evaluate(frame), 6))

pairs = (
    ("root", "Root"), ("pelvis", "pelvis"), ("spine_01", "spine_01"),
    ("spine_02", "spine_02"), ("spine_03", "spine_03"),
    ("neck_01", "neck_01"), ("Head", "head"),
    ("clavicle_l", "clavicle_l"), ("upperarm_l", "upperarm_l"),
    ("lowerarm_l", "lowerarm_l"), ("hand_l", "hand_l"),
    ("thigh_l", "thigh_l"), ("calf_l", "calf_l"),
    ("foot_l", "foot_l"), ("ball_l", "ball_l"),
)
for source_name, target_name in pairs:
    source_pose = source.pose.bones[source_name]
    target_pose = target.pose.bones[target_name]
    source_y = source_pose.matrix.to_3x3() @ Vector((0.0, 1.0, 0.0))
    target_y = target_pose.matrix.to_3x3() @ Vector((0.0, 1.0, 0.0))
    print("AVATAR2_POSE_COMPARE", source_name, target_name,
          "source_head", tuple(round(v, 4) for v in source_pose.head),
          "source_y", tuple(round(v, 4) for v in source_y.normalized()),
          "target_head", tuple(round(v, 4) for v in target_pose.head),
          "target_y", tuple(round(v, 4) for v in target_y.normalized()))
    print("AVATAR2_BASIS_COMPARE", source_name, target_name,
          "source_basis", tuple(round(v, 5) for v in source_pose.matrix_basis.to_quaternion()),
          "target_basis", tuple(round(v, 5) for v in target_pose.matrix_basis.to_quaternion()),
          "source_rest_q", tuple(round(v, 5) for v in source_pose.bone.matrix_local.to_quaternion()),
          "target_rest_q", tuple(round(v, 5) for v in target_pose.bone.matrix_local.to_quaternion()))
    print("AVATAR2_REST_POINTS", source_name, target_name,
          "source", tuple(round(v, 4) for v in source_pose.bone.head_local),
          tuple(round(v, 4) for v in source_pose.bone.tail_local),
          "target", tuple(round(v, 4) for v in target_pose.bone.head_local),
          tuple(round(v, 4) for v in target_pose.bone.tail_local))
    target_local = (target_pose.parent.matrix.inverted() @ target_pose.matrix
                    if target_pose.parent else target_pose.matrix.copy())
    print("AVATAR2_TARGET_LOCAL", target_name,
          tuple(round(value, 6) for row in target_local for value in row))
