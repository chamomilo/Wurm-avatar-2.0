package org.avatar2.client;

import javassist.*;
import javassist.expr.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.File;

/** Runs the same deterministic renderer/telemetry interleaving against old or current adapters. */
public final class AvatarStabilityProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);ClassPool pool=new ClassPool(true);
        pool.insertClassPath(args.length>1?args[1]:new File(root,"build/classes/java/main").getPath());
        pool.appendClassPath(new File(root,"build/classes/java/test").getPath());
        pool.appendClassPath(new File(root,"build/resources/main").getPath());
        for(String name:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})pool.appendClassPath(new File(root,"libs/"+name).getPath());
        ClientHooks.installAvatarWeights(pool);ClientHooks.installGeometryPrototype(pool);
        ClientHooks.installHeightPrototype(pool);ClientHooks.installEquipmentSockets(pool);
        for(CtMethod transform:pool.get("org.avatar2.client.AvatarEquipmentSockets").getDeclaredMethods("transform"))
        transform.instrument(new ExprEditor(){
            public void edit(MethodCall call)throws CannotCompileException {
                if(call.getClassName().equals("org.avatar2.client.AvatarHeightPrototype")&&call.getMethodName().equals("scale"))
                    call.replace("{ org.avatar2.client.AvatarStabilityHarness.rendezvous();$_=$proceed($$); }");
            }
        });
        pool.get("org.avatar2.client.AvatarHeightPrototype").getDeclaredMethod("describe").instrument(new ExprEditor(){
            public void edit(MethodCall call)throws CannotCompileException {
                if(call.getMethodName().equals("getNullAttachmentOffset"))
                    call.replace("{ org.avatar2.client.AvatarStabilityHarness.rendezvous();$_=$proceed($$); }");
            }
        });
        new DefrostingClassLoader(pool).loadClass("org.avatar2.client.AvatarStabilityHarness")
                .getMethod("run",String.class,boolean.class).invoke(null,root.getAbsolutePath(),args.length>1);
    }
}
