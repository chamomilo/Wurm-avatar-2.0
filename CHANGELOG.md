# Changelog

## 0.15.2 — 2026-10-18

- Embed the current complete Chamomilo updater 1.5.0, including the shared native
  Settings editor, language selection and restart reminders.
- Expose Studio visibility and diagnostic logging with live application; save
  profile/resource paths for the next client start. Store user overrides outside
  the installation and retain new release resource paths unless explicitly edited.
- Reduce the installer to the mod descriptor, runtime JAR, resource JAR and
  README.txt. Retain license notices inside the JARs. Replace the project README
  with an illustrated installation and usage guide; remove development diaries
  and temporary work files from the public project.

## 0.15.1 — 2026-10-15

- Refine the 12 native hairstyles with private curved patches, retaining their
  texture mapping and colour. Bound surface displacement to 4 mm and preserve
  triangle projection/winding and exact shared edges; generate each hairstyle copy once.
- Replace the flat under-hair colour with mapped strand regions from the installed
  texture. Clip Avatar's own cranial surface to a finer forehead/temple contour
  with a shallow central peak, recessed temples, small edge variation and tapered
  1.4–3 mm roots to soften the transition to skin.
- Hide head/mouth geometry, eyes, hair and the under-hair surface when free-look
  flies within 0.35 m of the local eye socket. The free-camera exemption exposed
  the inside of the head. Restore the full model as soon as the camera leaves.
- Keep portraits, other model identities and shadows complete. Verify repeated
  camera entry/exit with CPU/GPU modes, height/head-size endpoints and both sexes.
  Profile schema and shared native hairstyle buffers remain unchanged.

## 0.15.0 — 2026-10-14

- Restore native Wurm hairstyles on the local Avatar and paper doll using fitted
  cranial landmarks and the completed head pose. Preserve hairstyle and colour
  selection; refresh the native hair cache when switching Avatar sex or disabling it.
- Fit hair after changes to body shape, height and head size without altering
  skinning joints, shared hairstyle meshes or native helmet coverage rules.
  Hide local hair in first person, retaining portraits, shadows and third person.
- Add a private under-hair surface from Avatar's own head triangles, preserving
  the native texture/colour and closing exposed scalp gaps between hair cards.
- Verify actual native worker CPU/GPU poses, native hairstyle selection/attachment,
  independent world/paper copies, paused-worker publication and Lab rollback.
  Profile schema remains 8. Installed visual review remains pending.

## 0.14.0 — 2026-10-14

- Add Young/Middle/Old adult body presets to Build for both sexes, native Body/Lab,
  browser and companion. Four male and twelve female old grid corners retain
  muscle/fullness/breast interactions on frozen joints. Young preserves every
  released target exactly; Middle interpolates halfway to Old.
- Preset buttons preview body and matching skin in one validated command; skin
  remains independently editable. Persist `agePreset` in schema 8, preserving
  schemas 1–7 and their prior skin/body combinations. Keep Save/Revert, temporary
  trials and reset/off/expiry rollback. Preserve head, eyes, grips, soles and
  posture; no extra age muscle multiplier or gameplay changes.
- Verify legacy morph entry hashes, combined native CPU/baked/GPU poses, triangle
  orientation/normals, protected regions, height endpoints, world/paper save/reload,
  actual Studio input and four-language preset captions. Retain the complete
  canonical updater verification and manual installation ZIP.
- Head appearance remains deferred until hair; installed age review is pending.

## 0.13.0 — 2026-10-13

- Add independent Head size to both sexes and all body shapes in Body, native
  Lab, browser and companion. 0/50/100% gives 92/100/108% of the released head.
  Scale eyes with the face and smoothly join the neck to the unchanged torso;
  keep the crown and chosen standing height fixed.
- Persist exact `headSize` in schema 7 without reinterpreting the old `head`
  placeholder. Preserve old profiles, immediate preview, Save/Revert and explicit
  Lab Save; ordinary trials retain reset/off/expiry rollback.
- Move the completed camera offset with the head while retaining the frozen
  rig and native CPU/GPU skinning. Verify head combinations, native Studio input,
  source/baked geometry, height endpoints, world/paper and camera publication.
- User confirmed the 0.12.1 acceleration and turning fixes feel substantially
  better in the installed game. Final hair/helmet fitting remains a later stage.

## 0.12.1 — 2026-10-13

- Use the authored relaxed Idle as the stationary-turn reference instead of
  the arms-out skeletal bind. Legs use restrained repositioning; arms retain
  90% of relaxed Idle. Check both turn directions over 480 native poses.
