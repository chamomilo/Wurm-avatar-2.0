package org.avatar2.client;

import javassist.*;
import javassist.expr.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

/** Production hair/native worker hooks. Substitute asset scheduling and queue submission only. */
public final class HairHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]); ClassPool pool=new ClassPool(true);
        for(String path:new String[]{"build/classes/java/main","build/classes/java/test","build/resources/main"})
            pool.appendClassPath(new File(root,path).getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})
            pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        ClientHooks.installAvatarWeights(pool); ClientHooks.installWalkMotion(pool);
        ClientHooks.installGeometryPrototype(pool); ClientHooks.installHeightPrototype(pool);
        ClientHooks.installEquipmentSockets(pool); ClientHooks.installFirstPerson(pool);
        ClientHooks.installAnimationLab(pool); ClientHooks.installHair(pool);
        String harness="org.avatar2.client.HairRuntimeHarness";
        pool.get("com.wurmonline.client.game.World").getDeclaredMethod("getFrameDelta").setBody("{return 1.0f/60;}");
        pool.get("com.wurmonline.client.game.World").getDeclaredMethod("getTickFraction").setBody("{return 1f;}");
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData").getDeclaredMethod("calculateSkins")
                .insertBefore("{ "+harness+".awaitBeforeSkin(); }");
        pool.get("com.wurmonline.client.renderer.cell.AttachedCellRenderable").getDeclaredMethod("prepareRender").instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws CannotCompileException {
                if(call.getClassName().endsWith(".StaticModelRenderable")&&call.getMethodName().equals("prepareRender"))call.replace("{}");
            }
        });
        pool.get("com.wurmonline.client.renderer.model.ModelResourceLoader").getClassInitializer().setBody("{}");
        pool.get("com.wurmonline.client.renderer.model.ModelResourceLoader").getDeclaredMethod("getBrokenModel").setBody("{return null;}");
        pool.get("com.wurmonline.client.renderer.model.ModelResourceLoader").getDeclaredMethod("storeWeakReference").setBody("{}");
        pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh").getDeclaredMethod("setDefaultMaterial").setBody("{}");
        for(String method:new String[]{"renderTriangleList","renderGPUSkinningTriangleList"})
            pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelRender").getDeclaredMethod(method)
                    .setBody("{ "+harness+".submitted($9,$2,$3); }");
        CtClass body=pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable");
        body.getDeclaredMethod("renderNullEquipment").setBody("{ "+harness+".draw($2,$3,$4,$5,$6,$8); }");
        body.getDeclaredMethod("renderHair").instrument(new ExprEditor() {
            @Override public void edit(NewExpr creation) throws CannotCompileException {
                if(creation.getClassName().endsWith(".AttachedCellRenderable"))
                    creation.replace("{ $_="+harness+".createHair($1,$2,$4); }");
            }
        });
        new DefrostingClassLoader(pool).loadClass(harness).getMethod("run",String.class).invoke(null,root.getAbsolutePath());
    }
}
