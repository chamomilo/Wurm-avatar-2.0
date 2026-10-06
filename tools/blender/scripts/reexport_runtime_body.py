"""Re-export an existing Avatar body blend in Wurm's Y-up coordinate space."""

import os
import re
import sys

import bpy


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(args) != 2:
        raise RuntimeError("usage: reexport_runtime_body.py -- source.blend output.dae")
    source, output = map(os.path.abspath, args)
    bpy.ops.wm.open_mainfile(filepath=source)
    rig = next(obj for obj in bpy.context.scene.objects
               if obj.type == "ARMATURE" and obj.name.startswith("Avatar2NativeRig"))
    meshes = [obj for obj in bpy.context.scene.objects
              if obj.type == "MESH"
              and (obj.name.startswith("Avatar2BodyMesh")
                   or obj.name.startswith("Avatar2EyesMesh"))]
    bpy.ops.object.select_all(action="DESELECT")
    rig.select_set(True)
    for mesh in meshes:
        mesh.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.wm.collada_export(
        filepath=output,
        selected=True,
        include_children=False,
        include_armatures=True,
        include_animations=False,
        apply_modifiers=False,
        triangulate=True,
        use_texture_copies=True,
        export_global_forward_selection="-Z",
        export_global_up_selection="Y",
        apply_global_orientation=True,
        keep_bind_info=True,
    )
    with open(output, "r", encoding="utf-8") as stream:
        contents = stream.read()
    contents = re.sub(r'<material id="([^\"]+)-material" name="[^\"]+">',
                      lambda match: '<material id="' + match.group(1)
                      + '-material" name="' + match.group(1) + '">', contents)
    contents = re.sub(r'(material|symbol)="([^\"]+)-material"',
                      lambda match: match.group(1) + '="' + match.group(2) + '"',
                      contents)
    with open(output, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(contents)
    print("AVATAR2_BODY_REEXPORTED=" + output)


if __name__ == "__main__":
    main()