- Remove the local non-combat Idle-to-stride publication gate: the first actual
  movement reaches the native gait queue immediately while the worker retains
  its short visual crossfade. Begin .08 cycles into the stride. Swimming,
  flying, mounts, combat/actions, vanilla and explicit Lab playback retain
  their native selection path.
- Add a required regression through the real local selector, Cell dispatcher,
  animation queues and worker at 23/60 fps, including early acceleration.
  Pose/clock checks alone no longer stand in for gameplay startup verification.

## 0.12.0 — 2026-10-13

- Add Shoulder width for both sexes in Studio Body/Lab, browser and companion.
  0% narrow / 50% original / 100% wide; two MPFB local residuals per body on
  frozen joints, with a 3 cm displacement bound. Schema 6 saves exact values;
  legacy placeholder shoulders do not change migrated appearance.
- Bound local gameplay cadence independently of the unbounded distance phase,
  account for body height and begin the gait during early acceleration. Share
  the corrected phase with native footsteps and shorten Idle/stride crossfade
  to 80 ms. Preserve native speed, position, actions and explicit Lab playback.
- Ease the local rendered heading at 220 degrees/second, using shortest arcs.
  Add restrained stationary repositioning steps and up to 10 degrees of torso
  lead. Camera/server heading stays responsive; swimming, mounting and flying
  retain their native heading behavior.
- Restore pre-fairing torso relief in Heavy and muscular/full Build, retaining
  fairing at joint transitions. Preserve Original, eyes, rigs, clips and the
  female cup residuals. Verify source/baked CPU/GPU, profile migration/reload,
  combined controls, native turning/FK, rollback and complete canonical updater.

## 0.11.0 — 2026-10-13

- Add independent female Breast volume in Studio Body/Lab, browser and companion.
  50% keeps the released Build exactly; 0% smaller / 100% larger. Append eight
  MPFB cupsize corners with muscle/fullness interactions, frozen-bind fitting
  and a smooth chest envelope. Preserve all six released targets and male bank.
- Interpolate a bounded three-layer grid and persist the exact value in schema 5.
  Previous schemas retain appearance; male geometry ignores the female choice,
  while raw unsupported binding commands fail before publication. Immediate
  preview, Save/Revert, explicit Lab Save and temporary rollback remain intact.
- Check 100 female combinations, animated fingers, eyes, non-chest isolation,
  surface/normals, CPU/baked/GPU, DQ shader math at height endpoints, saved
  world/paper appearance, native Studio and browser/companion control paths.
  Compare the entire current canonical updater by version/hash/embedded bytes.

## 0.10.0 — 2026-10-13

- Add independent Muscle and Fullness controls in Studio Body/Lab, browser and
  companion through a separate Build mode. Author three MPFB grid corners on
  each approved binding, including the joint macro interaction. Interpolate
  within their convex grid; Original remains muscle 100% / fullness 0%.
- Fit new corners to frozen approved joints, reduce muscular volume around
  bone axes, smooth surfaces offline and protect head/hands/feet/eyes. Retain
  exact released Athletic/Heavy/Older deltas and immutable shared templates.
- Persist exact Build values in schema 4; keep old profiles' visible result.
  Preview immediately, Save/Revert and explicit Lab Save retain all values;
  ordinary Lab trials retain reset/off/expiry rollback and whole-command checks.
- Verify 5×5 grids, animated fingers, CPU/baked/GPU input, native DQ shader math
  at height endpoints, world/paper save/reload, Studio layout/input and browser
  ordering. Compare the complete current canonical updater by version, SHA-256
  and embedded entries. Shield clearance/strap checks remain manual.

## 0.9.1 — 2026-10-13

- Keep native age textures pending GL initialization instead of rejecting their
  initial id=-1 and caching a permanent failure. Native Queue performs the
  upload; both Body and Lab retain Middle/Old selection on CPU/GPU source/baked
  draws. Preserve exact skin PNGs, materials, eyes and four resident uploads.
- Reproduce the 0.9.0 failure with the actual ResourceTextureLoader and PNG
  preparation off the GL thread. Substitute only final upload/submission, then
  verify all four pending textures reach the queue and survive saved appearance.
- Restore full Blend slider range when converting a Shape: absorb the prior
  amount into its shares and start at full common amount without a visual jump.
  The edited share owns its percentage; scale other shares only when needed to
  fit the remaining weight. A 100% share reaches the complete selected endpoint.
- Retain schema 3, old-profile appearance, Save/Revert, temporary rollback and
  whole-command validation. Check native Studio/browser input and profile poses.
  Compare the entire current canonical updater by version, SHA-256 and entries.

