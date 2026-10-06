# Avatar 2.0 animation contract

Avatar 2.0 never applies a vanilla WOM animation to the anatomical rig. Wurm
remains the authority for the animation key and gameplay state, while every
motion shown by Avatar 2.0 must be retargeted or authored against its own native
bind pose. This prevents the axis and inverse-bind mismatch which folded the
early 91-bone test body.

The measured male WOM exposes 323 runtime keys. The exact, machine-readable
list is stored in `assets/source/wurm-vanilla/vanilla-rig-male.json` under
`animations`. Variants such as `.p1`, `.twohander` and attack positions
`.0`–`.7`/`.10` are aliases or parameterisations; they must not become hundreds
of copied animation files.

## Production families

| Priority | Master motion family | Wurm keys covered | Production approach |
|---|---|---|---|
| P0 | relaxed locomotion | `idle`, `walk`, `run` and `.p1` | dedicated loops; shipped |
| P0 | driving and sitting | `ride`, `rideWalk`, `ride.model.*` | later task: driving loop for carts/wagons and seated loop for furniture |
| P0 | swimming | `idleSwim`, `swim*` | idle and forward library clips; author backward/strafe variants |
| P0 | posture and traversal | `kneel`, `sprawl`, `climbup`, `climbdown`, `wounded`, `die` | library crouch/death plus authored transitions |
| P1 | combat ready | `ready*`, one-hand, two-hand and directional suffixes | four base stances with additive aim/direction layers |
| P1 | melee attacks | `fight_cut*`, `fight_maul*`, `fight_pierce*`, `fight_strike*`, `fight_kick` | weapon-family masters, mirrored/directional variants and hand/weapon IK |
| P1 | defence and ranged | `parry.*`, `dodge`, `shieldbash*`, `bow`, `shoot`, `throw`, `stun` | authored shield clearance; bow and projectile hand targets |
| P2 | ground and mining work | `dig`, `mine*`, `tunnel`, `flatten*`, `level*`, `pack`, `prospect` | shared tool arcs selected by target height and tool grip |
| P2 | construction/demolition | `build*`, `destroy*`, `create`, `chopup`, `cutdown` | reusable hammer/chop/push/pull masters with IK contacts |
| P2 | gathering and care | `forage*`, `botanize*`, `rummage*`, `farm`, `groom`, `butcher`, `bury*`, `firstaid*` | standing, crouched and kneeling interaction masters |
| P2 | fishing and consumption | `fish*`, `spearfish*`, `drink*`, `take`, `drop` | prop-aware clips and short transitions |
| P2 | magic and ritual | `spell*`, `pray`, `meditate`, `focus`, `conquer` | library spell base plus authored ritual variants |
| P3 | social/emotes | `applaud`, `point`, `wave`, `spy*`, `pull`, `push`, `hold`, `use` | library interaction/talking clips plus authored emotes |
| P3 | extended idle set | crouch, squat, spit, sleep, fidget, acrobatics and weapon kata | weighted periodic state machine with equipment and terrain guards |

## CC0 library inventory

The bundled Quaternius Universal Animation Library Standard contains 43 usable
source clips. Direct candidates are:

- movement: `Idle_Loop`, `Walk_Loop`, `Walk_Formal_Loop`, `Jog_Fwd_Loop`,
  `Sprint_Loop`, `Crouch_Fwd_Loop`, `Crouch_Idle_Loop`;
- transport/posture: `Driving_Loop`, `Sitting_Enter`, `Sitting_Idle_Loop`,
  `Sitting_Talking_Loop`, `Sitting_Exit`;
- traversal/reaction: `Jump_Start`, `Jump_Loop`, `Jump_Land`, `Roll`,
  `Hit_Chest`, `Hit_Head`, `Death01`;
- combat/magic: `Sword_Idle`, `Sword_Attack`, `Punch_Jab`, `Punch_Cross`,
  `Spell_Simple_Enter`, `Spell_Simple_Idle_Loop`, `Spell_Simple_Shoot`,
  `Spell_Simple_Exit`;
- actions/idles: `Fixing_Kneeling`, `Interact`, `PickUp_Table`, `Push_Loop`,
  `Idle_Talking_Loop`, `Idle_Torch_Loop`, `Dance_Loop`;
- water: `Swim_Idle_Loop`, `Swim_Fwd_Loop`.

Pistol clips are retained as possible upper-body/aim references, not as direct
medieval gameplay motions. Missing weapon families, shield contacts, climbing,
most crafts and the richer idle set must be authored or assembled from these
bases.

## Runtime rules

1. A clip is accepted only after Wurm's importer resolves it and all referenced
   joints exist.
2. Each master clip is authored once per anatomical sex rig; Wurm aliases point
   to it instead of duplicating data.
3. World translation stays server-controlled. Clips retain vertical/lateral
   pelvis motion but remove forward root travel unless the state explicitly
   needs an anchored transition.
4. Hands use weapon, shield and prop IK targets. Feet use ground contacts and
   foot locking for loops.
5. An unavailable state falls back to the last safe Avatar pose, never to a
   vanilla WOM transform.
6. Acceptance includes silhouette, foot sliding, self-intersection, shield and
   armour clearance, transition timing and first-/third-person camera safety.
