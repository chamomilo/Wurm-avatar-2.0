"""Duplicate Quaternius CC0 locomotion onto each body's native rig."""

import bpy
import os
import re
import sys
import math
from mathutils import Matrix, Vector


PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUTPUT_ROOT = os.environ.get(
    "AVATAR2_RETARGET_OUTPUT_ROOT",
    os.path.join(PROJECT, "assets", "runtime", "body", "anim"))
WORKING_ROOT = os.environ.get(
    "AVATAR2_RETARGET_WORKING_ROOT",
    os.path.join(PROJECT, "assets", "working", "animations"))
RETARGET_VERSION = "symmetric-neutral-quiet-chest-idle-v7"


def quiet_idle(target, mapping, reference):
    """Attenuate local motion, keeping breathing chiefly in the chest.

    Work from a snapshot so a changed parent cannot alter a child's measured
    delta. Only the first spine link receives the mild forward posture change;
    descendants inherit it naturally, without rotating limbs twice.
    """
    local = {p.name: (p.parent.matrix.inverted() @ p.matrix
                     if p.parent else p.matrix.copy()) for _, p in mapping}
    if not reference:
        reference.update({name: matrix.copy() for name, matrix in local.items()})
    for _, p in mapping:
        amount = 0.35 if p.name in {"spine_02", "spine_03"} else 0.10
        if p.name == "spine_01":
            amount = 0.15
        ref = reference[p.name]
        rotation = ref.to_quaternion().slerp(local[p.name].to_quaternion(), amount)
        point = ref.translation.lerp(local[p.name].translation, amount)
        result = Matrix.LocRotScale(point, rotation, Vector((1, 1, 1)))
        if p.name == "spine_01":
            parent_rest = p.parent.bone.matrix_local.to_quaternion().to_matrix().to_4x4()
            posture = (parent_rest.inverted()
                       @ Matrix.Rotation(math.radians(3), 4, 'X') @ parent_rest)
            result = posture @ result
            result.translation = point
        p.matrix = p.parent.matrix @ result if p.parent else result
        bpy.context.view_layer.update()

# Donor limb directions retain native bone roll and attachment points. Idle
# walk and run arms are aligned with the native anatomical chest.
# The torso and clavicles must never use this path: doing so copies the donor's
# stylised static silhouette into the MakeHuman skin around the hip, spine,
# neck and shoulder junctions.
DIRECTIONAL_BONES = {
    "upperarm_r", "lowerarm_r", "hand_r",
    "thumb_01_r", "thumb_02_r", "thumb_03_r",
    "index_01_r", "index_02_r", "index_03_r",
    "middle_01_r", "middle_02_r", "middle_03_r",
    "ring_01_r", "ring_02_r", "ring_03_r",
    "pinky_01_r", "pinky_02_r", "pinky_03_r",
    "upperarm_l", "lowerarm_l", "hand_l",
    "thumb_01_l", "thumb_02_l", "thumb_03_l",
    "index_01_l", "index_02_l", "index_03_l",
    "middle_01_l", "middle_02_l", "middle_03_l",
    "ring_01_l", "ring_02_l", "ring_03_l",
    "pinky_01_l", "pinky_02_l", "pinky_03_l",
    "thigh_r", "calf_r", "foot_r", "ball_r",
    "thigh_l", "calf_l", "foot_l", "ball_l",
}

# Quaternius and MakeHuman use different clavicle-local bases.  Applying the
# donor delta on the right maps the walk's anatomical yaw into target-local
# pitch, lifting one shoulder and dropping the other.  Clavicles therefore
# preserve the donor delta in armature/world axes, as does the axial chain.
WORLD_DELTA_BONES = {"clavicle_r", "clavicle_l"}

WORLD_AXIAL_BONES = {
    "Root", "pelvis", "spine_01", "spine_02", "spine_03",
    "neck_01", "head",
}

LEG_BONES = {"thigh_l", "calf_l", "foot_l", "ball_l",
             "thigh_r", "calf_r", "foot_r", "ball_r"}


