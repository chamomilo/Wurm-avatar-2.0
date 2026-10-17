package com.wurmonline.client.renderer.model.collada.animation;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarWalkMotionTest {
    private ColladaModel load(String body) throws Exception {
        File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/"+body+".dae");
        ColladaModel model;
        try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
        model.setRootJointName("Root");
        ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
        return model;
    }
    private void fk(ForwardKinematics fk,ColladaModel model){
        fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,31,0);
    }
    private float pointY(Joint joint,float x,float z){
        Matrix4f skin=joint.getBindMatrix().mult(joint.getOriginalInvertBindMatrix());
        return skin.m10*x+skin.m12*z+skin.m13;
    }
    private float sole(ColladaModel model,String side){
        Joint f=model.getJoint("foot_"+side),b=model.getJoint("ball_"+side);
        Matrix4f fr=f.getOriginalBindMatrix(),br=b.getOriginalBindMatrix();
        return Math.min(pointY(f,fr.m03,fr.m23-.025f),pointY(b,br.m03,br.m23));
    }
    private float[] position(Matrix4f m){return new float[]{m.m03,m.m13,m.m23};}

    @Test public void allBodiesHaveGroundedMobileWalkAndIdleWithoutChangingRootOrRotations() throws Exception {
        for(String body:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
            ColladaModel model=load(body);AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
            for(String name:new String[]{"walk","walk.p1","gait.walk.relaxed","idle","idle.p1"}){
                ColladaAnimation clip=model.getColladaAnimation(name);
                float loX=Float.POSITIVE_INFINITY,hiX=Float.NEGATIVE_INFINITY;
                float loY=Float.POSITIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY;
                float[] root=null;
                float[] plantedY=null;
                for(int frame=0;frame<120;frame++){
                    float phase=frame/120f;
                    fk.calculateForwardKinematics(phase,clip,false,false,false);
                    Map<Joint,Matrix4f> before=new IdentityHashMap<Joint,Matrix4f>();
                    for(Joint j:model.getJoints().values())before.put(j,new Matrix4f(j.getAnimationMatrix()));
                    AvatarWalkMotion.sampled(phase,clip,false,false);
                    for(Joint j:model.getJoints().values()){
                        Matrix4f a=before.get(j),b=j.getAnimationMatrix();
                        for(int r=0;r<3;r++)for(int c=0;c<3;c++)assertEquals(body+" "+j.getName(),a.get(r,c),b.get(r,c),0);
                        if(!"pelvis".equals(j.getName()))assertArrayEquals(position(a),position(b),0);
                    }
                    fk(fk,model);
                    if(root==null)root=position(model.getJoint("Root").getBindMatrix());
                    assertArrayEquals(root,position(model.getJoint("Root").getBindMatrix()),.000001f);
                    float contact=Math.min(sole(model,"l"),sole(model,"r"));
                    assertTrue(body+" "+name+" phase="+phase+" penetrates="+contact,contact>=-.0001f);
                    assertTrue(body+" "+name+" phase="+phase+" floats="+contact,contact<.003f);
                    Matrix4f pelvis=model.getJoint("pelvis").getBindMatrix();
                    if(AvatarWalkMotion.isIdle(name)){
                        float[] feet={model.getJoint("foot_l").getBindMatrix().m13,model.getJoint("foot_r").getBindMatrix().m13};
                        if(plantedY==null)plantedY=feet.clone();
                        assertArrayEquals(body+" "+name+" vertical foot bob",plantedY,feet,.00003f);
                    }
                    loX=Math.min(loX,pelvis.m03);hiX=Math.max(hiX,pelvis.m03);
                    loY=Math.min(loY,pelvis.m13);hiY=Math.max(hiY,pelvis.m13);
                    assertArrayEquals(position(model.getJoint("pelvis").getAnimationMatrix()),
                            position(clip.getAnimationMatrix(model.getJoint("pelvis").getBoneIndex())),.000001f);
                }
                if(AvatarWalkMotion.isIdle(name)){
                    assertTrue(body+" idle lateral pelvis range "+(hiX-loX),hiX-loX<.005f);
                    assertTrue(body+" idle vertical pelvis range "+(hiY-loY),hiY-loY>.0001f&&hiY-loY<.002f);
                }else{
                    assertTrue(body+" sway range "+(hiX-loX),hiX-loX>.025f&&hiX-loX<.10f);
                    assertTrue(body+" vertical range "+(hiY-loY),hiY-loY>.02f&&hiY-loY<.12f);
                }
                System.out.printf(Locale.ROOT,"AVATAR2_WALK_OK %s %s sway=%.4fm rise=%.4fm contact<3mm%n",body,name,hiX-loX,hiY-loY);
            }
        }
    }

    @Test public void nonAvatarPreviousJogAndUpperBodyWalkAreUnchangedAndRepeatedSamplesDoNotAccumulate() throws Exception {
        ColladaModel model=load("male-athletic");ForwardKinematics fk=new ForwardKinematics();
        ColladaAnimation walk=model.getColladaAnimation("walk");
        fk.calculateForwardKinematics(.25f,walk,false,false,false);
        float[] original=position(model.getJoint("pelvis").getAnimationMatrix());
        AvatarWalkMotion.sampled(.25f,walk,false,false);
        assertArrayEquals(original,position(model.getJoint("pelvis").getAnimationMatrix()),0);
        AvatarWalkMotion.register(model);
        for(String name:new String[]{"gait.jog"}){
            ColladaAnimation clip=model.getColladaAnimation(name);
            fk.calculateForwardKinematics(.25f,clip,false,false,false);
            float[] before=position(model.getJoint("pelvis").getAnimationMatrix());
            AvatarWalkMotion.sampled(.25f,clip,false,false);
            assertArrayEquals(name,before,position(model.getJoint("pelvis").getAnimationMatrix()),0);
        }
        fk.calculateForwardKinematics(.25f,walk,false,false,false);
        float[] before=position(model.getJoint("pelvis").getAnimationMatrix());
        assertFalse(model.getJoint("pelvis").isBlendOrChildOfBlend());
        AvatarWalkMotion.sampled(.25f,walk,true,false);
        assertArrayEquals(before,position(model.getJoint("pelvis").getAnimationMatrix()),0);
        float[] first=null;
        for(int i=0;i<20;i++){
            fk.calculateForwardKinematics(.25f,walk,false,false,false);
            AvatarWalkMotion.sampled(.25f,walk,false,false);fk(fk,model);
            if(first==null)first=position(model.getJoint("pelvis").getBindMatrix());
            assertArrayEquals(first,position(model.getJoint("pelvis").getBindMatrix()),.000001f);
        }
    }

    @Test public void correctedWalkCacheBlendsSmoothlyWithIdle() throws Exception {
        ColladaModel model=load("female-athletic");AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
        ColladaAnimation walk=model.getColladaAnimation("walk"),idle=model.getColladaAnimation("idle");
        fk.calculateForwardKinematics(.25f,walk,false,false,false);AvatarWalkMotion.sampled(.25f,walk,false,false);
        short index=model.getJoint("pelvis").getBoneIndex();
        float[] a=position(walk.getAnimationMatrix(index));
        fk.calculateForwardKinematics(.25f,idle,false,false,false);
        AvatarWalkMotion.sampled(.25f,idle,false,false);
        float[] b=position(idle.getAnimationMatrix(index));
        for(int step=0;step<=20;step++){
            float t=step/20f;
            fk.blendAnimation(walk,idle,model,t,false,false);
            float[] p=position(model.getJoint("pelvis").getAnimationMatrix());
            for(int axis=0;axis<3;axis++)assertEquals(a[axis]+(b[axis]-a[axis])*t,p[axis],.00001f);
        }
    }
}
