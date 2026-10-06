package org.avatar2.client;

import javassist.*;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class LabHookTest {
    @Test public void hooksTransformActualPinnedClientWithoutLoadingGameClasses() throws Exception {
        ClassPool pool=new ClassPool(true);File root=new File(System.getProperty("avatar2ProjectDir"));
        pool.appendClassPath(new File(root,"build/classes/java/main").getPath());
        for(String jar:new String[]{"client-patched.jar","common.jar","javassist.jar","modlauncher.jar"})pool.appendClassPath(new File(root,"libs/"+jar).getPath());
        ClientHooks.installAnimationLab(pool);
        for(String name:new String[]{"com.wurmonline.client.renderer.model.collada.ColladaAnimationJob","com.wurmonline.client.WurmClientBase"})assertTrue(pool.get(name).toBytecode().length>0);
    }
}
