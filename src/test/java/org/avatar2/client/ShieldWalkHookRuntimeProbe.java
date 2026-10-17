package org.avatar2.client;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

public final class ShieldWalkHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        ClassPool pool = new ClassPool(true);
        for (String dir : new String[]{"build/classes/java/main", "build/classes/java/test", "build/resources/main"})
            pool.appendClassPath(new File(root, dir).getPath());
        for (String jar : new String[]{"client-patched.jar", "common.jar", "javassist.jar", "modlauncher.jar"})
            pool.appendClassPath(new File(root, "libs/" + jar).getPath());
        ClientHooks.installWalkMotion(pool);
        ClientHooks.installAvatarWeights(pool);
        ClientHooks.installHeightPrototype(pool);
        ClientHooks.installEquipmentSockets(pool);
        new DefrostingClassLoader(pool).loadClass("org.avatar2.client.ShieldWalkRuntimeHarness")
                .getMethod("run", String.class, String.class).invoke(null, root.getAbsolutePath(), args[1]);
    }
}
