package com.wurmonline.client.renderer.model.collada.animation;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression for the neutral-reference twist and one-sided support/arm offsets. */
public class LocomotionSymmetryTest {
    private Matrix4f delta(Joint j) {
        Matrix4f m=j.getBindMatrix().mult(j.getOriginalInvertBindMatrix());
        m.setTranslation(0,0,0); return m;
    }
    private float lateral(Joint j,Joint chest,Matrix4f chestInverse) {
        Matrix4f a=j.getBindMatrix(),b=chest.getBindMatrix();
        return Math.abs(chestInverse.m00*(a.m03-b.m03)
                +chestInverse.m01*(a.m13-b.m13)+chestInverse.m02*(a.m23-b.m23));
    }
    @Test public void rebuiltIdleAndWalkHaveNoPersistentOneSidedBiasOnAllNativeBodies() throws Exception {
        for(String body:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
            File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/"+body+".dae");
            ColladaModel model;
            try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
            model.setRootJointName("Root");
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
            for(String name:new String[]{"idle","walk"}){
                double yaw=0,lean=0,hips=0,shoulders=0,elbows=0;
                double minYaw=Double.POSITIVE_INFINITY,maxYaw=Double.NEGATIVE_INFINITY;
                float minHip=Float.POSITIVE_INFINITY,maxHip=Float.NEGATIVE_INFINITY;
                for(int phase=0;phase<120;phase++){
                    fk.calculateForwardKinematics(phase/120f,model.getColladaAnimation(name),false,false,false);
                    AvatarWalkMotion.sampled(phase/120f,model.getColladaAnimation(name),false,false);
                    fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                    Joint chest=model.getJoint("spine_03");Matrix4f c=delta(chest),p=delta(model.getJoint("pelvis"));
                    double frameYaw=Math.toDegrees(Math.atan2(c.m02,c.m22));
                    yaw+=frameYaw;minYaw=Math.min(minYaw,frameYaw);maxYaw=Math.max(maxYaw,frameYaw);
                    lean+=Math.toDegrees(Math.atan2(p.m01,p.m11));
                    float hd=model.getJoint("thigh_r").getBindMatrix().m13-model.getJoint("thigh_l").getBindMatrix().m13;
                    float sd=model.getJoint("upperarm_r").getBindMatrix().m13-model.getJoint("upperarm_l").getBindMatrix().m13;
                    float ed=lateral(model.getJoint("lowerarm_l"),chest,c.invert())-lateral(model.getJoint("lowerarm_r"),chest,c.invert());
                    hips+=hd;shoulders+=sd;elbows+=ed;
                    minHip=Math.min(minHip,hd);maxHip=Math.max(maxHip,hd);
                    if(name.equals("idle")){
                        assertEquals(body+" idle hips",0,hd,.0001f);
                        assertEquals(body+" idle shoulders",0,sd,.0001f);
                        assertEquals(body+" idle elbow placement",0,ed,.0001f);
                    }
                }
                yaw/=120;lean/=120;hips/=120;shoulders/=120;elbows/=120;
                assertEquals(body+" "+name+" torso heading",0,yaw,1.0);
                assertEquals(body+" "+name+" pelvis lean",0,lean,.5);
                assertEquals(body+" "+name+" hip height bias",0,hips,.002);
                assertEquals(body+" "+name+" shoulder height bias",0,shoulders,.003);
                assertEquals(body+" "+name+" arm abduction bias",0,elbows,.010);
                if(name.equals("walk")){
                    assertTrue(body+" walk still turns the chest both ways",maxYaw-minYaw>10);
                    assertTrue(body+" walk still alternates hip height",minHip<-.003f&&maxHip>.003f);
                }
                System.out.printf(Locale.ROOT,"AVATAR2_SYMMETRY_OK %s %s yaw=%.3f lean=%.3f hip=%.3fmm shoulder=%.3fmm elbow=%.3fmm%n",
                        body,name,yaw,lean,hips*1000,shoulders*1000,elbows*1000);
            }
        }
    }
}