## 0.9.0 — 2026-10-13

- Add bounded Blend of the existing Athletic, Heavy and Older endpoints with
  independent shares and a common amount in native Studio Body/Lab, browser
  Lab and companion. Above 100% total, contributions share the available weight;
  below 100%, the remaining weight stays Original. Retain endpoint selection.
- Convert an endpoint to Blend without a visible change. Preview immediately;
  persist exact shares in schema 3, restore them in world/paper models, and
  preserve schema-1/2 appearance. Explicit Lab Save promotes all shares while
  retaining temporary motion; ordinary trials retain reset/off/expiry rollback.
- Verify mixed surface transitions, normalized normals, source/baked CPU/GPU,
  both sexes, height endpoints, save/reload, Studio input/layout and isolation.
- Embed complete canonical updater 1.5.0 / Chamomilo UI 0.4.11 and compare its
  version, SHA-256 and exact embedded entries before packaging. Shield
  clearance/strap probes remain manual.

## 0.8.3 — 2026-10-12

- Fair local surface ridges on Athletic, Heavy and Older endpoints for both
  sexes. MPFB fitting onto the fixed muscle proxy produced abrupt shoulder,
  hip and calf transitions; paired offline surface passes reduce those creases
  while preserving the stronger shape contrast. Recalculate endpoint normals.
- Keep the approved Original body, topology, UVs, bind, weights and clips.
  Pin head/neck, hands/fingers and feet through the existing influences. No
  smoothing or extra per-frame work is added to runtime transformation.
- Reproduce the old shoulder crease with a native topology test; check local
  dihedral angles at 25/50/75/100%, source/baked CPU/GPU output, both sexes,
  authored contrast, fingers, height, save/reload and native walking poses.
  Include the surface report and provenance in the manual distribution.
- Compare and embed the complete current canonical updater 1.4.2 / Chamomilo
  UI 0.4.11 by version, SHA-256 and exact entries before packaging.

## 0.8.2 — 2026-10-12

- Preview Body controls immediately while dragging or selecting; keep edits
  unsaved until Save and let Revert restore the last saved appearance. Retain
  Apply as an optional refresh. Reuse the current rig for appearance revisions
  instead of reloading the model on each slider update.
- Make Studio Save in Lab explicitly keep the selected shape, amount, physical
  height and skin. Preserve temporary motion/pose commands and the visible
  appearance; ordinary Lab edits still never write a profile. Reject unsupported
  torso width/depth trials instead of silently dropping them during Save.
- Store floats losslessly so saving/reloading keeps the precise slider values.
  Check immediate preview, failed-save rollback, native Lab Save input, unchanged
  source geometry/height, world/paper output and reset/reload for both sexes.
- Record the user's installed 0.8.1 portrait/shape review. Compare and embed the
  complete current canonical updater 1.3.3 / Chamomilo UI 0.4.11 before packaging.

## 0.8.1 — 2026-10-12

- Fix height-dependent native paper-doll framing after persisted height began
  affecting the portrait model. Scale full-body/head camera translations with
  the last completed Avatar pose; preserve rotation, offsets and vanilla models.
  Healthbar code and its crop remain unchanged.
- Request an independent Avatar paper-doll model from Wurm's native loader so
  applying a profile does not deform its cached import. Preserve native copy
  policy for ordinary bodies.
- Reauthor Athletic and Older endpoints with leaner MPFB macros and controlled
  muscle-volume fitting around the approved bone axes using the same weights.
  Older includes the oldest adult macro and reduced muscle volume. Recalculate
  normals offline and keep 0–100% interpolation, base topology, bind and clips.
- Reproduce the previous portrait drift and check fixed native renderPaperDoll
  dispatch across both sexes, 13–20 dirt, full/head views and rotations, including
  unfinished-worker reads, rollback and vanilla isolation. Check stronger shape
  endpoints, interpolation, fingers, source/baked meshes and CPU/GPU skinning.
- Compare the complete canonical updater 1.3.2 / Chamomilo UI 0.4.11 by version,
  SHA-256 and exact entries before packaging. Shield checks remain manual.

## 0.8.0 — 2026-10-12

- Persist authored Shape, amount, physical height and adult skin in profile
  schema 2. Restore applied appearance without Lab on both local world and
  paper-doll models; retain neutral cached templates and remote copies.
- Add permanent Body controls and a separate Lab tab in Avatar Studio. Apply
  previews the draft, Save persists it, Revert restores the saved profile, and
  temporary Lab reset/off/expiry returns to the applied profile. Save never
  imports Lab trials and clears their overrides.
