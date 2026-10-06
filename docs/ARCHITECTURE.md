# Avatar 2.0 architecture

The runtime is intentionally evolutionary: Wurm remains authoritative for
identity, equipment state and world movement. A high-detail anatomical deformation
rig keeps its native 53-bone hierarchy, while Avatar 2.0 owns the body bind pose
and selected animation clips. Wurm's 91-bone hierarchy is not injected into the
body. Compatibility is provided by non-deforming leaf sockets and offline asset
adapters.

1. **Avatar profile** chooses sex, muscularity and bounded proportions.
2. **Scale adapter** checks feet, pelvis, shoulders, head and hands against the
   measured vanilla envelope without rewriting the native skeleton.
3. **Outfit composer** resolves layer priority and coverage masks per body region.
4. **Pose collision guard** applies small corrective morphs/IK only to approved
   pairs such as beard/chest and hand/shield.
5. **Animation dispatcher** uses Wurm's action names as a state contract but loads
   only clips retargeted to the anatomical bind pose. Vanilla WOM transforms are
   never mixed into the new skeleton; unavailable actions retain a safe Avatar pose.
6. **Equipment adapter** attaches rigid items to named native sockets. Deforming
   garments receive one offline weight transfer per body family and cache the
   result; neither the body nor source armour is destructively rewritten.
7. **Renderer adapter** retains Wurm's model and paperdoll lifecycle; LOD assets
   replace geometry without replacing network state. While an Avatar body is
   inside the Collada importer it substitutes a correct four-slot skin-weight
   normalizer. This narrowly scoped sanitation prevents an index error in Wurm's
   own normalizer from changing the following vertex and separating UV-split
   copies into visible triangular cracks during deformation.

The paperdoll uses the same assembled avatar at a dedicated LOD and pose. It is
not a separately authored 2D likeness, which prevents equipment drift between
the world model and character window.

Target full-character budgets are 25-40k triangles for LOD0, 10-15k for LOD1,
3-5k for LOD2 and a vanilla-class fallback LOD3.
