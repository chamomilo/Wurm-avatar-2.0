package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.ResourceUrl;
import org.avatar2.core.GeometryProbe;
import org.avatar2.core.MorphBank;

import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;

/** Stage 0 ownership and geometry experiment. Only the animation worker edits meshes. */
public final class AvatarGeometryPrototype {
    // Values never retain their weak keys: a model owns its source meshes.
    private static final Map<TriangleMesh, MeshState> MESHES = new WeakHashMap<>();
    private static final Map<ColladaModel, GeometryProbe> REQUESTS = new WeakHashMap<>();

    private AvatarGeometryPrototype() { }

    public static synchronized boolean isManaged(TriangleMesh mesh) {
        return MESHES.containsKey(mesh);
    }

    static synchronized ColladaModel owner(TriangleMesh mesh) {
        MeshState state=MESHES.get(mesh);return state==null?null:state.owner.get();
    }

    public static synchronized float heightScale(TriangleMesh mesh) {
        MeshState state = MESHES.get(mesh);
        return state == null ? 1 : AvatarHeightPrototype.scale(state.owner.get());
    }

    /** Native bounds must read bind geometry, not the separately scaled GPU input. */
    public static synchronized FloatBuffer boundsBuffer(TriangleMesh mesh, FloatBuffer nativeBuffer) {
        MeshState state = MESHES.get(mesh);
        return state == null ? nativeBuffer : state.bindVertices;
    }

    /** Register only after the caller has verified the Avatar resource URL. */
    public static synchronized void register(ColladaModel model) {
        registerWithMorphs(model, null);
    }

    public static synchronized void register(ColladaModel model, ResourceUrl avatarUrl) {
        registerWithMorphs(model, MorphResources.forAvatar(avatarUrl));
    }

    /** Also used by the offline binding verifier with the exact packaged bytes. */
    public static synchronized void registerWithMorphs(ColladaModel model, MorphBank bank) {
        if (model == null || model.getGeometryArray() == null) return;
        if (REQUESTS.containsKey(model)) return;
        // Let Wurm compact UV-split triangles once BEFORE freezing vertex IDs.
        // Preventing this first optimization would multiply skinning work.
        model.optimize();
        float floor = Float.POSITIVE_INFINITY, ceiling = Float.NEGATIVE_INFINITY;
        List<TriangleMesh> meshes = sources(model);
        for (TriangleMesh mesh : meshes) {
            float[] vertices = mesh.getOriginalVertexBuffer();
            if (vertices == null || mesh.getOriginalNormalBuffer() == null || mesh.isSlimmed())
                throw new IllegalStateException("Avatar prototype requires retained bind geometry");
            for (int i = 1; i < vertices.length; i += 3) {
                floor = Math.min(floor, vertices[i]); ceiling = Math.max(ceiling, vertices[i]);
            }
        }
        if (meshes.isEmpty() || !(ceiling > floor))
            throw new IllegalStateException("Avatar prototype has no body geometry");
        for (TriangleMesh mesh : meshes) {
            mesh.setAllowSlim(false);
            MESHES.put(mesh, new MeshState(new Basis(mesh, floor, ceiling - floor, bank, model), model, GeometryProbe.NEUTRAL, mesh.hasGPUSkinning()));
        }
        REQUESTS.put(model, GeometryProbe.NEUTRAL);
    }

    /** Called after the injected copy constructor has detached all mutable buffers. */
    public static synchronized void copiedMesh(TriangleMesh source, TriangleMesh copy) {
        MeshState old = MESHES.get(source);
        if (old == null) return;
        copy.setAllowSlim(false);
        MeshState state = new MeshState(old.basis, old.owner.get(), old.applied, copy.hasGPUSkinning());
        state.head = old.head;
        state.renderScale = old.renderScale;
        state.bindVertices = FloatBuffer.wrap(copy.getOriginalVertexBuffer()).asReadOnlyBuffer();
        state.bindBounds = old.bindBounds.clone();
        MESHES.put(copy, state);
    }