- Read schema-1 profiles without modifying files or inventing prior trial
  settings. Keep the native-height sentinel and clamp explicit heights to
  1.3–2.0 metres. Preserve legacy preset metadata during normalization.
- Keep appearance caches scoped to two local models, rollback snapshots weak
  with name/matrix values, and age textures bounded to four extra uploads.
- Verify save/reload, native CPU/GPU source/baked poses and skin dispatch, Studio
  input/layout, rollback and old-profile compatibility. Installed restart,
  paper-doll appearance, stairs and equipment review remain manual checks.
- Compare and embed the complete canonical updater 1.3.1 / Chamomilo UI 0.4.10
  by version, SHA-256 and exact entries. Keep shield probes and their stale
  reports outside routine distribution packaging.

## 0.7.2 — 2026-10-12

- Use the validated Bodybuilder binding as the single male/female base. Remove
  the separate Athletic/Bodybuilder selectors and the old muscle/mass threshold
  that could switch topology after a sex or profile change.
- Keep Athletic, heavier and older as temporary Shape choices on those bases.
  Legacy profile values remain readable, with their settings and files preserved;
  an old Athletic profile now renders on the shared Bodybuilder base.
- Package exactly two body meshes with matching mappings, manifest and animation
  bindings. Retain historical Athletic controls in authoring sources and tests.
- Record the user's successful installed 0.7.1 review on both sexes, all three
  authored shapes and all skin choices. Profile persistence for these temporary
  trials remains a later step.

## 0.7.1 — 2026-10-11

- Fix age skin selection when the native CPU/GPU source draw receives a null
  texture override and resolves its diffuse texture from the material map.
  Add regressions for this actual renderer path, alongside baked draws.
- Apply the first authored shape at 100% in Avatar Studio. Preserve a chosen
  blend when switching between authored shapes; Original still clears it.
- Enforce Studio's 650×600 minimum when Wurm restores an older 650×540 window,
  keeping skin controls separate from the footer. Preserve native collapse.
- Embed the complete canonical updater 1.3.1 / Chamomilo UI 0.4.10; compare its
  version, SHA-256 and exact entries before packaging. Shield runtime checks
  remain a separate manual task.

## 0.7.0 — 2026-10-11

- Add temporary adult Young/Middle/Old diffuse skin choices for both sexes and
  both body presets in native Avatar Studio, browser Lab and the companion.
  Keep body morphology independent and persistent profiles unchanged.
- Substitute the local world's draw texture in native CPU/GPU and baked paths,
  retaining shared materials, eyes, UVs, geometry and other model copies.
  Restore Original on Reset, Lab off, body change and lease timeout.
- Ship six exact CC0 skin endpoints with provenance and resource-pack hash
  checks; reuse four extra resident textures. Verify native import/copy/bake/draw
  dispatch and Studio controls; installed colour/GL review remains pending.
- Embed the complete canonical updater 1.3.0 / Chamomilo UI 0.4.10 and verify
  its version, SHA-256 and exact embedded entries before packaging.
- Remove the expensive shield clearance/strap probe from routine ZIP builds;
  retain its separate manual task, as requested by the user.

## 0.6.11 — 2026-10-10

- Refresh the complete canonical Chamomilo updater 1.2.7, including its reviewed
  Chamomilo UI 0.4.10 SDK. Replace the whole embedded module on SHA-256 drift
  even when the updater version is unchanged.
- Retain all compact artwork, bundled fonts and licenses; verify exact updater
  and UI entry sets and bytes in the mod JAR and installable ZIP.

## 0.6.10 — 2026-10-10

- Translate the pictured large metal shield and both native straps together
  by 20 mm towards the elbow during Walk/Idle. Use the completed forearm
  direction at every height; keep the existing panel rotation and body pose.
- Check the panel and rear strap against the whole arm and the front strap
  against the arm outside the gripping hand. The closed native fist may
  contact its handle; record this contact separately rather than forcing the
  handle to float clear of the fingers. Retain front-anchor and rear-gap checks.
- Retain the complete canonical updater 1.2.6 and exact JAR/ZIP byte checks.

## 0.6.9 — 2026-10-10

- Embed the complete canonical updater 1.2.6 with no Off choice. The updater
  is the only language selector for capable mods and applies a concrete shared
  language at startup and after every choice. Retain exact module/hash checks.



## 0.6.8

- Bring the metal shield's front loop closer to the natural fist, following
  the current fingers instead of the old half-turned hand landmark. Retain
  the accepted arm/wrist pose and native item dimensions.
- Turn the panel 3 degrees about its front loop to bring the rear loop towards
  the forearm. Check both native strap meshes as well as the panel, across
  13–20 dirt and all four bodies.
