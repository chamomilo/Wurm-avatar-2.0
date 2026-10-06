# Avatar 2.0 locomotion

Version 0.1 replaces the three continuous player loops instead of inheriting the
vanilla gait.

| Wurm key | Source clip | Duration | Policy |
|---|---|---:|---|
| `idle` / `idle.p1` | `Idle_Loop` | 2.467 s | relaxed breathing and weight shift |
| `walk` / `walk.p1` | `Walk_Loop` | 1.300 s | heel-to-toe walk with contralateral arm swing |
| `run` / `run.p1` | `Jog_Fwd_Loop` | 0.900 s | dedicated in-place run |

Idle Animation Studio also exposes a comparison group without changing the
gameplay-facing locomotion keys:

| Studio key | Source clip | Duration |
|---|---|---:|
| `gait.walk.relaxed` | alias of `Walk_Loop` | 1.300 s |
| `gait.jog` | alias of `Jog_Fwd_Loop` | 0.900 s |

`Walk_Formal_Loop`, `Sprint_Loop` and `Crouch_Fwd_Loop` are rejected because
their retargeted poses are broken; they are not registered at runtime.

Run `animstudio refresh` after switching to an Avatar 2.0 body, then filter the
catalog by `gait.` to compare the loops.

The source is the CC0 Quaternius Universal Animation Library Standard FBX. The
version-5 Blender batch step uses frame 1 of `Idle_Loop` as a shared neutral
reference. Pelvis, spine, neck and head receive donor motion measured from that
reference and applied on top of the clean MakeHuman bind pose. Clavicles use the
same animated delta in armature/world axes: their local bases differ strongly
between Quaternius and MakeHuman, and a target-local conversion turns shoulder
yaw into an incorrect up/down roll. Arms and legs reproduce the approved donor
bone directions while retaining each native bone's roll and attachment point.
This hybrid is deliberate: a global reference-space conversion on the
differently oriented limb rigs bends elbows and knees sideways, while copying
the whole donor pose deforms the MakeHuman hip, spine, neck and shoulder
junctions.
Horizontal root travel is removed because Wurm owns character position;
vertical and lateral pelvis motion remains so the body still transfers weight.
The output runs at 30 fps.

The jog has a stronger torso swing than the walk and therefore uses a stricter
variant of the same hybrid. Its pelvis-to-head chain receives the neutral delta
in armature/world axes, preventing target-local yaw from becoming a sideways
spine bend. Its clavicles are referenced from the donor rest pose and their
stylised vertical hike is removed while forward/back shoulder rotation remains.
Walk and idle retain their already approved transfer paths.

Flat bone channels are retained because they carry constant reference and
direction corrections that Blender would otherwise omit. Armature-object and
root-motion matrix channels are removed from the final DAE because the legacy
Wurm Collada reader interprets them incorrectly. No post-retarget pelvis or
spine bend is applied: the torso posture comes from the native bind shape plus
the donor's actual animated delta, so there is no artificial correction
junction that can form a lumbar wave.

The client loads only animations exported for the Avatar 2.0 bind pose. Wurm's
323 animation keys remain the gameplay-facing contract, but its WOM matrices are
not a valid fallback: their joint axes and inverse bind pose differ from the
native rig. Version 0.1 contains no dedicated ride, sit, combat or work clips.
The production families and migration order are recorded in
`ANIMATION_CONTRACT.md`.

In-game approval should check foot sliding against Wurm's actual walk/run speeds,
stride length at slopes, weapon/shield clearance and transition timing. Those are
runtime tuning concerns; the source clips and retarget pipeline stay unchanged.