def symmetric_pose(source, matrices):
    """Keep neutral sagittal posture, remove one-sided stance and axial twist.

    Mirror world/rest deltas rather than bone rotations: left/right bones have
    different rolls. Reflection on both sides of a rotation stays a rotation.
    This is used for the Idle reference and Idle itself, never for walk/run.
    """
    reflection = Matrix.Diagonal((-1.0, 1.0, 1.0))
    deltas = {name: m.to_quaternion()
              @ source.data.bones[name].matrix_local.to_quaternion().inverted()
              for name, m in matrices.items()}
    result = {}
    for name, m in matrices.items():
        other = (name[:-2] + ('_r' if name.endswith('_l') else '_l')
                 if name.endswith(('_l', '_r')) else name)
        other = other if other in matrices else name
        mirrored = (reflection @ deltas[other].to_matrix()
                    @ reflection).to_quaternion()
        delta = deltas[name].slerp(mirrored, 0.5)
        rotation = delta @ source.data.bones[name].matrix_local.to_quaternion()
        point = m.translation.copy()
        opposite = matrices[other].translation
        point.x = (point.x - opposite.x) * 0.5
        point.y = (point.y + opposite.y) * 0.5
        point.z = (point.z + opposite.z) * 0.5
        result[name] = Matrix.LocRotScale(point, rotation, Vector((1, 1, 1)))
    return result


MOCAP_TO_NATIVE = {
    "root": "Root",
    "pelvis": "pelvis",
    "spine_01": "spine_01",
    "spine_02": "spine_02",
    "spine_03": "spine_03",
    "neck_01": "neck_01",
    "Head": "head",
    "clavicle_r": "clavicle_r",
    "upperarm_r": "upperarm_r",
    "lowerarm_r": "lowerarm_r",
    "hand_r": "hand_r",
    "thumb_01_r": "thumb_01_r",
    "thumb_02_r": "thumb_02_r",
    "thumb_03_r": "thumb_03_r",
    "index_01_r": "index_01_r",
    "index_02_r": "index_02_r",
    "index_03_r": "index_03_r",
    "middle_01_r": "middle_01_r",
    "middle_02_r": "middle_02_r",
    "middle_03_r": "middle_03_r",
    "ring_01_r": "ring_01_r",
    "ring_02_r": "ring_02_r",
    "ring_03_r": "ring_03_r",
    "pinky_01_r": "pinky_01_r",
    "pinky_02_r": "pinky_02_r",
    "pinky_03_r": "pinky_03_r",
    "clavicle_l": "clavicle_l",
    "upperarm_l": "upperarm_l",
    "lowerarm_l": "lowerarm_l",
    "hand_l": "hand_l",
    "thumb_01_l": "thumb_01_l",
    "thumb_02_l": "thumb_02_l",
    "thumb_03_l": "thumb_03_l",
    "index_01_l": "index_01_l",
    "index_02_l": "index_02_l",
    "index_03_l": "index_03_l",
    "middle_01_l": "middle_01_l",
    "middle_02_l": "middle_02_l",
    "middle_03_l": "middle_03_l",
    "ring_01_l": "ring_01_l",
    "ring_02_l": "ring_02_l",
    "ring_03_l": "ring_03_l",
    "pinky_01_l": "pinky_01_l",
    "pinky_02_l": "pinky_02_l",
    "pinky_03_l": "pinky_03_l",
    "thigh_r": "thigh_r",
    "calf_r": "calf_r",
    "foot_r": "foot_r",
    "ball_r": "ball_r",
    "thigh_l": "thigh_l",
    "calf_l": "calf_l",
    "foot_l": "foot_l",
    "ball_l": "ball_l",
}

CLIPS = {
    "idle": ("Idle_Loop", 1, 76),
    "walk": ("Walk_Loop", 1, 41),
    "run": ("Sprint_Loop", 1, 21),
    "run-jog-clean": ("Jog_Fwd_Loop", 1, 29),
    "run-sprint-clean": ("Sprint_Loop", 1, 21),
}


def find_action(fragment):
    suffix = "|" + fragment
    for action in bpy.data.actions:
        if action.name.endswith(suffix):
            return action
    raise RuntimeError("Missing source action: " + fragment)


def import_source(path):
    before = set(bpy.context.scene.objects)
    bpy.ops.import_scene.fbx(filepath=path, automatic_bone_orientation=False)
    imported = [obj for obj in bpy.context.scene.objects if obj not in before]
    armatures = [obj for obj in imported if obj.type == "ARMATURE"]
    if len(armatures) != 1:
        raise RuntimeError("Expected one imported armature, got " + str(len(armatures)))
    return armatures[0], imported


