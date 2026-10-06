# Avatar 2.0

Client-side Wurm Unlimited avatar modernization project. The first playable
prototype supplies higher-detail muscular male/female bodies, a native
**Avatar Studio** panel, persistent character profiles and custom human idle,
walk and run locomotion. Armour is deliberately excluded from this visual-approval
slice; its fitting, coverage and material contracts are retained for the next stage.

Open the panel with `/avatar2` or through HUD settings. `/avatar2 reset` restores
the muscular Bodybuilder preset; `/avatar2 save` writes the current profile.

Version 0.2.0 adds an opt-in **Animation Lab**: `/avatar2 lab on` starts the local
authenticated connector, `/avatar2 lab open` opens live controls, and
`/avatar2 lab off` restores ordinary animation. It exposes post-blend corrections,
actual engine matrices, fixed-phase preview and game-only framebuffer captures.
The Python companion measures X/Y/Z responses through the real Wurm FK pipeline.
See [Animation Lab](docs/ANIMATION_LAB.md) for startup, API and current limitations.
Existing production locomotion assets are unchanged in this release.

## Locomotion

The body does not reuse Wurm's walk or run. CC0 source motion is retargeted onto
the Avatar 2.0 anatomy and exported as in-place Wurm Collada clips: a 1.30-second
relaxed walk, a dedicated 0.90-second jog and a 2.47-second breathing idle.
Pelvis translation, spine counter-rotation, arm swing, knee flex and foot roll are preserved. Wurm
continues to control actual world movement. Vanilla animation matrices are not
applied to the anatomical rig; unsupported states remain on a safe Avatar pose
until their dedicated replacements are ready. Version 0.1 deliberately ships
only these three master clips; riding, sitting, combat and work actions are later
animation tasks. Idle Animation Studio also registers `gait.walk.relaxed` and
`gait.jog` for comparison. The rejected `walk formal`, `sprint` and `crouch`
clips are deliberately not registered. The active `walk` key uses the approved
relaxed walk, while `run` uses the corrected jog. The hybrid retarget keeps the pelvis, spine and
neck in MakeHuman's native reference space. Clavicle deltas are transferred in
armature/world axes so the walk's shoulder yaw remains forward/back motion
instead of becoming an up/down shoulder roll; arm and leg bones follow the
approved donor limb directions from the source clip. The stronger jog motion
uses world-axis torso deltas and levelled clavicle directions so its yaw remains
a natural running twist instead of becoming a sideways spine bend or shoulder
hike. This avoids both the old
deformed torso/shoulder silhouette and the sideways arms and knees created by
applying one conversion rule to every bone.

## Compatibility contract

Avatar 2.0 keeps Wurm's player scale, world origin and gameplay state contract,
but it no longer injects Wurm's 91-bone skeleton into the body. Each body retains
its native 53-bone deform rig. A single non-deforming `NullCamera0` leaf socket
provides Wurm's eye anchor, and future equipment uses a separate socket/weight
adapter. Height is deliberately limited to 96-104% in the UI, while shipping
runtime bodies must satisfy the stricter metrics in
`assets/metadata/vanilla-scale-contract.properties`.

The runtime replaces Wurm's broken four-slot weight normalization while an
Avatar 2.0 body is inside the Collada importer. This prevents the importer from
overwriting the first skin weight of the following vertex, which creates
missing-looking triangular cracks around bending joints. The workaround is
restricted to `avatar2/body/` and does not alter vanilla models or source body
files. Vanilla hair is temporarily hidden for Avatar bodies until the native rig
has a compatible helmet/hair socket; otherwise it is drawn at the model root.

Corrected armour belongs in `assets/working/armor/corrected/<family>/<sex>/` and
the game-ready output in `assets/runtime/armor/corrected/`. Every piece also needs
coverage metadata so upper layers can hide lower geometry instead of merely
pushing it inward.

## Reflections

The material work is based on the proven `armor-material-colors` shader path.
Reflection is evaluated independently for head, shoulders, torso, arms, hands,
legs, feet and shield. It is zero through QL 50, follows a quadratic ramp to QL
95, then remains capped. Gold and Silver cap at 30%; other metallic materials cap
at 16%. The game protocol does not transmit armour QL for remote players, so their
quality must come from a server-side appearance extension or an explicit fallback;
the client never invents a remote quality silently.

## Developer setup

The repository uses Java 8 and pinned Wurm client libraries in `libs/`. Portable
Blender 4.5 LTS and MPFB 2.0.17 are installed under `tools/` and `vendor/` by the
asset setup workflow. Run:

```powershell
$env:JAVA_HOME='C:\projects\tools\temurin8\jdk8u492-b09'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test dist
```

See `docs/ARCHITECTURE.md`, `docs/ANIMATION_CONTRACT.md`, `docs/LOCOMOTION.md`,
`docs/ARMOR_PIPELINE.md` and `docs/THIRD_PARTY_ASSETS.md` for the asset and
licensing contracts.

## License

Project code: GNU LGPL version 3 (LGPL-3.0-only), see LICENSE and COPYING.
Third-party graphical assets retain their separate licenses; see NOTICE.md and
docs/THIRD_PARTY_ASSETS.md. Wurm client libraries in `libs/` are local dependencies
and must not be committed or redistributed. Obtain matching libraries from your
own licensed client installation; consult the Gradle library list.
