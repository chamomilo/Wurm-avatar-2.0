package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.AbstractColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.resources.ResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.Texture;
import com.wurmonline.client.resources.textures.TextureLoader;
import org.avatar2.core.AgeSkin;
import org.avatar2.lab.AnimationLab;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Render-thread texture selection. Never edits shared materials, meshes or baked lists. */
public final class AvatarAgeSkin {
    private static final Logger LOG = Logger.getLogger("Avatar2");
    private static final Map<ColladaModel, String> MODELS = new WeakHashMap<>();
    private static final Map<TriangleMesh, String> MESHES = new WeakHashMap<>();
    // Exactly four extra textures (two ages per sex), independent of body copies/commands.
    private static final Map<String, ResourceTexture> TEXTURES = new HashMap<>();

    private AvatarAgeSkin() { }

    public static synchronized void register(ColladaModel model, ResourceUrl url) {
        if (model == null || url == null) return;
        String path = url.getFilePath().replace('\\', '/');
        String sex = path.matches("avatar2/body/male-(athletic|bodybuilder)\\.dae") ? "male"
                : path.matches("avatar2/body/female-(athletic|bodybuilder)\\.dae") ? "female" : null;
        if (sex == null) return;
        MODELS.put(model, sex);
        for (TriangleMesh mesh : AvatarGeometryPrototype.sources(model)) {
            if (mesh.getColladaMaterial() != null
                    && AgeSkin.file(sex, "young").equals(mesh.getColladaMaterial().getTextureName()))
                MESHES.put(mesh, sex);
        }
    }

    public static synchronized void copiedMesh(TriangleMesh source, TriangleMesh copy) {
        String sex = MESHES.get(source);
        if (sex != null) MESHES.put(copy, sex);
    }

    public static synchronized void copiedModel(ColladaModel source, ColladaModel copy) {
        String sex = MODELS.get(source);
        if (sex != null) MODELS.put(copy, sex);
    }

    public static synchronized Set<String> available(ColladaModel model) {
        return MODELS.containsKey(model) ? AgeSkin.PRESETS : Collections.singleton("original");
    }

    public static void validate(ColladaModel model, String preset) {
        AgeSkin.validate(preset);
        if (!available(model).contains(preset)) throw new IllegalArgumentException("Age skin requires a loaded Avatar body");
    }

    /** Native CPU/GPU entries resolve the applied local profile or a world-only Lab override. */
    public static Texture texture(AbstractColladaModelData data, TriangleMesh mesh, Texture original) {
        if (data == null || mesh == null) return original;
        String preset = AnimationLab.skinFor(data.getModel());
        // The shipped young texture is byte-identical to the original. No extra upload needed.
        if ("original".equals(preset) || "young".equals(preset)) return original;
        String sex;
        synchronized (AvatarAgeSkin.class) {
            sex = MODELS.get(data.getModel());
            if (sex == null || !sex.equals(MESHES.get(mesh))) return original;
        }
        // Source draws pass a null override: the native renderer resolves the
        // diffuse texture from this map later. Do the same read before selecting
        // an age skin, while leaving the map and the native fallback untouched.
        Texture nativeTexture = original;
        if (nativeTexture == null && mesh.getColladaMaterial() != null)
            nativeTexture = data.getLoadedTextures().get(mesh.getColladaMaterial());
        if (nativeTexture == null) return original;
        String file = AgeSkin.file(sex, preset);
        ResourceTexture selected;
        synchronized (TEXTURES) {
            if (!TEXTURES.containsKey(file)) {
                try {
                    ResourceUrl url = data.getUrl().derive(file);
                    if (!url.exists()) throw new IllegalStateException("Missing age skin " + file);
                    selected = ResourceTextureLoader.getInternalTexture(url, TextureLoader.Filter.MIPMAP, true, true, true);
                    // Queue construction can run outside Wurm's GL thread. Its
                    // loader returns id=-1 / needReinit=true until native Queue
                    // uploads the texture. Keep and enqueue that valid request.
                    if (!usable(selected)) throw new IllegalStateException("Could not load age skin " + file);
                    TEXTURES.put(file, selected);
                } catch (Exception failure) {
                    TEXTURES.put(file, null);
                    LOG.log(Level.WARNING, "Age skin unavailable: " + file + " (" + failure + "); retaining original texture", failure);
                }
            }
            selected = TEXTURES.get(file);
        }
        return usable(selected) ? selected : original;
    }

    private static boolean usable(ResourceTexture texture) {
        return texture != null && (texture.isValid() || texture.needReinit());
    }
}
