"""Build Avatar 2.0 bodies with their native rig plus Wurm adapter sockets."""

import bpy
import importlib
import json
import os
import re
import sys
from mathutils import Vector


MODULE = "bl_ext.system.mpfb"
PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SYSTEM_ASSETS = os.path.join(PROJECT, "assets", "source", "makehuman", "system-assets")
RUNTIME = os.path.join(PROJECT, "assets", "runtime", "body")
WORKING = os.path.join(PROJECT, "assets", "working", "body")
EYES_MHCLO = os.path.join(
    SYSTEM_ASSETS, "eyes", "high-poly", "high-poly.mhclo")


VARIANTS = (
    {"name": "male-athletic", "gender": 1.0, "muscle": 0.76, "weight": 0.48,
     "proxy": None, "skin": "young_caucasian_male"},
    {"name": "male-bodybuilder", "gender": 1.0, "muscle": 1.0, "weight": 0.55,
     "proxy": "male_muscle_13290", "skin": "young_caucasian_male"},
    {"name": "female-athletic", "gender": 0.0, "muscle": 0.68, "weight": 0.44,
     "proxy": None, "skin": "young_caucasian_female"},
    {"name": "female-bodybuilder", "gender": 0.0, "muscle": 1.0, "weight": 0.52,
     "proxy": "female_muscle_13442", "skin": "young_caucasian_female"},
)


def dynamic_import(suffix, symbol):
    for module_name in list(sys.modules):
        if module_name.endswith(suffix):
            return getattr(importlib.import_module(module_name), symbol)
    raise RuntimeError("MPFB module not found: " + suffix)


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for block in (bpy.data.meshes, bpy.data.armatures, bpy.data.materials):
        for item in list(block):
            if item.users == 0:
                block.remove(item)
    # MPFB can leave shape-key datablocks with a stale owner after body masks
    # are baked. Blender exposes those through orphan purge, not collection.remove.
    bpy.ops.outliner.orphans_purge(do_local_ids=True, do_linked_ids=True,
                                  do_recursive=True)


def mesh_bounds(meshes):
    points = [mesh.matrix_world @ vertex.co for mesh in meshes for vertex in mesh.data.vertices]
    return {
        "min": [min(point[index] for point in points) for index in range(3)],
        "max": [max(point[index] for point in points) for index in range(3)],
    }


