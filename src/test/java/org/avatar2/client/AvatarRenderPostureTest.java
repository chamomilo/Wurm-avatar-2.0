package org.avatar2.client;

import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.ColladaImporter;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Quaternion;
import com.wurmonline.math.Vector3f;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import sun.misc.Unsafe;
import static org.junit.Assert.*;

public class AvatarRenderPostureTest {
    @Test public void avatarStaysUprightAtEveryHeadingAndVanillaKeepsNativePitch() throws Exception {
        PlayerBodyRenderable avatar=body("avatar2/body/male-bodybuilder.dae");
        PlayerBodyRenderable vanilla=body("creature/humanoid/human/player/male.dae");
        float fixed=(float)Math.toRadians(-4);
        Quaternion nativeView=new Quaternion().fromAngles(fixed,0,0);
        float[] saved=nativeView.getQuat().clone();
        float largestNativeTilt=0;
        for(int heading=0;heading<360;heading++){
            float yaw=(float)Math.toRadians(180-heading);
            Quaternion corrected=new Quaternion().fromAngles(AvatarRenderPosture.pitch(avatar,fixed),yaw,0);
            corrected.mult(AvatarRenderPosture.viewRotation(avatar,nativeView));
            Vector3f up=corrected.rotate(new Vector3f(0,1,0),new Vector3f());
            assertEquals(0,up.x,1e-6);assertEquals(1,up.y,1e-6);assertEquals(0,up.z,1e-6);
            Vector3f forward=corrected.rotate(new Vector3f(0,0,1),new Vector3f());
            Vector3f expected=new Quaternion().fromAngles(0,yaw,0).rotate(new Vector3f(0,0,1),new Vector3f());
            assertEquals(expected.x,forward.x,1e-6);assertEquals(expected.z,forward.z,1e-6);
            Quaternion original=new Quaternion().fromAngles(fixed,yaw,0);original.mult(nativeView);
            Quaternion fallback=new Quaternion().fromAngles(AvatarRenderPosture.pitch(vanilla,fixed),yaw,0);
            assertSame(nativeView,AvatarRenderPosture.viewRotation(vanilla,nativeView));
            fallback.mult(AvatarRenderPosture.viewRotation(vanilla,nativeView));
            assertArrayEquals(original.getQuat(),fallback.getQuat(),0);
            Vector3f nativeUp=original.rotate(new Vector3f(0,1,0),new Vector3f());
            largestNativeTilt=Math.max(largestNativeTilt,(float)Math.toDegrees(Math.acos(Math.min(1,nativeUp.y))));
        }
        assertArrayEquals(saved,nativeView.getQuat(),0);
        assertEquals(8,largestNativeTilt,.001);
        assertEquals(fixed,AvatarRenderPosture.pitch(null,fixed),0);
        assertSame(nativeView,AvatarRenderPosture.viewRotation(null,nativeView));
        System.out.println("AVATAR2_RENDER_POSTURE_OK 360 headings; native maximum tilt="+largestNativeTilt+"; Avatar upright; vanilla/null fallback exact");
    }

    private static PlayerBodyRenderable body(String path) throws Exception {
        File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/male-bodybuilder.dae");
        ColladaModel model;
        try(InputStream input=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),input);}
        FakeResourceUrl url=new FakeResourceUrl(new URL("file:"+path));
        ColladaModelData data=new ColladaModelData(url,model);
        Constructor<ModelResourceWrapper> constructor=ModelResourceWrapper.class.getDeclaredConstructor(com.wurmonline.client.resources.ResourceUrl.class,com.wurmonline.client.renderer.model.AbstractModelData.class);
        constructor.setAccessible(true);ModelResourceWrapper wrapper=constructor.newInstance(url,data);
        // Test only: avoid starting the game's player/texture subsystem.
        Field access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        PlayerBodyRenderable body=(PlayerBodyRenderable)((Unsafe)access.get(null)).allocateInstance(PlayerBodyRenderable.class);
        Field field=PlayerBodyRenderable.class.getDeclaredField("playerModel");field.setAccessible(true);field.set(body,wrapper);
        return body;
    }
}
