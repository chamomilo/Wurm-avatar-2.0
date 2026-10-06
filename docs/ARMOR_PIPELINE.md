# Armour fitting, occlusion and collision pipeline

Armour is retained rather than discarded. Each existing Wurm mesh is exported,
aligned to the vanilla landmark contract, non-destructively fitted to the male and
female muscular base bodies, corrected, and versioned in this project.

The body does not need a Wurm-skeleton bridge. Rigid pieces (helmet, pauldron,
shield, weapon and most plate components) use a slot-to-native-socket transform.
Deforming pieces (shirt, trousers, mail and fitted cuirasses) pass through an
offline weight-transfer adapter for each body family. The adapted result is
cached by source item, sex, preset and body-rig version, while the original Wurm
mesh remains an immutable source asset.

## Static overlap

Each item declares a layer and a coverage mask. During outfit assembly, visible
triangles are computed from the top layer down. A pauldron masks covered sleeve
and breastplate triangles; a breastplate masks body/shirt triangles. This solves
the shoulder spike and breastplate-under-pauldron artifacts without runtime
triangle/triangle collision tests.

Masks use stable surface-region IDs, not screen-space depth, and include a small
authoring margin. Optional corrective shape keys handle remaining pose extremes.

## Dynamic contact

Only moving pairs use proxies: beard/chest uses capsules plus beard correction;
shield/hand uses grip anchors and arm IK, with a hand guard volume as a final
safety constraint. Expensive cloth/solid simulation is reserved for offline
baking, not ordinary gameplay.

## Materials

Every corrected armour submesh retains Wurm material-mask UVs. Reflection strength
is stored per armour slot so one pauldron, gauntlet or shield can be tuned without
changing the rest of the outfit. See `MaterialReflection` for the QL curve.
