package org.avatar2.client;

import javassist.*;
import javassist.expr.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

/** Real local movement selector, Cell dispatcher, animation queues and worker.
 * Only world/input getters and the wall clock are controlled; no selector,
 * dispatcher, queue, FK, blend or skin implementation is substituted.
 */
public final class GameplayStartRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);ClassPool pool=new ClassPool(true);
        pool.appendClassPath(new File(root,"build/classes/java/main").getPath());
        pool.appendClassPath(new File(root,"build/classes/java/test").getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})
            pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        String fixture="org.avatar2.client.GameplayStartRuntimeHarness";
        CtClass world=pool.get("com.wurmonline.client.game.World");
        world.getDeclaredMethod("getFrameDelta").setBody("{ return "+fixture+".dt; }");
        world.getDeclaredMethod("getTickFraction").setBody("{ return 1f; }");
        world.getDeclaredMethod("getPlayer").setBody("{ return "+fixture+".player; }");
        CtClass player=pool.get("com.wurmonline.client.game.PlayerObj");
        player.getDeclaredMethod("isFlying").setBody("{ return false; }");
        player.getDeclaredMethod("isCarrierController").setBody("{ return false; }");
        player.getDeclaredMethod("getCarrierCreature").setBody("{ return null; }");
        player.getDeclaredMethod("hasTarget").setBody("{ return false; }");
        CtClass body=pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable");
        body.getDeclaredMethod("getHPosValue").setBody("{ return 2f; }");
        body.getDeclaredMethod("getSwimHeightModified").setBody("{ return 0f; }");
        pool.get("org.avatar2.client.AvatarLocomotion").instrument(new ExprEditor() {
            @Override public void edit(MethodCall call)throws CannotCompileException {
                if(call.getClassName().equals("java.lang.System") && call.getMethodName().equals("nanoTime"))
                    call.replace("{ $_="+fixture+".now; }");
            }
        });
        ClientHooks.installWalkMotion(pool);
        DefrostingClassLoader loader=new DefrostingClassLoader(pool);
        loader.loadClass(fixture).getMethod("run",String.class).invoke(null,root.getAbsolutePath());
    }
}
