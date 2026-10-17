package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import org.avatar2.lab.IdlePoseReport;

public final class WalkRuntimeHarness {
    public static void run(String root) throws Exception {
        if(ForwardKinematics.class.getClassLoader()!=WalkRuntimeHarness.class.getClassLoader()
                || AvatarWalkMotion.class.getClassLoader()!=WalkRuntimeHarness.class.getClassLoader())
            throw new AssertionError("Sampler/helper did not share Wurm loader");
        for(String body:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
            File file=new File(root,"assets/runtime/body/"+body+".dae");ColladaModel model;
            try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
            model.setRootJointName("Root");
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
            IdlePoseReport.verifyAndExport(root,body,model,fk);
            float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
            for(int phase=0;phase<24;phase++){
                // There is deliberately NO direct call to AvatarWalkMotion.sampled.
                fk.calculateForwardKinematics(phase/24f,model.getColladaAnimation("walk"),false,false,false);
                fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                float x=model.getJoint("pelvis").getBindMatrix().m03;lo=Math.min(lo,x);hi=Math.max(hi,x);
            }
            if(hi-lo<.025f)throw new AssertionError(body+" patched walk still has fixed pelvis");
            // Body toggles resolve cached resources by copying the imported model.
            // A second copy also covers switching back repeatedly.
            ColladaModel copy = new ColladaModel(model);
            for(int generation=1;generation<=2;generation++){
              for(String name:new String[]{"walk","walk.p1","gait.walk.relaxed","run","run.p1","gait.sprint","idle","idle.p1"}){
                lo=Float.POSITIVE_INFINITY;hi=Float.NEGATIVE_INFINITY;
                for(int phase=0;phase<120;phase++){
                    // Normal full-body and lower-body samplers share this hook;
                    // the latter is the movement path used in split blending.
                    fk.calculateForwardKinematics(phase/120f,copy.getColladaAnimation(name),false,phase%2==1,false);
                    fk.updateBoneTransformations(copy,copy.getJoint("Root"),null,false,0,0);
                    Matrix4f bind=copy.getJoint("pelvis").getBindMatrix();
                    float x=AvatarWalkMotion.isIdle(name)?bind.m13:bind.m03;lo=Math.min(lo,x);hi=Math.max(hi,x);
                }
                if(hi-lo<(AvatarWalkMotion.isIdle(name)?.0001f:AvatarWalkMotion.isWalk(name)?.025f:.005f))throw new AssertionError(body+" "+name+" copied body "+generation+" has fixed pelvis; range="+(hi-lo));
              }
                copy=new ColladaModel(copy);
            }
            // An unregistered native fixture is not an Avatar resource. Its
            // copies must retain Wurm's original translation clamp.
            ColladaModel vanilla;
            try(InputStream in=new FileInputStream(file)){vanilla=ColladaImporter.loadModel(file.toURI().toString(),in);}
            vanilla.setRootJointName("Root");
            ColladaPropertiesXml.load(vanilla,new FakeResourceUrl(file.toURI().toURL()),false);
            vanilla=new ColladaModel(vanilla);
            fk.calculateForwardKinematics(.25f,vanilla.getColladaAnimation("walk"),false,false,false);
            Matrix4f unchanged=new Matrix4f(vanilla.getJoint("pelvis").getAnimationMatrix());
            fk.calculateForwardKinematics(.75f,vanilla.getColladaAnimation("walk"),false,false,false);
            Matrix4f later=vanilla.getJoint("pelvis").getAnimationMatrix();
            if(unchanged.m03!=later.m03||unchanged.m13!=later.m13||unchanged.m23!=later.m23)
                throw new AssertionError("Non-Avatar copy gained a pelvis correction");
            fk.calculateForwardKinematics(.25f,model.getColladaAnimation("gait.jog"),false,false,false);
            Matrix4f a=new Matrix4f(model.getJoint("pelvis").getAnimationMatrix());
            fk.calculateForwardKinematics(.75f,model.getColladaAnimation("gait.jog"),false,false,false);
            Matrix4f b=model.getJoint("pelvis").getAnimationMatrix();
            if(a.m03!=b.m03||a.m13!=b.m13||a.m23!=b.m23)throw new AssertionError("Previous jog changed");
        }
    }
}
