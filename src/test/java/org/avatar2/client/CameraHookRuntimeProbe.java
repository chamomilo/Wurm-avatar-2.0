package org.avatar2.client;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

public final class CameraHookRuntimeProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);ClassPool pool=new ClassPool(true);
        boolean old=args.length>1;
        if(old)pool.insertClassPath(args[1]);
        else pool.insertClassPath(new File(root,"build/classes/java/main").getPath());
        pool.appendClassPath(new File(root,"build/classes/java/test").getPath());
        pool.appendClassPath(new File(root,"build/resources/main").getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})
            pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        DefrostingClassLoader loader=new DefrostingClassLoader(pool);
        loader.delegateLoadingOf("javassist.");
        Class<?> hooks=loader.loadClass("org.avatar2.client.ClientHooks");
        for(String name:new String[]{"installAvatarWeights","installGeometryPrototype","installHeightPrototype"}) {
            java.lang.reflect.Method method=hooks.getDeclaredMethod(name,ClassPool.class);method.setAccessible(true);method.invoke(null,pool);
        }
        loader.loadClass("org.avatar2.lab.CameraRuntimeHarness").getMethod("run",String.class,boolean.class)
                .invoke(null,root.getAbsolutePath(),old);
    }
}
