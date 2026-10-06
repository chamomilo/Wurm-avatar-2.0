"""Render a reproducible visual QA image from an exported Avatar 2.0 blend."""

import bpy
import math
import os
import sys
from mathutils import Vector


def look_at(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) not in (2, 3, 4, 5):
    raise RuntimeError(
        "usage: render_body_preview.py -- input.blend output.png "
        "[frame] [action] [three-quarter|side]")
source, output = [os.path.abspath(path) for path in args[:2]]
bpy.ops.wm.open_mainfile(filepath=source)
if len(args) >= 4:
    armature = next(obj for obj in bpy.context.scene.objects
                    if obj.type == "ARMATURE" and obj.animation_data)
    action = bpy.data.actions.get(args[3])
    if action is None:
        raise RuntimeError("Missing preview action: " + args[3])
    armature.animation_data.action = action
if len(args) >= 3:
    bpy.context.scene.frame_set(int(args[2]))

for obj in bpy.context.scene.objects:
    if obj.type == "ARMATURE":
        obj.hide_render = True
    if obj.type == "MESH":
        for polygon in obj.data.polygons:
            polygon.use_smooth = True
        for material in obj.data.materials:
            if material is not None:
                # Match Wurm's one-sided rasterization so flipped/degenerated
                # animated triangles cannot hide in Blender QA renders.
                material.use_backface_culling = True

view = args[4] if len(args) == 5 else "three-quarter"
camera_location = ((3.9, 0.0, 1.35) if view == "side"
                   else (2.65, -4.2, 1.42))
bpy.ops.object.camera_add(location=camera_location)
camera = bpy.context.object
look_at(camera, (0.0, 0.0, 0.83))
camera.data.lens = 58
bpy.context.scene.camera = camera

for location, energy, size, color in (
        ((-2.2, -3.0, 3.3), 1150, 3.0, (1.0, 0.87, 0.72)),
        ((2.8, -1.3, 2.0), 850, 2.5, (0.64, 0.76, 1.0)),
        ((0.0, 2.2, 2.8), 1050, 2.0, (1.0, 0.72, 0.52))):
    bpy.ops.object.light_add(type="AREA", location=location)
    light = bpy.context.object
    light.data.energy = energy
    light.data.shape = "DISK"
    light.data.size = size
    light.data.color = color
    look_at(light, (0.0, 0.0, 0.9))

bpy.ops.mesh.primitive_plane_add(size=8, location=(0.0, 0.0, -0.025))
floor = bpy.context.object
floor_material = bpy.data.materials.new("Preview floor")
floor_material.diffuse_color = (0.055, 0.06, 0.07, 1.0)
floor.data.materials.append(floor_material)

scene = bpy.context.scene
scene.render.engine = ("BLENDER_WORKBENCH" if view == "side"
                       else "BLENDER_EEVEE_NEXT")
if view == "side":
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
scene.render.resolution_x = 700
scene.render.resolution_y = 700
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = output
scene.render.film_transparent = False
scene.world.color = (0.025, 0.028, 0.035)
scene.view_settings.look = "AgX - Medium High Contrast"
bpy.ops.render.render(write_still=True)
print("AVATAR2_PREVIEW=" + output)
