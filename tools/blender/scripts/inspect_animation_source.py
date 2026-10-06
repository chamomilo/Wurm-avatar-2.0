"""Print armatures, bones, actions and strips from a Blender-importable source."""

import bpy
import os
import sys


args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) != 1:
    raise RuntimeError("usage: inspect_animation_source.py -- source.fbx|source.glb")
source = os.path.abspath(args[0])
extension = os.path.splitext(source)[1].lower()
if extension == ".fbx":
    bpy.ops.import_scene.fbx(filepath=source, automatic_bone_orientation=False)
elif extension in (".glb", ".gltf"):
    bpy.ops.import_scene.gltf(filepath=source)
else:
    raise RuntimeError("Unsupported source: " + source)

for obj in bpy.context.scene.objects:
    if obj.type == "ARMATURE":
        print("AVATAR2_SOURCE_ARMATURE", obj.name, "bones", len(obj.data.bones),
              "scale", tuple(round(v, 6) for v in obj.scale))
        for bone in obj.data.bones:
            print("AVATAR2_SOURCE_RIG_BONE", bone.name,
                  "parent=" + (bone.parent.name if bone.parent else "-"))
            if bone.name in {"root", "pelvis", "spine_01", "spine_02", "spine_03",
                             "neck_01", "Head", "clavicle_l", "upperarm_l",
                             "lowerarm_l", "hand_l", "thigh_l", "calf_l",
                             "foot_l", "ball_l"}:
                print("AVATAR2_SOURCE_REST", bone.name,
                      tuple(round(v, 5) for v in bone.head_local),
                      tuple(round(v, 5) for v in bone.tail_local))
        if obj.animation_data:
            print("AVATAR2_SOURCE_ACTIVE_ACTION",
                  obj.animation_data.action.name if obj.animation_data.action else "-")
            for track in obj.animation_data.nla_tracks:
                for strip in track.strips:
                    print("AVATAR2_SOURCE_STRIP", track.name, strip.name,
                          strip.action.name if strip.action else "-",
                          round(strip.frame_start, 3), round(strip.frame_end, 3))

for action in bpy.data.actions:
    print("AVATAR2_SOURCE_ACTION", action.name,
          round(action.frame_range[0], 3), round(action.frame_range[1], 3),
          "slots", len(action.slots))
    if action.name.endswith("|Walk_Loop"):
        for slot in action.slots:
            print("AVATAR2_WALK_SLOT", slot.identifier, slot.target_id_type)
        for fcurve in action.fcurves:
            values = [point.co.y for point in fcurve.keyframe_points]
            if values and max(values) - min(values) > 0.0001:
                print("AVATAR2_WALK_CURVE", fcurve.data_path, fcurve.array_index,
                      len(values), round(min(values), 5), round(max(values), 5))
