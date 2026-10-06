"""Retarget additional Quaternius gait loops onto an Avatar 2.0 rig.

Run from Blender with one of the male/female locomotion .blend files open.
The established Avatar walk/run retargets are used as calibration poses, so
the added variants retain the native rig's bone roll and bind corrections.
"""

import math
import os
import sys
import xml.etree.ElementTree as ET

import bpy
from mathutils import Matrix, Vector


SOURCE_FBX = (r"C:\projects\Avatar 2.0\assets\source\quaternius"
              r"\universal-animation-library-standard"
              r"\Universal Animation Library[Standard]"
              r"\Unity\UAL1_Standard.fbx")

VARIANTS = (
    ("Avatar2_gait_formal", "Armature|Walk_Formal_Loop",
     "Armature|Walk_Loop", "Avatar2_walk", "avatar2-gait-formal.dae"),
    ("Avatar2_gait_sprint", "Armature|Sprint_Loop",
     "Armature|Jog_Fwd_Loop", "Avatar2_run", "avatar2-gait-sprint.dae"),
    ("Avatar2_gait_crouch", "Armature|Crouch_Fwd_Loop",
     "Armature|Walk_Loop", "Avatar2_walk", "avatar2-gait-crouch.dae"),
)


def action_frame(action, fraction):
    start, end = action.frame_range
    return start + (end - start) * fraction


def set_frame(value):
    base = math.floor(value)
    bpy.context.scene.frame_set(base, subframe=value - base)


def evaluated_pose(armature, action, frame, bone_mapping):
    armature.animation_data.action = action
    set_frame(frame)
    bpy.context.view_layer.update()
    rotations = {}
    translations = {}
    for target_name, source_name in bone_mapping.items():
        pose_bone = armature.pose.bones.get(source_name)
        if pose_bone is None:
            continue
        rotations[target_name] = pose_bone.matrix.to_quaternion()
        translations[target_name] = pose_bone.matrix.to_translation()
    return rotations, translations


def clear_pose(armature):
    for pose_bone in armature.pose.bones:
        pose_bone.matrix_basis.identity()
        pose_bone.rotation_mode = 'QUATERNION'
    bpy.context.view_layer.update()


def hierarchy_order(armature):
    def depth(bone):
        result = 0
        parent = bone.parent
        while parent is not None:
            result += 1
            parent = parent.parent
        return result
    return sorted(armature.pose.bones, key=lambda bone: depth(bone.bone))


def key_pose(armature, frame):
    for pose_bone in armature.pose.bones:
        if pose_bone.name == "Root":
            # Kept in the Blender source for a complete pose. The Wurm-facing
            # DAE channel is removed after export because Wurm owns root motion.
            pose_bone.matrix_basis.identity()
        pose_bone.keyframe_insert("location", frame=frame, group=pose_bone.name)
        pose_bone.keyframe_insert("rotation_quaternion", frame=frame,
                                  group=pose_bone.name)
        pose_bone.keyframe_insert("scale", frame=frame, group=pose_bone.name)


def build_variant(target, source, action_name, source_action_name,
                  source_reference_name, target_reference_name, mapping):
    source_action = bpy.data.actions[source_action_name]
    source_reference = bpy.data.actions[source_reference_name]
    target_reference = bpy.data.actions[target_reference_name]

    old = bpy.data.actions.get(action_name)
    if old is not None:
        bpy.data.actions.remove(old)
    result = bpy.data.actions.new(action_name)
    result.use_fake_user = True

    start = int(round(source_action.frame_range[0]))
    end = int(round(source_action.frame_range[1]))
    result.frame_start = start
    result.frame_end = end

    ordered = hierarchy_order(target)
    for frame in range(start, end + 1):
        fraction = 0.0 if end == start else (frame - start) / float(end - start)
        source_rot, source_loc = evaluated_pose(
            source, source_action, frame, mapping)
        reference_source_rot, reference_source_loc = evaluated_pose(
            source, source_reference, action_frame(source_reference, fraction),
            mapping)
        target_rot, target_loc = evaluated_pose(
            target, target_reference, action_frame(target_reference, fraction),
            {name: name for name in mapping})

        target.animation_data.action = result
        set_frame(frame)
        clear_pose(target)

        for pose_bone in ordered:
            name = pose_bone.name
            if name == "Root" or name not in source_rot or name not in target_rot:
                continue
            delta = source_rot[name] @ reference_source_rot[name].inverted()
            desired_rotation = delta @ target_rot[name]
            bpy.context.view_layer.update()
            desired_location = pose_bone.matrix.to_translation()
            if name == "pelvis":
                desired_location = (target_loc[name]
                                    + source_loc[name]
                                    - reference_source_loc[name])
            desired_matrix = Matrix.LocRotScale(
                desired_location, desired_rotation, Vector((1.0, 1.0, 1.0)))
            pose_bone.matrix = desired_matrix
            bpy.context.view_layer.update()

        key_pose(target, frame)

    for curve in result.fcurves:
        for point in curve.keyframe_points:
            point.interpolation = 'LINEAR'
    return result


