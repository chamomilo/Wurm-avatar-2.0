package org.avatar2.client;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

public final class EquipmentSocketHookRuntimeProbe {
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);ClassPool pool=new ClassPool(true);
        pool.appendClassPath(new File(root,"build/classes/java/main").getPath());
        pool.appendClassPath(new File(root,"build/classes/java/test").getPath());
        pool.appendClassPath(new File(root,"build/resources/main").getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})
            pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        ClientHooks.installWalkMotion(pool);ClientHooks.installAvatarWeights(pool);
        ClientHooks.installHeightPrototype(pool);ClientHooks.installEquipmentSockets(pool);
        new DefrostingClassLoader(pool).loadClass("org.avatar2.client.EquipmentSocketRuntimeHarness")
                .getMethod("run",String.class).invoke(null,root.getAbsolutePath());
    }
}
