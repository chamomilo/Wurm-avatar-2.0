package org.avatar2.client;

import com.wurmonline.client.renderer.*;
import com.wurmonline.client.renderer.cell.AttachedCellRenderable;
import com.wurmonline.client.renderer.model.*;
import com.wurmonline.client.renderer.model.collada.*;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.FakeResourceUrl;
import org.avatar2.core.*;
import org.avatar2.lab.AnimationLab;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Execute the same renderPaperDoll used by Healthbar, with real saved height and head coordinates. */
public final class PortraitHeightRuntimeHarness {
    private static Matrix captured;
    public static void capture(Matrix matrix){captured=new Matrix(matrix);}
    public static void run(String root,boolean reproduce) throws Exception {
        int cases=0;float maxDrift=0;
        try {
            AnimationLab.stop();
            for(AvatarProfile.Sex sex:AvatarProfile.Sex.values()) {
                String name=sex.name().toLowerCase(Locale.ROOT)+"-bodybuilder";
                File file=new File(root,"assets/runtime/body/"+name+".dae");
                ColladaModel model=GeometryRuntimeHarness.load(file);AvatarGeometryPrototype.register(model);
                ColladaModelData data=new ColladaModelData(new FakeResourceUrl(new java.net.URL("file:avatar2/body/"+name+".dae")),model);
                model.setCantBeBaked(true); // Skip equipment submission after the actual native body matrix.
                AnimationLab.registerLocalModel(null);AnimationLab.registerPaperModel(model);
                ModelResourceWrapper wrapper=wrapper(data);
                PlayerBodyRenderable body=body(wrapper);
                if(!reproduce)check(wantsCopy(body),"Avatar paper doll must request an independent model copy");
                for(boolean head:new boolean[]{false,true})for(float yaw:new float[]{0,.6f,1.3f}) {
                    AvatarProfile profile=new AvatarProfile();profile.sex=sex;AvatarRuntimeState.apply(profile);sample(data);
                    float[] neutral=point(model);
                    body.renderPaperDoll(null,head,yaw,.04f,-.03f,.02f);
                    float[] baseline=project(captured,neutral);
                    for(int dirt=13;dirt<=20;dirt++) {
                        profile.heightMetres=dirt/10f;AvatarRuntimeState.apply(profile);sample(data);
                        float[] scaled=point(model);
                        body.renderPaperDoll(null,head,yaw,.04f,-.03f,.02f);
                        float[] actual=project(captured,scaled);
                        float drift=Math.max(Math.abs(actual[0]-baseline[0]),Math.abs(actual[1]-baseline[1]));
                        maxDrift=Math.max(maxDrift,drift);cases++;
                        if(!reproduce)near(drift,0,"Head screen position changes at "+dirt+" dirt, "+name+" head="+head);
                        Matrix complete=new Matrix(captured);
                        AnimationLab.beginFrame(model);
                        try {body.renderPaperDoll(null,head,yaw,.04f,-.03f,.02f);
                            if(!reproduce)near(captured._m31(),complete._m31(),"Portrait camera read unfinished height");
                        } finally {AnimationLab.endFrame();}
                    }
                    AvatarRuntimeState.apply(new AvatarProfile());sample(data);
                    body.renderPaperDoll(null,head,yaw,.04f,-.03f,.02f);
                    if(!reproduce)near(project(captured,point(model))[1],baseline[1],"Default height rollback framing");
                }
                // An ordinary native model must keep all of Wurm's original camera values.
                ColladaModel ordinary=GeometryRuntimeHarness.load(file);
                ColladaModelData ordinaryData=new ColladaModelData(new FakeResourceUrl(new java.net.URL("file:creature/humanoid/human/player/male.dae")),ordinary);ordinary.setCantBeBaked(true);
                PlayerBodyRenderable vanilla=body(wrapper(ordinaryData));
                check(!wantsCopy(vanilla),"Vanilla paper doll copy policy must remain native");
                vanilla.renderPaperDoll(null,false,0,.04f,-.03f,.02f);
                near(captured._m30(),.04f,"Vanilla X");near(captured._m31(),-.83f,"Vanilla height");near(captured._m32(),-2.28f,"Vanilla depth");
            }
            if(reproduce && maxDrift<.15f)throw new AssertionError("Old height framing defect was not reproduced: "+maxDrift);
            String result=(reproduce?"AVATAR2_080_PORTRAIT_DRIFT_REPRODUCED":"AVATAR2_PORTRAIT_HEIGHT_OK")+" cases="+cases+" max_projected_head_drift="+maxDrift;
            System.out.println(result);
            if(!reproduce)Files.write(Paths.get(root,"build/reports/avatar2/portrait-height-runtime.txt"),
                    Arrays.asList("Avatar "+Avatar2Mod.VERSION,result,"Native full/head renderPaperDoll, two sexes, 13..20 dirt, three rotations, nonzero offsets, unfinished-worker stability, default rollback, vanilla unchanged.",
                            "Healthbar source unchanged; installed visual check pending."),java.nio.charset.StandardCharsets.UTF_8);
        } finally {AnimationLab.stop();AnimationLab.registerPaperModel(null);AvatarRuntimeState.apply(new AvatarProfile());}
    }
    private static void sample(ColladaModelData data) {
        ColladaModel model=data.getModel();AnimationLab.beginFrame(model);
        try {ForwardKinematics fk=new ForwardKinematics();fk.calculateForwardKinematics(.25f,model.getColladaAnimation("idle"),false,false,false);
            fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            if(AnimationLab.beforeForwardKinematics(model))fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            AnimationLab.afterForwardKinematics(model);data.calculateSkins(TriangleMesh.LODLevel.NONE);
        } finally {AnimationLab.endFrame();}
    }
    private static float[] point(ColladaModel model) {com.wurmonline.client.renderer.model.collada.math.Matrix4f m=model.getJoint("head").getBindMatrix();return new float[]{m.m03,m.m13,m.m23};}
    private static float[] project(Matrix m,float[] p) {
        com.wurmonline.math.Vector3f point=new com.wurmonline.math.Vector3f(p[0],p[1],p[2]);
        m.transformInPlace(point);
        return new float[]{point.x/-point.z,point.y/-point.z};
    }
    private static ModelResourceWrapper wrapper(ColladaModelData data)throws Exception {
        Constructor<ModelResourceWrapper> c=ModelResourceWrapper.class.getDeclaredConstructor(com.wurmonline.client.resources.ResourceUrl.class,AbstractModelData.class);c.setAccessible(true);return c.newInstance(data.getUrl(),data);
    }
    private static PlayerBodyRenderable body(ModelResourceWrapper wrapper)throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)access.get(null);
        PlayerBodyRenderable body=(PlayerBodyRenderable)unsafe.allocateInstance(PlayerBodyRenderable.class);
        Field paper=PlayerBodyRenderable.class.getDeclaredField("paperDoll");paper.setAccessible(true);Object doll=unsafe.allocateInstance(paper.getType());
        Field model=paper.getType().getDeclaredField("paperDollModel");model.setAccessible(true);model.set(doll,wrapper);paper.set(body,doll);
        Field player=PlayerBodyRenderable.class.getDeclaredField("playerModel");player.setAccessible(true);player.set(body,wrapper);
        Field equipment=PlayerBodyRenderable.class.getDeclaredField("equipment");equipment.setAccessible(true);equipment.set(body,new AttachedCellRenderable[30]);
        return body;
    }
    private static boolean wantsCopy(PlayerBodyRenderable body)throws Exception {
        Field paper=PlayerBodyRenderable.class.getDeclaredField("paperDoll");paper.setAccessible(true);Object doll=paper.get(body);
        Method method=paper.getType().getDeclaredMethod("wantNewModelCopy");method.setAccessible(true);return (Boolean)method.invoke(doll);
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void near(float a,float b,String label){if(!Float.isFinite(a)||Math.abs(a-b)>.00003f)throw new AssertionError(label+": "+a+" != "+b);}
}