def remove_wurm_unsafe_channels(path):
    namespace = "http://www.collada.org/2005/11/COLLADASchema"
    ET.register_namespace("", namespace)
    tree = ET.parse(path)
    root = tree.getroot()
    channel_tag = "{%s}channel" % namespace
    animation_tag = "{%s}animation" % namespace

    def prune(parent):
        for child in list(parent):
            if child.tag == animation_tag:
                # Only remove the leaf animation that owns the unsafe channel;
                # the outer action container also contains that descendant.
                targets = [node.get("target", "")
                           for node in child.findall(channel_tag)]
                if any(target == "Avatar2NativeRig/transform"
                       or target.endswith("_Root/transform")
                       for target in targets):
                    parent.remove(child)
                    continue
            prune(child)

    prune(root)
    tree.write(path, encoding="utf-8", xml_declaration=True)


def export_action(target, action, path):
    target.animation_data.action = action
    bpy.context.scene.frame_start = int(round(action.frame_range[0]))
    bpy.context.scene.frame_end = int(round(action.frame_range[1]))
    bpy.context.scene.render.fps = 30
    bpy.ops.object.select_all(action='DESELECT')
    target.hide_set(False)
    target.select_set(True)
    bpy.context.view_layer.objects.active = target
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.wm.collada_export(
        filepath=path,
        check_existing=False,
        apply_modifiers=False,
        selected=True,
        include_children=False,
        include_armatures=True,
        deform_bones_only=False,
        include_animations=True,
        include_all_actions=False,
        export_animation_type_selection='sample',
        sampling_rate=1,
        keep_smooth_curves=False,
        keep_keyframes=True,
        keep_flat_curves=True,
        active_uv_only=True,
        use_texture_copies=False,
        triangulate=False,
        sort_by_name=False,
        export_object_transformation_type_selection='matrix',
        export_animation_transformation_type_selection='matrix',
        open_sim=False,
        limit_precision=False,
        keep_bind_info=False,
    )
    remove_wurm_unsafe_channels(path)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(argv) != 1:
        raise SystemExit("Pass the output directory after --")
    output_directory = os.path.abspath(argv[0])

    target = bpy.data.objects["Avatar2NativeRig"]
    if target.animation_data is None:
        target.animation_data_create()
    existing_objects = set(bpy.data.objects)
    existing_actions = set(bpy.data.actions)
    bpy.ops.wm.fbx_import(filepath=SOURCE_FBX)
    source = bpy.data.objects["Armature"]

    mapping = {bone.name: bone.name for bone in target.pose.bones
               if bone.name in source.pose.bones and bone.name != "Root"}
    if "head" in target.pose.bones and "Head" in source.pose.bones:
        mapping["head"] = "Head"

    generated = []
    for spec in VARIANTS:
        action = build_variant(target, source, *spec[:-1], mapping)
        generated.append((action, spec[-1]))
    for action, filename in generated:
        export_action(target, action, os.path.join(output_directory, filename))
        print("AVATAR2_GAIT_EXPORTED", action.name, filename,
              int(action.frame_range[0]), int(action.frame_range[1]))

    for obj in list(bpy.data.objects):
        if obj not in existing_objects:
            bpy.data.objects.remove(obj, do_unlink=True)
    for action in list(bpy.data.actions):
        if action not in existing_actions and action not in [item[0] for item in generated]:
            bpy.data.actions.remove(action)
    target.animation_data.action = generated[0][0]
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)


if __name__ == "__main__":
    main()