- Check actual arm/hand mesh clearance at all 11,520 walking poses. Keep the
  circular arm proxies in the report as diagnostics; close-fitting loops are
  judged against the actual surface. Tighten the front anchor limit to 21 mm.
- Embed and verify the complete canonical Chamomilo updater 1.2.4, including
  its SHA-256 and exact JAR/ZIP entries.

## 0.6.7

- Remove the shield forearm/fist's 180-degree roll introduced in 0.6.6. Keep
  the accepted elbow and bend the natural wrist towards the grip loop.
- Aim the forearm from the current sampled finger placement without stretching
  bones. Keep the shield's 0.6.6 panel orientation and placement, including
  small metal's 5-degree upper-edge tilt, instead of moving it away from the body.
- Apply the base arm pose to all shields. Check actual arm/hand clearance and
  front-strap separation for the three metal shields at 13–20 dirt and through
  stopping transitions. Require the restored palm direction as a regression
  against the reversed fist.

## 0.6.6

- Fix large, medium and small metal shields floating beside the closed fist.
  Replace fixed outward displacement and one-axis strap fitting with a native
  front-strap anchor following the current fingers in all three dimensions.
- Turn the shield forearm towards the panel and keep the wrist straight in the
  common carry for all shields. Preserve the accepted elbow/forearm directions,
  native finger animation, item size and small metal's outward upper-edge tilt.
  Counter the forearm roll in the rigid shield attachment to retain panel orientation.
- Keep the common shield hold in Idle and through walk/idle crossfades. Derive
  the socket counter-rotation from the actual native blended arm rotation,
  avoiding a linear-angle estimate during transitions. Preserve native Run,
  Sprint and empty-handed animation.
- Require 1,536 actual arm-mesh/front-strap grip checks across all three metal
  shields, four bodies and every height from 13 to 20 dirt, alongside the 11,520
  native arm/leg clearance poses. Reject a strap more than 12 mm from the hand
  surface or a front anchor more than 20 mm from the phalange midpoint.
  Check 480 additional native walk/idle blended poses at 13 and 20 dirt for arm
  clearance and front-strap contact.

## 0.6.5

- Move the left shield elbow slightly backwards and lower the forearm in the
  common walking carry for all shields, following the accepted live Lab pose.
- Add separate native walking fits for medium and small metal shields; their
  uncorrected attachments intersected the arm at both short and tall heights.
  Small metal aligns its front strap along the forearm with the current fist,
  turns the wrist outwards by 20 degrees and tips its upper edge outwards by 5
  degrees about the front strap. Keep the arm
  beside the torso and retain native item dimensions.
- Check all three metal WOM meshes on all four bodies at each height from
  13 to 20 dirt: 11,520 walk clearance poses and 768 CPU arm-mesh clearance
  poses. Small's wrapping straps use actual mesh separation at every height
  instead of pushing the item away to clear an oversized forearm capsule.
  Check each attachment's native transition weight and completed-frame
  publication. Include shield type in the distributed CSV reports.

## 0.6.4

- Replace the walking shield arm's outward swing with an asymmetric carry:
  upper arm almost vertical beside the torso, forearm forward, shield face along
  the flank. Apply this base arm pose to every visible shield in native slot 11.
  Keep right-arm, leg, camera, Idle, Run and empty-handed motion unchanged.
- Fit the large metal shield separately to clear its native curved panel and
  rim from the upper arm and forearm. Retain native item scale, height-scaled
  anchor placement and the other shields' existing attachment offsets.
- Match the large-metal attachment clearance to the native arm crossfade and
  publish it only after successful skinning, avoiding intermediate sampler states.
- Require 3,840 native shield/arm/leg capsule checks across the four bodies at
  13–20 dirt, plus 128 CPU arm-mesh intersection checks at 13 and 20 dirt.
  Check native partial sampling, walk aliases, repeated phases, transitions,
  successful-skinning publication, all-shield base selection and rollback.

## 0.6.3

- Fit walking with the equipped large metal shield: rotate the left upper arm
  outward by 10 degrees and the forearm by 40 degrees around their joint origins
  in model space. Keep the shield's native dimensions and attachment offsets.
- Select this fit by the exact loaded `weapon/Shields/LargeMetalShield.wom`
  resource, including its material variants, only while Wurm displays the item
  in the shield slot. Hidden shields, equipped tools and other shield models
  retain their ordinary arm motion. Idle and running remain unchanged.