def fix_wurm_material_symbols(dae_path):
    """Undo Blender 4.x's material-symbol suffix which Wurm cannot resolve."""
    with open(dae_path, "r", encoding="utf-8") as stream:
        contents = stream.read()
    contents = re.sub(r'<material id="([^\"]+)-material" name="[^\"]+">',
                      lambda match: '<material id="' + match.group(1)
                      + '-material" name="' + match.group(1) + '">', contents)
    contents = re.sub(r'(material|symbol)="([^\"]+)-material"',
                      lambda match: match.group(1) + '="' + match.group(2) + '"',
                      contents)
    with open(dae_path, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(contents)


def ensure_native_binding(mesh, rig):
    """Keep the source vertex groups and bind them to their original rig."""
    for modifier in list(mesh.modifiers):
        if modifier.type == "ARMATURE" and modifier.object != rig:
            mesh.modifiers.remove(modifier)
    if not any(modifier.type == "ARMATURE" and modifier.object == rig
               for modifier in mesh.modifiers):
        modifier = mesh.modifiers.new("Avatar2 native rig", "ARMATURE")
        modifier.object = rig
    mesh.parent = rig
    mesh.matrix_parent_inverse = rig.matrix_world.inverted()


def limit_deform_weights(mesh, rig, limit=4):
    """Match Wurm's four-influence skinning limit without editing topology."""
    # Wurm's Collada importer sees weights for every vertex group whose name is
    # also a joint, including native control/intermediate bones whose Blender
    # use_deform flag is false.  Normalize that exact exported set; filtering by
    # use_deform left some vertices at totals as high as 1.175 in Wurm.
    deform_names = {bone.name for bone in rig.data.bones}
    group_by_index = {group.index: group for group in mesh.vertex_groups
                      if group.name in deform_names}
    trimmed = 0
    discarded_fraction_sum = 0.0
    discarded_fraction_max = 0.0
    for vertex in mesh.data.vertices:
        influences = sorted(
            ((element.weight, group_by_index[element.group])
             for element in vertex.groups if element.group in group_by_index),
            key=lambda item: item[0], reverse=True)
        total_before = sum(weight for weight, _group in influences)
        if total_before <= 0.0:
            raise RuntimeError(
                mesh.name + " has an unweighted vertex " + str(vertex.index))
        discarded = sum(weight for weight, _group in influences[limit:])
        if len(influences) > limit:
            trimmed += 1
            discarded_fraction = discarded / total_before
            discarded_fraction_sum += discarded_fraction
            discarded_fraction_max = max(discarded_fraction_max,
                                         discarded_fraction)
        for _weight, group in influences[limit:]:
            group.remove([vertex.index])
        kept = influences[:limit]
        total = sum(weight for weight, _group in kept)
        for weight, group in kept:
            group.add([vertex.index], weight / total, "REPLACE")
    discarded_fraction_mean = (discarded_fraction_sum / trimmed
                               if trimmed else 0.0)
    print("AVATAR2_WEIGHT_LIMIT", mesh.name, "trimmed_vertices", trimmed,
          "max_influences", limit,
          "discarded_mean", round(discarded_fraction_mean, 6),
          "discarded_max", round(discarded_fraction_max, 6))


def add_camera_socket(rig, eyes):
    """Add one non-deforming Wurm leaf socket to the native head."""
    eye_points = [eyes.matrix_world @ vertex.co for vertex in eyes.data.vertices]
    eye_center = sum(eye_points, Vector((0.0, 0.0, 0.0))) / len(eye_points)
    eye_center_local = rig.matrix_world.inverted() @ eye_center
    bpy.ops.object.select_all(action="DESELECT")
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    socket = rig.data.edit_bones.new("NullCamera0")
    socket.head = eye_center_local
    socket.tail = eye_center_local + Vector((0.0, 0.0, 0.04))
    socket.parent = rig.data.edit_bones.get("head")
    socket.use_connect = False
    socket.use_deform = False
    bpy.ops.object.mode_set(mode="OBJECT")
    return socket.name


def build_variant(spec, services):
    HumanService, TargetService, ExportService = services
    clear_scene()
    macro = TargetService.get_default_macro_info_dict()
    macro.update({
        "gender": spec["gender"],
        "muscle": spec["muscle"],
        "weight": spec["weight"],
        "height": 0.5,
    })
    basemesh = HumanService.create_human(
        mask_helpers=True,
        detailed_helpers=True,
        extra_vertex_groups=True,
        feet_on_ground=True,
        scale=0.1,
        macro_detail_dict=macro,
    )
    skin_path = os.path.join(SYSTEM_ASSETS, "skins", spec["skin"], spec["skin"] + ".mhmat")
    HumanService.set_character_skin(skin_path, basemesh, skin_type="GAMEENGINE")
    source_rig = HumanService.add_builtin_rig(basemesh, "game_engine")

    # Eyes are a separate skinned asset in MakeHuman, not part of the naked
    # basemesh. Export them explicitly so the face has sclera, iris and pupil.
    if not os.path.isfile(EYES_MHCLO):
        raise RuntimeError("Missing MakeHuman eyes: " + EYES_MHCLO)
    eyes = HumanService.add_mhclo_asset(
        EYES_MHCLO, basemesh, asset_type="Eyes", subdiv_levels=0,
        material_type="GAMEENGINE")

    # The dedicated CC0 muscle topology is visibly broader and more defined
    # than merely moving MakeHuman's macro slider from 0.76 to 1.0.
    body = basemesh
    if spec["proxy"]:
        proxy_path = os.path.join(
            SYSTEM_ASSETS, "proxymeshes", spec["proxy"], spec["proxy"] + ".proxy")
        body = HumanService.add_mhclo_asset(
            proxy_path, basemesh, asset_type="Proxymeshes", subdiv_levels=0,
            material_type="NONE")
        body.data.materials.clear()
        for material in basemesh.data.materials:
            body.data.materials.append(material)

    # Remove authoring helpers while the source rig is still in its native
    # neutral bind pose. No Wurm bones or bind matrices are introduced here.
    ExportService.bake_modifiers_remove_helpers(
        basemesh, bake_masks=True, bake_subdiv=False,
        remove_helpers=True, also_proxy=False)
    if body != basemesh:
        bpy.data.objects.remove(basemesh, do_unlink=True)

    # At this point only the body/helper and eye assets exist, so mask baking
    # cannot accidentally punch holes for clothing which is not exported.
    for material in body.data.materials:
        material.name = "Avatar2Skin"
    for material in eyes.data.materials:
        material.name = "Avatar2Eyes"
    if body.data.shape_keys:
        bpy.context.view_layer.objects.active = body
        body.select_set(True)
        bpy.ops.object.shape_key_remove(all=True, apply_mix=True)

    source_rig.name = "Avatar2NativeRig"
    source_rig.data.name = "Avatar2NativeRig"
    ensure_native_binding(body, source_rig)
    ensure_native_binding(eyes, source_rig)
    body.name = "Avatar2BodyMesh"
    body.data.name = "Avatar2BodyMesh"
    eyes.name = "Avatar2EyesMesh"
    eyes.data.name = "Avatar2EyesMesh"
    limit_deform_weights(body, source_rig)
    limit_deform_weights(eyes, source_rig)
    add_camera_socket(source_rig, eyes)
    for action in list(bpy.data.actions):
        bpy.data.actions.remove(action)
    bpy.ops.outliner.orphans_purge(do_local_ids=True, do_linked_ids=True,
                                  do_recursive=True)

    os.makedirs(RUNTIME, exist_ok=True)
    os.makedirs(WORKING, exist_ok=True)
    dae_path = os.path.join(RUNTIME, spec["name"] + ".dae")
    blend_path = os.path.join(WORKING, spec["name"] + ".blend")
    bpy.ops.object.select_all(action="DESELECT")
    source_rig.select_set(True)
    body.select_set(True)
    eyes.select_set(True)
    bpy.context.view_layer.objects.active = source_rig
    bpy.ops.wm.collada_export(
        filepath=dae_path,
        selected=True,
        include_children=False,
        include_armatures=True,
        include_animations=False,
        apply_modifiers=False,
        triangulate=True,
        use_texture_copies=True,
        # Wurm consumes COLLADA coordinates directly as X/height/depth and
        # does not rotate a Blender Z_UP asset from the <up_axis> metadata.
        export_global_forward_selection="-Z",
        export_global_up_selection="Y",
        apply_global_orientation=True,
        keep_bind_info=True,
    )
    fix_wurm_material_symbols(dae_path)
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)
    info = {
        "name": spec["name"],
        "vertices": len(body.data.vertices) + len(eyes.data.vertices),
        "triangles": sum(len(poly.vertices) - 2
                         for mesh in (body, eyes) for poly in mesh.data.polygons),
        "joints": len(source_rig.data.bones),
        "skeleton": "makehuman-game-engine-native",
        "meshes": [body.name, eyes.name],
        "bounds": mesh_bounds([body, eyes]),
        "dae": dae_path,
    }
    print("AVATAR2_BODY_BUILT=" + json.dumps(info, separators=(",", ":")))
    return info


def main():
    bpy.ops.preferences.addon_enable(module=MODULE)
    HumanService = dynamic_import("mpfb.services.humanservice", "HumanService")
    TargetService = dynamic_import("mpfb.services.targetservice", "TargetService")
    ExportService = dynamic_import("mpfb.services.exportservice", "ExportService")
    results = [build_variant(spec, (HumanService, TargetService, ExportService))
               for spec in VARIANTS]
    with open(os.path.join(RUNTIME, "body-manifest.json"), "w", encoding="utf-8") as stream:
        json.dump({"schema": 1, "variants": results}, stream, indent=2)


if __name__ == "__main__":
    main()