    /** A model copy has its own request. Baked single-source copies keep their owner. */
    public static synchronized void copiedModel(ColladaModel source, ColladaModel copy) {
        GeometryProbe requested = REQUESTS.get(source);
        if (requested == null) return;
        // A temporary world-body experiment must not become a paper-doll or
        // cached-body setting when Wurm copies the model during the experiment.
        REQUESTS.put(copy, GeometryProbe.NEUTRAL);
        for (TriangleMesh mesh : sources(copy)) {
            MeshState state = MESHES.get(mesh);
            if (state == null) throw new IllegalStateException("Avatar mesh copy hook is missing");
            state.owner = new WeakReference<>(copy);
        }
    }

    public static synchronized void request(ColladaModel model, GeometryProbe probe) {
        if (!REQUESTS.containsKey(model)) throw new IllegalStateException("Avatar geometry is not registered");
        validate(model, probe);
        REQUESTS.put(model, probe == null ? GeometryProbe.NEUTRAL : probe);
    }

    public static synchronized void validate(ColladaModel model, GeometryProbe probe) {
        if (probe != null && !"young".equals(probe.agePreset) && !ageSupported(model, probe))
            throw new IllegalArgumentException("Body age requires the matching Build age grid");
        if (probe != null && probe.buildShoulders != .5f && !shouldersSupported(model))
            throw new IllegalArgumentException("Shoulder width requires the matching Build bank");
        if (probe != null && probe.buildBreast != .5f && !breastSupported(model))
            throw new IllegalArgumentException("Breast volume requires the matching female Build grid");
        if (probe != null && probe.hasAuthoredShape() && !availableShapes(model).contains(probe.shape))
            throw new IllegalArgumentException("Authored shapes require the matching Bodybuilder binding; available: " + availableShapes(model));
    }

    public static synchronized boolean breastSupported(ColladaModel model) {
        List<TriangleMesh> meshes = sources(model);
        if (meshes.isEmpty()) return false;
        for (TriangleMesh mesh : meshes) {
            MeshState state = MESHES.get(mesh);
            if (state == null || state.basis.morph == null || !state.basis.morph.shapes().containsAll(GeometryProbe.BREAST_TARGETS))
                return false;
        }
        return true;
    }

    public static synchronized boolean shouldersSupported(ColladaModel model) {
        List<TriangleMesh> meshes = sources(model);
        if (meshes.isEmpty()) return false;
        for (TriangleMesh mesh : meshes) {
            MeshState state = MESHES.get(mesh);
            if (state == null || state.basis.morph == null || !state.basis.morph.shapes().containsAll(
                    Arrays.asList("build-shoulders-narrow", "build-shoulders-wide"))) return false;
        }
        return true;
    }

    public static synchronized boolean ageSupported(ColladaModel model, GeometryProbe probe) {
        List<TriangleMesh> meshes=sources(model);
        if(meshes.isEmpty())return false;
        List<String> required=GeometryProbe.AGE_TARGETS.subList(0, probe.buildBreast==.5f?4:12);
        for(TriangleMesh mesh:meshes) {
            MeshState state=MESHES.get(mesh);
            if(state==null||state.basis.morph==null||!state.basis.morph.shapes().containsAll(required))return false;
        }
        return true;
    }

    public static synchronized Set<String> availableShapes(ColladaModel model) {
        Set<String> shapes = new LinkedHashSet<>(GeometryProbe.TARGETS);
        List<TriangleMesh> meshes = sources(model);
        for (TriangleMesh mesh : meshes) {
            MeshState state = MESHES.get(mesh);
            if (state == null || state.basis.morph == null) { shapes.clear(); break; }
            shapes.retainAll(state.basis.morph.shapes());
        }
        if (meshes.isEmpty()) shapes.clear();
        if (shapes.containsAll(Arrays.asList("mpfb-athletic", "mpfb-heavy", "mpfb-older"))) shapes.add("blend");
        if (shapes.containsAll(Arrays.asList("build-low-muscle", "build-full", "build-low-muscle-full"))) shapes.add("proportions");
        shapes.retainAll(GeometryProbe.SHAPES);
        shapes.add("none");
        return Collections.unmodifiableSet(shapes);
    }

    public static synchronized boolean hasProbe(ColladaModel model) {
        GeometryProbe probe = REQUESTS.get(model);
        return probe != null && !probe.isNeutral();
    }