- Apply corrections to freshly sampled keys and their native blend cache;
  follow Wurm's actual joint blend-group filters for partial sampling. Lab's
  Walk preview and native Studio Walk control use that same sampler.
- Require a real shared-loader check with the installed WOM shield triangles,
  120 walk phases on each of the four bodies at every integer height from
  13 through 20 dirt, leg capsule/floor clearance, walk aliases, partial native
  sampling, repeated frames, disarming, hiding, other shields and model copies.

## 0.6.2

- Publish camera offsets only after successful skinning. Both native camera
  offset APIs retain the last completed pose while Lab restores and recalculates
  its temporary height/animation, preventing reads of the intermediate 100%
  height. Retain native camera blending, heading, mounts and third-person controls.
- Reproduce the camera jump with the archived 0.6.1 JAR and a paused animation
  worker in 32 scenarios across all four bodies, 13/20 dirt and game/idle/walk/run.
  Require the corrected interleavings and Lab-off rollback in release checks.
- Make repeated registration of an absent local Avatar a no-op and clear old
  pose telemetry when the local body changes, so disabled bodies cannot report
  a previous height or continuously increment the command revision.
- Build/check the current canonical shared updater and verify its version,
  SHA-256 and complete embedded entries. Installed camera motion still requires
  a manual check with Lab on/off in first and third person.

## 0.6.1

- Fix a lock-order cycle between height telemetry and virtual equipment sockets
  that can freeze the animation and render workers at any body height. Reproduce
  it deterministically with the 0.6.0 JAR, then verify the corrected interleaving
  and 40,000 concurrent native attachment/telemetry calls. Move native bounds
  calculation outside the height registration monitor as well.
- Hide the local Avatar head, eyes and mouth interior in first-person rendering.
  Use a separate index mask with a closed lower-neck cap for all four bodies,
  retaining the full body for third person, free camera, shadows and other models.
  Preserve the rig, skin arrays and full mesh indices; carry masks through native
  model copies and the singleton mesh baker, reusing one native IBO per draw mesh.
- Expose validated temporary height (13–20 dirt), authored shape/amount and
  idle/walk/run controls in the native Chamomilo Avatar Studio window. Use the
  same Lab validation and command as the browser, with drag clamps, release-only
  buttons, disabled incompatible shape banks and a complete Reset test action.
  Retain the selected shape at 0% so increasing its amount works again.
  Test both Bodybuilder banks, native window/input, font sizes, fixed opacity and
  persistent-profile isolation. Keep these experiments out of Save body.
- Embed canonical Chamomilo updater 1.0.2 / UI 0.3.4 with exact entry/byte and ZIP
  verification. Package the first-person topology report and stability notes.
- Installed 0.6.0 testing obtained two idle poses at 13 dirt and one game frame
  before telemetry stopped. Full equipment and new first-person GL checks remain
  pending manual installation. An earlier AWT/Direct3D native crash is a separate
  observation; this patch does not establish the cause of every reported hang.

## 0.6.0

- Add virtual rigid Wurm sockets for both hands, shield, middle-finger attachments
  and Spine/SpineTop holster anchors. Transfer pinned vanilla hand/forearm landmarks
  to each anatomical bind; retain every imported joint, weight and morph binding.
- Scale socket positions with temporary height while keeping item rotation axes
  and scale rigid. Preserve current/original, string/indexed and destination-buffer
  APIs, independent copied poses and native unsupported-resource fallback.
- Add Lab socket telemetry and a required real shared-loader/FK runtime check:
  four bodies, three heights, 432 poses and seven anchors. Package its CSV report.
  Actual grips, shield facing and holster clearance require manual installation.
- Replace private updater sources with the complete canonical Chamomilo updater
  1.0.2 and its release gate; verify exact module bytes in the JAR and ZIP.
- Record user confirmation of installed 0.5.4 male/female standing posture.

## 0.5.4

- Remove both decorative -4-degree render pitches from Wurm's `WorldRender.renderPlayerModel` when the local body resolves to an Avatar resource. The heading-dependent composition can tilt the whole mesh by 8 degrees after FK, explaining why vertically aligned leg matrices still looked slanted in game.
- Keep authored Idle/Walk/Sprint resources, bones, contact compensation, heading, camera and mount transforms unchanged. Vanilla/unloaded bodies keep the original renderer behavior. Discard the experimental leg-angle compensation rather than bake a renderer workaround into the animation.
- Verify Avatar's upright render orientation across 360 headings and exact vanilla/null fallback. Validate both hook sites against the pinned Wurm renderer; retain the native CPU/GPU skinning and contact checks.
- Live Root compensation is a diagnostic preview; the production renderer hook requires manual installation and an installed-runtime visual check.

