# Animation Lab (0.2.0)

## Manual startup

Install the distribution ZIP manually with the client closed. No project tool
installs or replaces game files. Start Wurm, enable the Avatar body, show the local
body in third person, then run `/avatar2 lab on` in the mod console. Optional:
`/avatar2 lab open` opens the local dashboard. `/avatar2 lab status` reports the
endpoint/session-file path; `/avatar2 lab off` closes the server and restores
original animation on the next visible animation frame.

The server is OFF by default. It binds an ephemeral port on 127.0.0.1 only, uses a
fresh 256-bit bearer token, rejects foreign Host/Origin headers and exposes no
file-write, code-execution or game-action endpoint. The token is stored in a
temporary session JSON owned by the local OS user. Do not share this file or the
dashboard URL fragment. The normal shutdown/off path removes the session file.
Stale files after a crash are skipped by the companion. After 30 seconds without
an authenticated command/state request, preview/pose/corrections reset to game.
The dashboard keeps the lease alive while open; close it to release control.

## External buffer and telemetry

Python 3, standard library only:

```powershell
python tools/animation-lab/animation_lab.py state
python tools/animation-lab/animation_lab.py command corrections.json
python tools/animation-lab/animation_lab.py probe clavicle_l --clip walk --phase .25 --space local --output axis-calibration.json
python tools/animation-lab/animation_lab.py record --clip run --frames 24 --images --output lab-observation
python tools/animation-lab/animation_lab.py capture lab-frame.png
python tools/animation-lab/animation_lab.py reset
```

The ZIP includes the companion under `mods/avatar2/animation-lab/`. Use its local
path after manual extraction. `--session PATH` selects an exact running client;
otherwise the newest reachable temporary session is used. Never run two clients
or editors on the same buffer during a probe. Revision conflicts abort, rather
than silently overwrite a newer command. Probes/recording restore the prior
buffer only when no other controller has intervened.

Example complete correction replacement:

```json
{
  "preview": {"clip": "walk", "paused": true, "phase": 0.25, "speed": 1},
  "corrections": {
    "clavicle_l": {"space": "local", "rotation": [0, 3, 0], "translation": [0, 0, 0]}
  }
}
```

`POST /command` accepts optional `expectedRevision`, `preview`, `corrections`,
`pose` and `reset`. Unknown fields, unknown clips/bones, camera-socket corrections,
non-finite numbers, invalid rigid matrices and oversized/deep/duplicate-key JSON
are rejected. `corrections` and `pose` replace the entire corresponding map.
Unspecified fields persist. `pose` maps bone names to rigid local 4x4 matrices,
allowing an external retargeter to supply a complete pose without exporting DAE.
Rotations are limited to ±45 degrees per axis, translations to ±0.15 metres per
axis; supplied rigid poses may not scale, shear or reflect.

Matrices are explicit ROW-MAJOR arrays using column vectors (translation at
indices 3, 7, 11). Coordinates are the imported Wurm model space, Y-up, metres;
they are NOT Blender Z-up or game-world coordinates. Rotation delta is
`Rx * Ry * Rz`; each rotation pivots around the bone origin. `local` postmultiplies
the local orientation; `parent` premultiplies it; `model` converts through the
corrected parent chain **before Wurm's heading adjustments**. Translation uses
the selected axes. Model-space commands do not promise a fixed game-world axis.

`GET /state` returns the accepted command revision, native hierarchy/rest matrices,
registered clip names and latest successfully-skinned pose. For each bone:

- `mixedLocal`: unmodified animation matrix after Wurm blending (identity can mean
  a missing channel, not a literal zero pose);
- `baselineLocal/Global/Bind`: original Wurm FK response in this frame;
- `correctedLocal`: matrix supplied for the corrected FK pass;
- `predictedGlobal`: simple parent × local prediction before engine adjustments;
- `engineLocal/Global/Bind`: actual FK output of the engine;
- `skinMatrix`: actual engine bind × original inverse bind.

Blended upper/lower-body clip names, phases, blend fractions and movement speed
modifier accompany the frame. Native locals are restored after skinning; baseline
engine matrices are restored before the next blending pass. This prevents
accumulation and isolates preview from natural motion state. Preview uses a clone
of the registered clip so it does not alter the live clip's interpolation cache.
Only the local world-body model identity is targeted, not paper doll/remote models.

## Observation and calibration

The connector hooks the actual pinned Wurm `ColladaAnimationJob.applyAnimations`.
The normal FK pass first establishes a baseline. A second FK pass is used only
when overriding/correcting the pose, with the same engine heading arguments.
Skinning then runs normally. Telemetry is published only after skinning succeeds.
The capture hook reads the game back buffer after rendering/HUD and before swap;
PNG encoding runs off the render thread. It never captures the desktop. Captures
are on-demand; this is not a high-frame-rate video stream.

`probe` freezes a clip/phase, applies ±3° around each selected axis and computes a
central-difference Jacobian of joint positions and orientations from actual Wurm
bind matrices. It records all input/output poses. This is a **local calibration**
of FK response, not a completed biomechanical/end-to-end human-motion model. It
must be repeated for relevant bones, clips/phases, body variants and heading
states. Differences between prediction and engine output reveal conversion or
engine-heading effects; rendered captures expose skinning/weight artefacts that
FK alone cannot identify. GPU skinning may use dual quaternions: skin matrices
must not be treated as proof of final vertex positions.

The companion waits for two further successfully-skinned poses after a revision
before capture. Screenshot revision/sequence are attached as HTTP headers.
These are pose associations, NOT a GPU synchronization fence or proof of an
exact video frame; keep the preview paused for reliable comparisons.

## Current boundary / next stage

The release provides the live correction/observation and measured-axis probe,
not automatically inferred natural movement. No corrections are shipped enabled;
approved idle/walk/run files remain unchanged. Actual in-game GL capture and
calibration need a manually installed running client. The next step is to measure
shoulders/spine on idle/walk/run, compare both sides at fixed phases and build a
calibrated donor-to-native-rig map. Accepted corrections can then be baked through
the existing Blender exporter and checked with Wurm's importer. Automatic baking
back into Blender Z-up/DAE is deliberately not provided until that inverse mapping
has been measured and round-trip verified. End users will not need the Lab once
the corrected production clips have been baked.
