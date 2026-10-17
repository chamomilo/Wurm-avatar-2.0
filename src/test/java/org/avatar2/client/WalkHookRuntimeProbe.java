package org.avatar2.client;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

/** Runs the actual patched sampler under Wurm's shared loader, not a mock. */
public final class WalkHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);ClassPool pool=new ClassPool(true);
        pool.appendClassPath(new File(root,"build/classes/java/main").getPath());
        pool.appendClassPath(new File(root,"build/classes/java/test").getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})
            pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        ClientHooks.installWalkMotion(pool);
        DefrostingClassLoader loader=new DefrostingClassLoader(pool);
        Class<?> harness=loader.loadClass("org.avatar2.client.WalkRuntimeHarness");
        if(harness.getClassLoader()!=loader)throw new AssertionError("Harness did not use Wurm loader");
        harness.getMethod("run",String.class).invoke(null,root.getAbsolutePath());
        loader.loadClass("org.avatar2.client.TurnRuntimeHarness").getMethod("run",String.class).invoke(null,root.getAbsolutePath());
        System.out.println("AVATAR2_WALK_HOOK_OK actual patched Wurm sampler, shared loader, all four bodies");
    }
}