def mapped_in_target_order(target, source):
    source_by_target = {target_name: source_name
                        for source_name, target_name in MOCAP_TO_NATIVE.items()}
    result = []
    for target_bone in target.data.bones:
        source_name = source_by_target.get(target_bone.name)
        if source_name and source.pose.bones.get(source_name):
            result.append((source.pose.bones[source_name],
                           target.pose.bones[target_bone.name]))
    # Pose matrices are assigned in armature space, so parents must be updated
    # before their children regardless of Blender's data-block insertion order.
    result.sort(key=lambda pair: len(pair[1].parent_recursive))
    return result


def source_reference_matrices(source, symmetric=True):
    """Return the donor pose that corresponds to the target bind pose.

    MakeHuman's game rig is authored in a relaxed A-pose while the Quaternius
    armature bind pose is a T-pose.  The old retarget copied absolute donor
    directions and consequently inherited the donor's stylised spine and
    shoulder silhouette.  A conventional rest-delta still lowers the arms
    twice. The shared neutral keeps the first Idle frame's sagittal posture,
    but averages mirrored rest-relative rotations to remove its static twist
    and one-sided stance. Motion is applied in world axes on top of the clean
    native bind pose. The selected Sprint uses this same clean reference.
    """
    source.animation_data_create()
    neutral = find_action("Idle_Loop")
    source.animation_data.action = neutral
    if neutral.slots:
        source.animation_data.action_slot = neutral.slots[0]
    bpy.context.scene.frame_set(1)
    bpy.context.view_layer.update()
    matrices = {pose_bone.name: pose_bone.matrix.copy()
                for pose_bone in source.pose.bones}
    return symmetric_pose(source, matrices) if symmetric else matrices


def reference_relative_translation(source_pose, reference_matrices):
    """Extract only a bone's translation away from the neutral donor pose."""
    reference = reference_matrices[source_pose.name]
    if source_pose.parent:
        reference_parent = reference_matrices[source_pose.parent.name]
        reference_local = reference_parent.inverted_safe() @ reference
        expected = source_pose.parent.matrix @ reference_local
    else:
        expected = reference
    return source_pose.matrix.translation - expected.translation