    /** Called before the body's calculateSkins, including its baked render path. */
    public static synchronized boolean beforeSkins(ColladaModel model) {
        GeometryProbe desired = REQUESTS.get(model);
        if (desired == null) return false;
        boolean changed = false;
        for (TriangleMesh mesh : sources(model)) {
            MeshState state = MESHES.get(mesh);
            changed |= state != null && !desired.equals(state.applied);
            beforeSkin(mesh);
        }
        return changed;
    }

    /** Injected before TriangleMesh.calculateSkin; applies to source and baked clones. */
    public static synchronized void beforeSkin(TriangleMesh mesh) {
        MeshState state = MESHES.get(mesh);
        if (state == null) return;
        ColladaModel owner = state.owner.get();
        GeometryProbe desired = owner == null ? GeometryProbe.NEUTRAL : REQUESTS.get(owner);
        if (desired == null) desired = GeometryProbe.NEUTRAL;
        boolean changed = !desired.equals(state.applied), gpu = mesh.hasGPUSkinning();
        float renderScale = gpu ? AvatarHeightPrototype.scale(owner) : 1;
        if (!changed && gpu == state.gpu && renderScale == state.renderScale) return;
        boolean modeChanged = gpu != state.gpu;
        if (mesh.isSlimmed()) throw new IllegalStateException("Avatar mutable mesh was slimmed");
        Basis basis = state.basis;
        float[] vertices = mesh.getOriginalVertexBuffer(), normals = mesh.getOriginalNormalBuffer();
        if (changed) {
            vertices = new float[basis.vertices.length]; normals = new float[basis.normals.length];
            if (desired.hasAuthoredShape()) {
                if (basis.morph == null) throw new IllegalStateException("Avatar morph fingerprint mismatch");
                basis.morph.deform(desired, basis.vertices, basis.normals, vertices, normals);
                if (desired.torsoWidth != 0 || desired.torsoDepth != 0) {
                    float[] authoredVertices = vertices, authoredNormals = normals;
                    vertices = new float[vertices.length]; normals = new float[normals.length];
                    desired.deform(authoredVertices, authoredNormals, vertices, normals, basis.floor, basis.height);
                }
            } else desired.deform(basis.vertices, basis.normals, vertices, normals, basis.floor, basis.height);
            state.head = basis.head.atCrown(mesh.getName().startsWith("Avatar2BodyMesh")
                    ? bounds(vertices)[4] : headCrown(owner, desired, basis.head.y));
            state.head.deform(desired.headSize, vertices, normals);
            state.hairFit = null;
            state.scalp = null;
            mesh.setOriginalVertexBuffer(vertices); mesh.setOriginalNormalBuffer(normals);
            state.bindVertices = FloatBuffer.wrap(vertices).asReadOnlyBuffer();
        }
        put(mesh.getVertexBuffer(), vertices);
        // Wurm GPU skinning uses dual quaternions, which cannot encode scale.
        // Scale only the private render input, leaving CPU/bind arrays intact.
        if (renderScale != 1) {
            FloatBuffer buffer = mesh.getVertexBuffer();
            for (int i = 0; i < vertices.length; i++) buffer.put(i, vertices[i]*renderScale);
        }
        put(mesh.getNormalBuffer(), normals);
        if (modeChanged) { mesh.freeVBO(); mesh.shouldRender = true; }
        else mesh.updateVBO(); // GPU reads bind buffers, CPU reads the arrays.
        state.applied = desired;
        state.gpu = gpu;
        state.renderScale = renderScale;
        if (changed) { state.builds++; state.bindBounds = bounds(vertices); }
    }

    private static void put(FloatBuffer buffer, float[] values) {
        if (buffer == null || buffer.capacity() != values.length)
            throw new IllegalStateException("Avatar buffer layout changed");
        FloatBuffer destination = buffer.duplicate(); destination.clear(); destination.put(values);
    }

