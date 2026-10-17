package org.avatar2.client;

import javassist.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;

/** Native paper-doll transform and dispatch; substitute only rendering and animation scheduling. */
public final class PortraitHeightRuntimeProbe {
    public static void main(String[] args) throws Exception {
        ClassPool pool=new ClassPool(true);
        String harness="org.avatar2.client.PortraitHeightRuntimeHarness";
        for(CtMethod render:pool.get("com.wurmonline.client.renderer.model.ModelResourceWrapper").getDeclaredMethods("render"))
            if(render.getParameterTypes().length==11)render.setBody("{ "+harness+".capture($2); }");
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData").getDeclaredMethod("getAnimation").setBody("{ return null; }");
        ClientHooks.installAvatarWeights(pool);ClientHooks.installWalkMotion(pool);
        ClientHooks.installGeometryPrototype(pool);ClientHooks.installHeightPrototype(pool);
        if(args.length<2 || !args[1].equals("reproduce"))ClientHooks.installPortraitHeight(pool);
        new DefrostingClassLoader(pool).loadClass(harness).getMethod("run",String.class,boolean.class)
                .invoke(null,args[0],args.length>1);
    }
}
