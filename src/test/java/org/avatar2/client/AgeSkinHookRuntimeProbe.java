package org.avatar2.client;

import javassist.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;

/** Production import/copy/bake/draw hooks, replacing only GL submission and texture upload. */
public final class AgeSkinHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        ClassPool pool = new ClassPool(true);
        String harness = "com.wurmonline.client.resources.AgeSkinRuntimeHarness";
        CtClass renderer = pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelRender");
        renderer.getDeclaredMethod("renderTriangleList").setBody(
                "{ if ($9.hasGPUSkinning()) this.renderGPUSkinningTriangleList($$); else " + harness + ".draw($8,$9,$10,false); }");
        renderer.getDeclaredMethod("renderGPUSkinningTriangleList").setBody(
                "{ " + harness + ".draw($8,$9,$10,true); }");
        // Keep the actual loader, PNG preparation and deferred ResourceTexture.
        // Only GPU upload is replaced. Queue building deliberately has no GL context.
        pool.get("com.wurmonline.client.renderer.backend.Backend").getDeclaredMethod("isGLThread").setBody("{ return false; }");
        pool.get("com.wurmonline.client.resources.textures.ResourceTexture").getDeclaredMethod("reinit").setBody(
                "{ " + harness + ".initializeTexture(this); }");
        boolean legacy=args.length>2&&"legacy".equals(args[2]);
        if(legacy)pool.get("org.avatar2.client.AvatarAgeSkin").getDeclaredMethod("usable").setBody(
                "{ return $1 != null && $1.isValid(); }");
        ClientHooks.installAvatarWeights(pool);ClientHooks.installWalkMotion(pool);
        ClientHooks.installGeometryPrototype(pool);ClientHooks.installHeightPrototype(pool);
        ClientHooks.installEquipmentSockets(pool);ClientHooks.installAgeSkin(pool);
        DefrostingClassLoader loader = new DefrostingClassLoader(pool);
        try {
            loader.loadClass(harness).getMethod("run",String.class,String.class).invoke(null,args[0],args[1]);
            if(legacy)throw new AssertionError("Legacy age-skin predicate unexpectedly passed deferred GL loading");
        } catch(java.lang.reflect.InvocationTargetException failure) {
            Throwable cause=failure.getCause();
            if(legacy&&cause instanceof AssertionError&&cause.getMessage().startsWith("Source draw skin:")) {
                System.out.println("AVATAR2_AGE_SKIN_LEGACY_REPRODUCED native loader returned id=-1 / needReinit=true; 0.9.0 retained Original instead of Old");
                return;
            }
            throw failure;
        }
    }
}
