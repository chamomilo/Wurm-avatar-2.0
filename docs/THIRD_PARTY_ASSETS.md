# Third-party assets

| Asset | Version/source | License | Role |
|---|---|---|---|
| MPFB | 2.0.17, MakeHuman Community | GPL-3.0 code; generated/core graphical assets CC0 as documented upstream | Parametric body authoring and game-engine rig donor |
| MakeHuman system assets | CC0 system pack | CC0 | Muscular male/female proxy bodies and supporting authoring assets |
| MakeHuman adult skins | young/middleage/old caucasian male/female diffuse endpoints from the same system pack | CC0 | Six adult skin endpoints; provenance in assets/metadata/age-skin-prototype.json |
| Blender Human Base Meshes | 1.4.1, Blender Foundation | CC0 | Anatomy/topology reference and optional detail donor |
| Blender | 4.5.13 LTS | GPL and bundled third-party licenses | Reproducible portable DCC runtime |
| Universal Animation Library | Standard edition, Quaternius | CC0 | Human idle, walk and jog source motion; retargeted to the Avatar 2.0 rig |
| Wurm Unlimited models | Local installed game | Proprietary game assets | Local scale/rig reference and native runtime hairstyles; not redistributed |
| armor-material-colors | local project 0.3.16 | Project code/resources | Proven material tint and reflection implementation used as integration baseline |

Downloaded archive hashes are recorded in `assets/metadata/downloads.sha256`.
Avatar 0.10.0's Muscle/Fullness corners use the same pinned MPFB/system assets.
Their macro grid, frozen-bind fitting and bank hashes are recorded in
`assets/metadata/morph-prototype.json` and `assets/metadata/body-build-0100.json`.
Avatar 0.11.0's female cupsize corners use the same assets; provenance and
preservation measurements are in `assets/metadata/breast-volume-0110.json`.
Avatar 0.12.0 uses MPFB's CC0 torso shoulder-distance measurement targets on
the same frozen body bindings. Local displacement and preservation measurements
are recorded in `assets/metadata/shoulder-width-0120.json`. Heavy torso relief
recovers the project's pinned pre-fairing source, recorded in
`assets/metadata/muscle-relief-0120.json`; no new third-party art is introduced.
Before redistribution, retain upstream license files and never package extracted
Wurm WOM/XML/texture sources.

Avatar 0.15.0 reads native hairstyle meshes/textures from the installed game.
Measurement-only provenance is in `assets/metadata/hair-fit-0150.json`. Its
private under-hair surface comes from Avatar's own CC0-derived head triangles;
no extracted Wurm art is included in either distribution.
