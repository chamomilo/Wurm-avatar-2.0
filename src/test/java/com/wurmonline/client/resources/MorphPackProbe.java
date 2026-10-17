package com.wurmonline.client.resources;

import com.wurmonline.client.renderer.model.collada.importer.ColladaImporter;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import org.avatar2.client.AvatarAnimationBridge;
import org.avatar2.client.AvatarGeometryPrototype;
import org.avatar2.core.GeometryProbe;
import java.io.*;

/** Real JarPack/PackResourceUrl path, including the production animation bridge. */
public final class MorphPackProbe {
    public static void run(String file) throws Exception {
        JarPack pack = new JarPack(new File(file));
        try {
            for (String body : new String[]{"male-bodybuilder", "female-bodybuilder"}) {
                ResourceUrl url = new PackResourceUrl(pack, "avatar2/body/" + body + ".dae");
                AvatarAnimationBridge.beginImport(url);
                ColladaModel model;
                try (InputStream in = url.openStream()) { model = ColladaImporter.loadModel(url.toString(), in); }
                finally { AvatarAnimationBridge.endImport(); }
                if (!AvatarAnimationBridge.loadIfAvatar(model, url)) throw new AssertionError("Production Avatar bridge failed");
                if (!AvatarGeometryPrototype.availableShapes(model).containsAll(GeometryProbe.SHAPES))
                    throw new AssertionError("PackResourceUrl morph load/binding failed: " + body);
            }
            System.out.println("AVATAR2_MORPH_PACK_OK production importer/bridge, real JarPack and PackResourceUrl, both Bodybuilder bindings");
        } finally { pack.close(); }
    }
}