## 0.5.3

- Make the main male/female Idle calmer: retain 35% of local chest motion, 15% of lower spine motion and 10% elsewhere; lengthen its loop from 2.467 to 3.700 native seconds. Add a gentle 3-degree forward correction to the upper body's neutral posture. Keep Walk and Sprint resources unchanged.
- Fix the pinned pelvis in `idle` / `idle.p1`. Wurm clamps non-root translations; recover pelvis placement from the leg pose, anchoring the mean ankle position and heel/toe support height. The pelvis follows subtle knee flexion while feet keep their height; Root/world placement stays native.
- Verify 120 phases per control body, full/lower-body sampling, two copy generations, foot contact, reduced head sway and native fallback. Check skinned soles through CPU and GPU DQ paths across 13–20 dirt. Include contact reports.
- Installed-runtime blending, female live viewing and stairs/floor checks remain pending manual installation.

## 0.5.2

- Limit temporary Lab standing height to 13–20 Wurm dirt/slope units (1.3–2.0 metres) for both sexes. Calculate scale limits from each approved control body's measured height; 100% retains its original size. This replaces the exploratory 50–150% interval.
- Accept `heightDirts`, `heightMetres` or `heightScale`, one unit per command. Publish physical and body-specific scale ranges in the schema, reject invalid commands before changing the revision, and add CLI `height 13 --unit dirt` / `height 2 --unit metres`.
- Verify 1,200 native CPU poses and 432 native GPU DQ/shader-math poses across four bodies, including older forms at both physical endpoints. Check the conversion against Wurm's native height decoder.
- Live male Bodybuilder inspection of 0.5.1 confirms the GPU distortion fix on viewed neutral/older idle/walk/run frames. Female height, first-person camera, stairs, floor transitions and low ceilings remain live checks; the prototype still preserves native world movement.

## 0.5.1

- Fix severe GPU body/hand distortion during the height trial, observed at 50% in live 0.5.0. Wurm uses dual-quaternion skinning, so a scaled rotation matrix cannot carry height through its quaternion conversion. Scale private GPU vertex inputs and inverse-bind translations, and supply unscaled current rotation axes to the native DQ upload. Keep CPU bind arrays, original joint matrices and morph fingerprints intact.
- Restore the private GPU inputs when returning to 100%, changing skinning mode or copying a scaled body. Preserve the existing height camera, bounds, rigid-item attachment and rollback behavior.
- Calculate native render/picking bounds from a retained unscaled bind-buffer view so the separately scaled GPU input cannot cause the height factor to be applied twice.
- Extend the required native runtime check with Wurm's actual `updateBoneBuffers` and the pinned shader's antipodality/blending/rotation/translation formula. Check every body/eye vertex and normal in 576 GPU DQ poses across four bodies, including older forms at the height extremes, alongside the existing 1,632 CPU poses. Actual GL rendering and first-person camera still require manual installation and live verification of 0.5.1.

## 0.5.0

- Add a temporary proportional height trial in Animation Lab, 50–150% of each approved control body's measured import size. Scale the completed skinning pose about the bind floor, retaining immutable vertices, weights, inverse binds and approved clips. Keep profiles unchanged.
- Scale render/picking bounds and both current/original camera socket offsets. Return rigid attachment matrices and original socket rotation/scale so the added body scale is not inherited by a separate item model. Existing missing equipment sockets and actual fitting remain pending.
- Restore height on Reset, Lab off, lease expiry and local-model replacement; copies made during a preview retain an independent unscaled pose. Add CLI height commands, JSON examples and telemetry.
- Verify 11 scales and 396 native CPU poses on each of four bodies, plus 24 age/height combinations per Bodybuilder sex, eyes, camera APIs, bounds, attachment transforms, model copies, GPU inputs and rollbacks. Height GPU rendering, first-person view, gait frequency, equipment, sitting and the common release height reference still need in-game calibration.
- Confirm the 0.4.1 finger fix in limited live GPU inspection on both Bodybuilder sexes. Recorded phases, endpoint/midpoint checks and reset pass; detailed face closeups, paper doll and CPU/GPU switching remain pending.

## 0.4.1

- Fix stretched, curled fingers in the older MPFB endpoint. Refit a temporary authoring skeleton to the age shape and map its vertices back to the approved bind with the original four skin weights; recompute surface normals. Runtime joints, weights and locomotion remain unchanged.
- Add a native CPU regression comparing finger vertices with the neutral body across 36 idle/walk/run poses per shape and sex. The old bank fails at about 56 mm displacement; corrected age banks stay within 5.6 mm (male) and 2.8 mm (female). Include the measurement report in the ZIP.
- Record the live 0.4.0 GPU checks and their visual age-shape failure. The corrected 0.4.1 banks still require a new in-game check after manual installation; stage 0 remains open.

