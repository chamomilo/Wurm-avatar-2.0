package org.avatar2.client;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

/** Uses the production hooks and Wurm shared loader; no substituted mesh/baker. */
public final class GeometryHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]); ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new File(root, "build/classes/java/main").getPath());
        pool.appendClassPath(new File(root, "build/classes/java/test").getPath());
        pool.appendClassPath(new File(root, "build/resources/main").getPath());
        for (String jar : new String[]{"client-patched.jar", "common.jar", "javassist.jar", "modlauncher.jar"})
            pool.appendClassPath(new File(root, "libs/" + jar).getPath());
        ClientHooks.installWalkMotion(pool);
        ClientHooks.installAvatarWeights(pool);
        ClientHooks.installGeometryPrototype(pool);
        ClientHooks.installHeightPrototype(pool);
        ClientHooks.installEquipmentSockets(pool);
        ClientHooks.installFirstPerson(pool);
        ClientHooks.installAgeSkin(pool);
        DefrostingClassLoader loader = new DefrostingClassLoader(pool);
        if(args.length>1 && args[1].equals("firstPersonOnly")) {
            loader.loadClass("org.avatar2.client.FirstPersonRuntimeHarness").getMethod("run",String.class).invoke(null,root.getAbsolutePath());return;
        }
        if(args.length>1 && args[1].equals("ageOnly")) {
            loader.loadClass("org.avatar2.client.BodyAgeRuntimeHarness").getMethod("run",String.class).invoke(null,root.getAbsolutePath());return;
        }
        if(args.length>1 && args[1].equals("headOnly")) {
            loader.loadClass("org.avatar2.client.HeadRuntimeHarness").getMethod("run",String.class).invoke(null,root.getAbsolutePath());return;
        }
        if (args.length > 1 && args[1].equals("bakeMorphs")) {
            loader.loadClass("org.avatar2.client.MorphBankBaker").getMethod("run", String.class).invoke(null, root.getAbsolutePath());
            return;
        }
        if (args.length > 1) loader.loadClass("com.wurmonline.client.resources.MorphPackProbe")
                .getMethod("run", String.class).invoke(null, args[1]);
        Class<?> harness = loader.loadClass("org.avatar2.client.GeometryRuntimeHarness");
        harness.getMethod("run", String.class).invoke(null, root.getAbsolutePath());
        loader.loadClass("org.avatar2.lab.HeightRuntimeHarness").getMethod("run", String.class).invoke(null, root.getAbsolutePath());
        loader.loadClass("org.avatar2.client.ProfileAppearanceRuntimeHarness").getMethod("run", String.class).invoke(null, root.getAbsolutePath());
        loader.loadClass("org.avatar2.client.FirstPersonRuntimeHarness").getMethod("run", String.class).invoke(null, root.getAbsolutePath());
        System.out.println("AVATAR2_GEOMETRY_HOOK_OK all four bodies, independent copies, real single-source baker, CPU skinning/normals, native GPU DQ and shader math/reset; GL rendering requires live client");
    }
}