    public static synchronized List<Map<String, Object>> describe(ColladaModel model) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TriangleMesh mesh : sources(model)) {
            MeshState state = MESHES.get(mesh);
            if (state == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", mesh.getName()); row.put("basisSha256", state.basis.fingerprint);
            row.put("vertices", mesh.getVertexCount()); row.put("indices", mesh.getIndexCount());
            row.put("gpuSkinning", mesh.hasGPUSkinning()); row.put("slimmed", mesh.isSlimmed());
            row.put("builds", state.builds);
            row.put("torsoWidth", state.applied.torsoWidth); row.put("torsoDepth", state.applied.torsoDepth);
            row.put("shape", state.applied.shape); row.put("shapeMix", state.applied.shapeMix);
            row.put("blendAthletic", state.applied.blendAthletic); row.put("blendHeavy", state.applied.blendHeavy);
            row.put("blendOlder", state.applied.blendOlder); row.put("shapeWeights", state.applied.weights());
            row.put("buildMuscle", state.applied.buildMuscle); row.put("buildFat", state.applied.buildFat);
            row.put("buildWeights", state.applied.buildWeights());
            row.put("buildBreast", state.applied.buildBreast); row.put("buildShoulders", state.applied.buildShoulders); row.put("buildGridWeights", state.applied.buildGridWeights());
            row.put("headSize", state.applied.headSize); row.put("headScale", AvatarHeadShape.scale(state.applied.headSize));
            row.put("agePreset", state.applied.agePreset); row.put("ageWeights", state.applied.ageWeights());
            row.put("availableShapes", state.basis.morph == null ? Collections.singleton("none") : state.basis.morph.shapes());
            row.put("bindBounds", state.bindBounds.clone()); result.add(row);
        }
        return result;
    }

    private static float[] bounds(float[] vertices) {
        float[] b = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < vertices.length; i++) {
            int axis = i % 3; b[axis] = Math.min(b[axis], vertices[i]); b[axis + 3] = Math.max(b[axis + 3], vertices[i]);
        }
        return b;
    }

    static List<TriangleMesh> sources(ColladaModel model) {
        List<TriangleMesh> result = new ArrayList<>();
        if (model == null || model.getGeometryArray() == null) return result;
        for (ColladaGeometry geometry : model.getGeometryArray()) {
            if (geometry == null || geometry.getTriangleMeshArray() == null) continue;
            for (TriangleMesh mesh : geometry.getTriangleMeshArray())
                if (mesh != null && mesh.getName() != null && (mesh.getName().startsWith("Avatar2BodyMesh")
                        || mesh.getName().startsWith("Avatar2EyesMesh"))) result.add(mesh);
        }
        return result;
    }

    private static final class MeshState {
        final Basis basis;
        AvatarHeadShape head;
        com.wurmonline.client.renderer.model.collada.math.Matrix4f hairFit;
        AvatarScalp scalp;
        WeakReference<ColladaModel> owner;
        GeometryProbe applied;
        boolean gpu;
        float renderScale = 1;
        FloatBuffer bindVertices;
        float[] bindBounds;
        int builds;
        MeshState(Basis basis, ColladaModel owner, GeometryProbe applied, boolean gpu) {
            this.basis = basis; this.head = basis.head; this.owner = new WeakReference<>(owner); this.applied = applied;
            this.gpu = gpu; this.bindBounds = bounds(basis.vertices);
            this.bindVertices = FloatBuffer.wrap(basis.vertices).asReadOnlyBuffer();
        }
    }

    /** Shared only as immutable data; live arrays and NIO buffers are per instance. */
    private static final class Basis {
        final float[] vertices, normals;
        final float floor, height;
        final String fingerprint;
        final MorphBank.Mesh morph;
        final AvatarHeadShape head;
        final int[] cranialVertices;
        final int[] scalpIndices;
        Basis(TriangleMesh mesh, float floor, float height, MorphBank bank, ColladaModel model) {
            vertices = mesh.getOriginalVertexBuffer().clone(); normals = mesh.getOriginalNormalBuffer().clone();
            this.floor = floor; this.height = height; fingerprint = fingerprint(mesh);
            morph = bank == null ? null : bank.matching(fingerprint);
            head = new AvatarHeadShape(model, floor + height, height);
            List<Integer> cap = new ArrayList<>();
            float cut = head.y - (head.y-head.upper)*.8f;
            for (int v=0;v<vertices.length/3;v++) if (vertices[v*3+1]>cut) cap.add(v);
            cranialVertices = new int[cap.size()];
            for (int i=0;i<cap.size();i++) cranialVertices[i]=cap.get(i);
            scalpIndices=mesh.getName().startsWith("Avatar2BodyMesh")
                    ?AvatarScalp.select(mesh,head,model.getJoint("head").getBoneIndex()):new int[0];
        }
    }

    /** Called before entering the height monitor, after geometry has completed. */
    static synchronized float[] headCameraDelta(ColladaModel model) {
        for (TriangleMesh mesh : sources(model)) {
            MeshState state=MESHES.get(mesh);
            Joint camera=model.getJoint("NullCamera0");
            if(state!=null && camera!=null) return state.head.delta(state.applied.headSize, camera.getOriginalBindMatrix());
        }
        return new float[3];
    }

    static synchronized AvatarScalp scalp(ColladaModel model) {
        for(TriangleMesh mesh:sources(model))if(mesh.getName().startsWith("Avatar2BodyMesh")) {
            MeshState state=MESHES.get(mesh);if(state==null)return null;
            if(state.scalp==null)state.scalp=new AvatarScalp(mesh,state.basis.scalpIndices,state.basis.vertices,state.basis.head);
            return state.scalp;
        }
        return null;
    }

    /** Cached cranial fit on stable vertex IDs; recomputed only after appearance changes. */
    static synchronized com.wurmonline.client.renderer.model.collada.math.Matrix4f headHairFit(ColladaModel model, boolean male) {
        for (TriangleMesh mesh : sources(model)) {
            MeshState state = MESHES.get(mesh);
            if (state == null || !mesh.getName().startsWith("Avatar2BodyMesh")) continue;
            if (state.hairFit == null) {
                float x0=Float.POSITIVE_INFINITY, z0=x0, x1=-x0, z1=-x0;
                float[] vertices=mesh.getOriginalVertexBuffer();
                for (int v : state.basis.cranialVertices) {
                    x0=Math.min(x0,vertices[v*3]); x1=Math.max(x1,vertices[v*3]);
                    z0=Math.min(z0,vertices[v*3+2]); z1=Math.max(z1,vertices[v*3+2]);
                }
                float headY=state.head.y+(state.head.upper-state.head.y)*AvatarHeadShape.scale(state.applied.headSize);
                com.wurmonline.client.renderer.model.collada.math.Matrix4f fit=new com.wurmonline.client.renderer.model.collada.math.Matrix4f();
                // Pinned bare backpart/upperFace0 cranial extents from local WOM,
                // above 20% of the crown-to-head-joint height. Excludes headwear.
                float sourceX0=male?-.073027f:-.070824f, sourceX1=male?.073027f:.070829f;
                float sourceZ0=male?-.08103f:-.073797f, sourceZ1=male?.110983f:.11026f;
                float sourceCrown=male?1.654048f:1.653191f;
                fit.m00=(x1-x0)/(sourceX1-sourceX0);
                fit.m11=(state.head.y-headY)/(sourceCrown-1.511672f);
                fit.m22=(z1-z0)/(sourceZ1-sourceZ0);
                fit.m03=(x0+x1-(sourceX0+sourceX1)*fit.m00)/2;
                fit.m13=state.head.y-(sourceCrown-1.617755f)*fit.m11;
                fit.m23=(z0+z1-(sourceZ0+sourceZ1)*fit.m22)/2-.000366f*fit.m22;
                if (!Float.isFinite(fit.m00) || !Float.isFinite(fit.m11) || !Float.isFinite(fit.m22)
                        || fit.m00<=0 || fit.m11<=0 || fit.m22<=0)
                    throw new IllegalStateException("Invalid Avatar cranial hair fit");
                state.hairFit=fit;
            }
            return new com.wurmonline.client.renderer.model.collada.math.Matrix4f(state.hairFit);
        }
        return null;
    }

    private static float headCrown(ColladaModel owner, GeometryProbe desired, float fallback) {
        for(TriangleMesh body:sources(owner)) if(body.getName().startsWith("Avatar2BodyMesh")) {
            MeshState state=MESHES.get(body);if(state==null)continue;
            if(desired.equals(state.applied))return state.head.y;
            // Normally the source body has already completed. Also support an
            // eyes-only baker/copy invocation without depending on mesh order.
            Basis basis=state.basis;
            if(!desired.hasAuthoredShape())return basis.head.y;
            float[] v=new float[basis.vertices.length], n=new float[basis.normals.length];
            basis.morph.deform(desired,basis.vertices,basis.normals,v,n);
            return bounds(v)[4];
        }
        return fallback;
    }

    private static final class MorphResources {
        // Exactly two resource families; no per-profile or per-frame cache.
        private static final Map<String,MorphBank> BANKS = new HashMap<>();
        static MorphBank forAvatar(ResourceUrl avatarUrl) {
            if (avatarUrl == null || !avatarUrl.getFilePath().startsWith("avatar2/body/")) return null;
            for (String body : new String[]{"male-bodybuilder", "female-bodybuilder"}) {
                if (avatarUrl.getFilePath().endsWith("/" + body + ".dae")) {
                    MorphBank bank = BANKS.get(body);
                    if (bank == null) try (java.io.InputStream in = avatarUrl.changeFilePath("avatar2/morph/" + body + ".avm").openStream()) {
                        bank = MorphBank.read(in);
                        if (!body.equals(bank.body)) throw new java.io.IOException("Wrong Avatar morph family");
                        BANKS.put(body, bank);
                    } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load Avatar morph resource: " + body, error); }
                    return bank;
                }
            }
            return null;
        }
    }

    /** Hash the imported binding, not just vertex count or an authoring mesh name. */
    private static String fingerprint(TriangleMesh mesh) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(new OutputStream() {
                @Override public void write(int ignored) { }
                @Override public void write(byte[] bytes, int offset, int length) { }
            }, digest)))) {
                out.writeUTF("avatar2-imported-bind-v1"); out.writeUTF(mesh.getName());
                out.writeInt(mesh.getVertexCount()); out.writeInt(mesh.getIndexCount());
                for (float v : mesh.getOriginalVertexBuffer()) out.writeFloat(v);
                for (float v : mesh.getOriginalNormalBuffer()) out.writeFloat(v);
                IntBuffer indices = mesh.getIndexBuffer();
                out.writeInt(indices == null ? 0 : indices.capacity());
                if (indices != null) for (int i = 0; i < indices.capacity(); i++) out.writeInt(indices.get(i));
                for (int layer = 0; layer < 2; layer++) {
                    FloatBuffer uv = mesh.getTextureCoordBuffer(layer);
                    out.writeInt(uv == null ? 0 : uv.capacity());
                    if (uv != null) for (int i = 0; i < uv.capacity(); i++) out.writeFloat(uv.get(i));
                }
                FloatBuffer weights = mesh.getWeightBuffer();
                out.writeInt(weights == null ? 0 : weights.capacity());
                if (weights != null) for (int i = 0; i < weights.capacity(); i++) {
                    out.writeFloat(weights.get(i)); out.writeShort(mesh.getBoneBuffer().get(i));
                }
                Joint[] bones = mesh.getBones();
                out.writeInt(bones == null ? 0 : bones.length);
                if (bones != null) for (Joint bone : bones) out.writeUTF(bone == null ? "" : bone.getName());
                matrix(out, mesh.getBindmatrix());
                if (bones != null) for (Joint bone : bones) if (bone != null) {
                    matrix(out, bone.getOriginalBindMatrix()); matrix(out, bone.getOriginalInvertBindMatrix());
                }
            }
            StringBuilder text = new StringBuilder();
            for (byte b : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (Exception error) { throw new IllegalStateException("Cannot fingerprint Avatar binding", error); }
    }

    private static void matrix(DataOutputStream out, Matrix4f matrix) throws java.io.IOException {
        out.writeBoolean(matrix != null);
        if (matrix == null) return;
        for (float value : new float[]{matrix.m00,matrix.m01,matrix.m02,matrix.m03,
                matrix.m10,matrix.m11,matrix.m12,matrix.m13,matrix.m20,matrix.m21,matrix.m22,matrix.m23,
                matrix.m30,matrix.m31,matrix.m32,matrix.m33}) out.writeFloat(value);
    }
}