## 0.4.0

- Add MPFB-authored Athletic, higher-weight and older-adult geometry endpoints on the approved male/female Bodybuilder vertex IDs, including separately fitted eyes. Retain the fixed weights, bind joints and approved animation clips.
- Add immutable morph banks tied to the full optimized Wurm binding fingerprint. Reconstruct and compare authoring vertex IDs against the approved blend/DAE; map all imported UV-split vertices without nearest-point guessing. Reject incompatible topology before accepting a Lab command.
- Load banks through Wurm's resource-pack API. Extend temporary Lab geometry commands with shape/shapeMix, CLI options and examples; retain exact neutral reset and independent model/baked copies.
- Verify the production JarPack/import/animation bridge plus endpoint interpolation and real CPU skinning/normals for all three shapes and idle/walk/run phases. GPU checks cover bind inputs only; stage 0 remains open for live rendering, age skin, height and equipment.

## 0.3.0

- Start customization-plan stage 0 with a temporary torso geometry probe in Animation Lab: JSON geometry commands and companion CLI `geometry --width ... --depth ...`, each in -1..1. The analytic probe is diagnostic, not an authored muscle/fat morph.
- Compact imported Avatar meshes once, fingerprint their complete imported binding, and keep immutable baselines plus private original arrays, NIO buffers and VBO ownership on model/baked copies. Preserve the native behavior of unregistered resources.
- Rebuild geometry only when the command changes, transform normals with the deformation Jacobian, update render/picking bounds, and feed both source and baked skinning. Keep neutral templates and paper-doll copies independent of temporary world probes.
- Calculate CPU normals on every managed Avatar skinning frame; Wurm's native shared-buffer flag otherwise skips them after buffers are detached. Restore bind inputs and rebuild VBO layout when switching CPU/GPU modes.
- Restore geometry on Reset, Lab off, lease expiry and local-model changes. Keep experimental settings out of saved profiles, and retain them when companion calibration/recording restores a command.
- Add a required release probe using the real Wurm shared loader, four control bodies, singleton baker, CPU skinning/normals and simulated GPU input branch. Package its binding report. Live GPU rendering, height/equipment tests and remaining stage-0 prototypes are pending.

## 0.2.10

- Preserve pelvis-motion registration when Wurm copies cached Avatar bodies. Male Bodybuilder and repeated body switches now keep the same walk weight transfer as the imported model.
- Reimport both supplier running loops, Jog_Fwd_Loop and Sprint_Loop, directly onto clean native male/female rigs with the symmetric reference and chest-aligned arms. Expose gait.jog.clean and gait.sprint.clean for comparison.
- Select the user-approved Sprint for run/run.p1/gait.sprint. Restore imported pelvis sway and vertical bob for production running and both comparison clips, retaining the source's airborne motion.
- Add Jog, Sprint and previous-run comparison buttons to Animation Lab. Candidate selection starts playback and clears experimental poses/corrections; ordinary movement restores game control.
- Verify copied models and full/lower-body walking/running with Wurm's actual patched sampler, both fresh runners on all four bodies, and live Bodybuilder matrices/captures. Retain the previous jog under gait.jog for comparison.

## 0.2.9

- Reimport Idle_Loop and Walk_Loop from the supplier FBX onto clean native rigs. Build a symmetric Idle reference, transfer axial motion in world axes, and align arm directions with the anatomical chest.
- Remove the persistent torso twist, pelvis lean and unequal arm abduction; retain animated weight transfer and the existing run files.
- Verify cycle-average symmetry and natural alternating motion on all four bodies, plus live candidate poses and captures through Animation Lab.
- Add a live Animation Lab on/off button and Open Lab action to Avatar Studio's Body page.
- Open Lab starts the connector when needed and opens its controls without console commands; stopping clears experimental poses and corrections.
- Keep Lab state separate from the saved body profile and reflect console changes in the button state.

## 0.2.8

- Restore pelvis weight transfer in Avatar walk/walk.p1/gait.walk.relaxed after Wurm's non-root translation clamp.
- Derive walk support height from the accepted native leg pose and sole landmarks; leave Root, bone rotations, idle and run unchanged.
- Verify 120 phases of all three walk aliases on all four bodies, idle blending and the actual patched sampler under Wurm's shared classloader.

## 0.2.7

- Fix private HUD access in the shared updater bridge and verify mixed-mod menu registration.
