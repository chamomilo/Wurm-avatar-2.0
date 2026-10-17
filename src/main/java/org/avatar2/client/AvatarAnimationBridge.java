package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.resources.ResourceUrl;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads only animations retargeted for the anatomical Avatar 2.0 bind pose. */
public final class AvatarAnimationBridge {
    private static final Logger LOG = Logger.getLogger("Avatar2");
    private static final ThreadLocal<Boolean> AVATAR_IMPORT =
            new ThreadLocal<Boolean>();

    private AvatarAnimationBridge() { }

    /** Marks the Collada loader thread before it enters Wurm's importer. */
    public static void beginImport(ResourceUrl url) {
        AVATAR_IMPORT.set(url != null && url.getFilePath() != null
                && url.getFilePath().replace('\\', '/')
                .startsWith("avatar2/body/"));
    }

    /** Clears loader-thread state even when Wurm rejects a Collada resource. */
    public static void endImport() {
        AVATAR_IMPORT.remove();
    }

    /**
     * Replaces Wurm's broken normalizer while an Avatar body is being imported.
     *
     * <p>This must run inside {@code ColladaImporter.loadModel}. Waiting until
     * the importer returns is too late: a four-influence vertex has already
     * caused the first weight of the following vertex to be overwritten.</p>
     */
    public static boolean normalizeDuringAvatarImport(TriangleMesh mesh) {
        if (!Boolean.TRUE.equals(AVATAR_IMPORT.get())) return false;
        normalizeSkinWeights(mesh);
        return true;
    }

    /** Called from the patched Collada loader. True means the normal XML load is replaced. */
    public static boolean loadIfAvatar(ColladaModel model, ResourceUrl avatarUrl) {
        if (model == null || avatarUrl == null
                || !avatarUrl.getFilePath().startsWith("avatar2/body/")) return false;
        // Blender's Collada exporter identifies the skeleton root in the scene,
        // but Wurm's importer does not copy that identifier into rootJointName.
        // The animation worker dereferences it without a null check.
        ensureRoot(model);
        int repairedWeights = normalizeSkinWeights(model);

        try {
            AvatarGeometryPrototype.register(model, avatarUrl);
            AvatarAgeSkin.register(model, avatarUrl);
            ColladaPropertiesXml.load(model, avatarUrl, false);
            com.wurmonline.client.renderer.model.collada.animation.AvatarWalkMotion.register(model);
            AvatarEquipmentSockets.register(model);
            AvatarHair.register(model, !avatarUrl.getFilePath().contains("female-"));
            AvatarFirstPerson.register(model);
            // Vanilla WOM matrices are never loaded into the native MakeHuman
            // hierarchy. Matching action names is only a gameplay-state contract.
            LOG.info("Attached bind-safe Avatar 2.0 locomotion to "
                    + avatarUrl.getFilePath() + "; normalized "
                    + repairedWeights + " skin vertices");
            return true;
        } catch (Throwable error) {
            LOG.log(Level.SEVERE, "Could not attach Avatar 2.0 animations to "
                    + avatarUrl.getFilePath(), error);
            return false;
        }
    }

    /** Applies the root name required by ColladaAnimationJob. */
    public static void ensureRoot(ColladaModel model) {
        if (model == null) throw new IllegalArgumentException("model");
        if (model.getJoint("Root") == null) {
            throw new IllegalStateException("Avatar 2.0 model has no Root joint");
        }
        if (model.getRootJointName() == null
                || model.getJoint(model.getRootJointName()) == null) {
            model.setRootJointName("Root");
        }
    }

    /**
     * Repairs a Wurm client bug in TriangleMesh.normalizeBoneWeights().
     *
     * <p>For a four-weight vertex the vanilla loop reads slot four instead of
     * its loop variable, thereby scaling the first weight of the next vertex.
     * UV-split copies then deform by different amounts and expose triangular
     * cracks. This repair is deliberately scoped to Avatar 2.0 models.</p>
     */
    public static int normalizeSkinWeights(ColladaModel model) {
        if (model == null || model.getGeometryArray() == null) return 0;
        int repaired = 0;
        for (ColladaGeometry geometry : model.getGeometryArray()) {
            if (geometry == null || geometry.getTriangleMeshArray() == null) continue;
            for (TriangleMesh mesh : geometry.getTriangleMeshArray()) {
                repaired += normalizeSkinWeights(mesh);
            }
        }
        return repaired;
    }

    /** Correct implementation of Wurm's four-slot skin-weight normalizer. */
    public static int normalizeSkinWeights(TriangleMesh mesh) {
        if (mesh == null) return 0;
        FloatBuffer weights = mesh.getWeightBuffer();
        ShortBuffer bones = mesh.getBoneBuffer();
        int vertices = mesh.getVertexCount();
        if (weights == null || bones == null
                || weights.capacity() < vertices * 4
                || bones.capacity() < vertices * 4) return 0;
        int repaired = 0;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int base = vertex * 4;
            float total = 0.0f;
            for (int influence = 0; influence < 4; influence++) {
                float weight = weights.get(base + influence);
                if (!Float.isNaN(weight) && !Float.isInfinite(weight)
                        && weight > 0.0f) total += weight;
            }
            if (total <= 0.000001f || Float.isNaN(total)
                    || Float.isInfinite(total)) {
                weights.put(base, 1.0f);
                bones.put(base, (short) 0);
                for (int influence = 1; influence < 4; influence++) {
                    weights.put(base + influence, 0.0f);
                    bones.put(base + influence, (short) 0);
                }
                repaired++;
                continue;
            }
            if (Math.abs(total - 1.0f) > 0.000001f) repaired++;
            float inverse = 1.0f / total;
            for (int influence = 0; influence < 4; influence++) {
                int offset = base + influence;
                float weight = weights.get(offset);
                if (Float.isNaN(weight) || Float.isInfinite(weight)
                        || weight <= 0.0f) {
                    weights.put(offset, 0.0f);
                    bones.put(offset, (short) 0);
                } else {
                    weights.put(offset, weight * inverse);
                }
            }
        }
        return repaired;
    }

}