def retarget_clip(target, source, clip_name, source_fragment, first, last,
                  reference_matrices):
    source.animation_data_create()
    source_action = find_action(source_fragment)
    source.animation_data.action = source_action
    if source_action.slots:
        source.animation_data.action_slot = source_action.slots[0]
    target.animation_data_create()
    target_action = bpy.data.actions.new(name="Avatar2_" + clip_name)
    target_action.use_fake_user = True
    target.animation_data.action = target_action
    scene = bpy.context.scene
    scene.render.fps = 20 if clip_name == "idle" else 30
    scene.frame_start = first
    scene.frame_end = last

    mapping = mapped_in_target_order(target, source)
    for _source_pose, target_pose in mapping:
        target_pose.rotation_mode = "QUATERNION"
    source_leg = (source.data.bones["pelvis"].head_local.z
                  - source.data.bones["ball_l"].head_local.z)
    target_leg = (target.data.bones["pelvis"].head_local.z
                  - target.data.bones["ball_l"].head_local.z)
    root_motion_scale = target_leg / source_leg if source_leg > 0.001 else 1.0

    idle_reference = {}
    for frame in range(first, last + 1):
        scene.frame_set(frame)
        source_matrices = {p.name: p.matrix.copy() for p in source.pose.bones}
        if clip_name == "idle":
            source_matrices = symmetric_pose(source, source_matrices)
        for pose_bone in target.pose.bones:
            pose_bone.matrix_basis.identity()
        bpy.context.view_layer.update()

        for source_pose, target_pose in mapping:
            target_rest = target_pose.bone.matrix_local
            source_matrix = source_matrices[source_pose.name]

            source_reference = reference_matrices[source_pose.name]
            if clip_name == "idle" and target_pose.name in LEG_BONES:
                rotation = (source_matrix.to_quaternion()
                            @ source_reference.to_quaternion().inverted()
                            @ target_rest.to_quaternion()).normalized()
            elif target_pose.name in DIRECTIONAL_BONES:
                # Preserve the approved donor limb direction but retain the
                # target bone's intrinsic roll. This avoids the lateral arm and
                # knee axes produced when a total global delta is conjugated
                # from a differently oriented reference rig.
                source_rest_rotation = source_pose.bone.matrix_local.to_quaternion()
                target_rest_rotation = target_rest.to_quaternion()
                source_axis = source_rest_rotation @ Vector((0.0, 1.0, 0.0))
                target_axis = target_rest_rotation @ Vector((0.0, 1.0, 0.0))
                align_target_to_source = target_axis.rotation_difference(source_axis)
                aligned_target_rotation = (align_target_to_source
                                           @ target_rest_rotation)
                roll_offset = (source_rest_rotation.inverted()
                               @ aligned_target_rotation)
                rotation = (source_matrix.to_quaternion()
                            @ roll_offset).normalized()
                if target_pose.name not in LEG_BONES:
                    # Arm directions must follow the same anatomical chest
                    # reference as the torso, including its sagittal posture.
                    # Absolute donor axes left one elbow 6 cm farther out.
                    source_chest = source_matrices["spine_03"].to_quaternion()
                    source_rest_chest = source.data.bones["spine_03"].matrix_local.to_quaternion()
                    target_chest = target.pose.bones["spine_03"]
                    target_chest_delta = (target_chest.matrix.to_quaternion()
                                          @ target_chest.bone.matrix_local.to_quaternion().inverted())
                    source_chest_delta = source_chest @ source_rest_chest.inverted()
                    rotation = (target_chest_delta @ source_chest_delta.inverted()
                                @ rotation).normalized()
            elif target_pose.name in WORLD_DELTA_BONES:
                world_delta = (
                    source_matrix.to_quaternion()
                    @ source_reference.to_quaternion().inverted())
                rotation = (world_delta
                            @ target_rest.to_quaternion()).normalized()
            elif target_pose.name in WORLD_AXIAL_BONES:
                # Apply axial deltas in anatomical/world axes. Conjugating
                # donor yaw through native bone bases created a constant lean.
                world_delta = (
                    source_matrix.to_quaternion()
                    @ source_reference.to_quaternion().inverted())
                rotation = (world_delta
                            @ target_rest.to_quaternion()).normalized()
            else:
                # Axial bones keep the neutral-reference path that already
                # preserves the clean MakeHuman hip/spine/neck junctions.
                reference_correction = (
                    target_rest.to_quaternion()
                    @ source_reference.to_quaternion().inverted())
                rotation = (reference_correction
                            @ source_matrix.to_quaternion()).normalized()

            if target_pose.parent:
                parent_pose = target_pose.parent.matrix
                parent_rest = target_pose.parent.bone.matrix_local
                head = (parent_pose @ parent_rest.inverted()
                        @ target_rest).translation
            else:
                head = target_rest.translation.copy()
            if target_pose.name in {"Root", "pelvis"}:
                offset = (reference_relative_translation(
                    source_pose, reference_matrices) * root_motion_scale)
                if clip_name == "idle":
                    # Symmetric Idle has no one-sided pelvis offset. Preserve
                    # only its small breathing/weight-shift translation.
                    offset.x = 0.0
                if target_pose.name == "Root":
                    offset.y = 0.0
                head += offset

            target_pose.matrix = Matrix.LocRotScale(
                head, rotation, Vector((1.0, 1.0, 1.0)))
            bpy.context.view_layer.update()
        if clip_name == "idle":
            quiet_idle(target, mapping, idle_reference)
        for _source_pose, target_pose in mapping:
            target_pose.keyframe_insert(data_path="location", frame=frame,
                                        group=target_pose.name)
            target_pose.keyframe_insert(data_path="rotation_quaternion", frame=frame,
                                        group=target_pose.name)
            target_pose.keyframe_insert(data_path="scale", frame=frame,
                                        group=target_pose.name)

    for fcurve in target_action.fcurves:
        for keyframe in fcurve.keyframe_points:
            keyframe.interpolation = "LINEAR"
    target_action["avatar2_source"] = source_action.name
    target_action["avatar2_retarget"] = RETARGET_VERSION
    target_action["avatar2_fps"] = scene.render.fps
    target_action["avatar2_loop"] = True
    return target_action


def export_clip(target, action, output_path, first, last):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    target.animation_data.action = action
    bpy.context.scene.frame_start = first
    bpy.context.scene.frame_end = last
    bpy.ops.object.select_all(action="DESELECT")
    target.select_set(True)
    bpy.context.view_layer.objects.active = target
    bpy.ops.wm.collada_export(
        filepath=output_path,
        selected=True,
        include_children=False,
        include_armatures=True,
        # All 53 native animation bones are marked deforming before export;
        # this excludes the non-deforming NullCamera0 socket, whose flat
        # animation channel trips a bug in Wurm's old Collada parser.
        deform_bones_only=True,
        include_animations=True,
        include_all_actions=False,
        # Blender otherwise drops animation channels that are constant over the
        # clip.  Those channels still contain the essential rest-pose
        # correction between the donor and MakeHuman rigs; Wurm would fall back
        # to the native bind transform and compound a large torso/limb tilt.
        keep_flat_curves=True,
        apply_modifiers=False,
        triangulate=False,
        export_animation_transformation_type_selection="matrix",
        # Keep clip matrices in the same Y_UP coordinate space as the body.
        export_global_forward_selection="-Z",
        export_global_up_selection="Y",
        apply_global_orientation=True,
        keep_bind_info=True,
    )
    strip_wurm_unsafe_channels(output_path)


def strip_wurm_unsafe_channels(output_path):
    """Remove channels that Wurm mistakes for root motion or camera data."""
    with open(output_path, "r", encoding="utf-8") as stream:
        contents = stream.read()
    pattern = re.compile(
        r'\s*<animation id="[^"]*(?:_transform|_Root_pose_matrix|'
        r'_NullCamera0_pose_matrix)"[^>]*>.*?</animation>',
        re.DOTALL)
    contents, removed = pattern.subn("", contents)
    if removed < 2:
        raise RuntimeError(
            "Expected to remove armature and Root animation channels from "
            + output_path + ", removed " + str(removed))
    with open(output_path, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(contents)
    print("AVATAR2_WURM_CHANNELS_STRIPPED", output_path, removed)


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(args) not in (3, 4):
        raise RuntimeError("usage: retarget_locomotion.py -- target.blend source.fbx male|female [idle,walk,run,run-jog-clean,run-sprint-clean]")
    target_blend, source_fbx, sex = [os.path.abspath(value) if index < 2 else value
                                     for index, value in enumerate(args[:3])]
    selected = args[3].split(',') if len(args) == 4 else list(CLIPS)
    if not selected or any(name not in CLIPS for name in selected):
        raise RuntimeError("Unknown clip selection")
    bpy.ops.wm.open_mainfile(filepath=target_blend)
    target = next(obj for obj in bpy.context.scene.objects
                  if obj.type == "ARMATURE" and obj.name.startswith("Avatar2NativeRig"))
    target.animation_data_create()
    target.animation_data.action = None
    for action in list(bpy.data.actions):
        if action.name.startswith("Avatar2_"):
            bpy.data.actions.remove(action)
    animated_bones = set(MOCAP_TO_NATIVE.values())
    # The animation files are skeleton-only resources.  The body DAE owns the
    # camera socket; keeping it in a flat-channel export exposes a long-standing
    # Wurm parser bug (it looks for a time source under the socket's output id).
    # Work on this dedicated locomotion copy, never on the generated body blend.
    camera_bone = target.data.bones.get("NullCamera0")
    if camera_bone is not None:
        bpy.context.view_layer.objects.active = target
        target.select_set(True)
        bpy.ops.object.mode_set(mode="EDIT")
        target.data.edit_bones.remove(target.data.edit_bones["NullCamera0"])
        bpy.ops.object.mode_set(mode="OBJECT")
    for bone in target.data.bones:
        bone.use_deform = bone.name in animated_bones
    source, imported = import_source(source_fbx)
    reference_matrices = source_reference_matrices(source)

    actions = {}
    for clip_name, (fragment, first, last) in CLIPS.items():
        if clip_name not in selected:
            continue
        actions[clip_name] = retarget_clip(
            target, source, clip_name, fragment, first, last,
            reference_matrices)
        output = os.path.join(OUTPUT_ROOT, sex, "avatar2-" + clip_name + ".dae")
        export_clip(target, actions[clip_name], output, first, last)
        print("AVATAR2_ANIMATION_EXPORTED", sex, clip_name, output,
              "frames", first, last)

    for obj in imported:
        bpy.data.objects.remove(obj, do_unlink=True)
    for action in list(bpy.data.actions):
        if action.name.startswith("Armature|Armature|"):
            bpy.data.actions.remove(action)
    bpy.ops.outliner.orphans_purge(do_local_ids=True, do_linked_ids=True,
                                  do_recursive=True)
    preview = "walk" if "walk" in actions else selected[0]
    target.animation_data.action = actions[preview]
    bpy.context.scene.frame_start = CLIPS[preview][1]
    bpy.context.scene.frame_end = CLIPS[preview][2]
    os.makedirs(WORKING_ROOT, exist_ok=True)
    working = os.path.join(WORKING_ROOT, sex + "-locomotion.blend")
    bpy.ops.wm.save_as_mainfile(filepath=working)
    print("AVATAR2_LOCOMOTION_BLEND=" + working)


if __name__ == "__main__":
    main()
