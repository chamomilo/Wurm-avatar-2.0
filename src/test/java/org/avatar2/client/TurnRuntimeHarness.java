package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.lang.reflect.Field;
import java.util.Map;

/** Actual patched FK: turning steps, rigid matrices, neutral rollback and copy isolation. */
public final class TurnRuntimeHarness {
    @SuppressWarnings("unchecked") public static void run(String root) throws Exception {
        Field field=AvatarLocomotion.class.getDeclaredField("STATES");field.setAccessible(true);
        Map<ColladaModel,AvatarLocomotion.State> states=(Map<ColladaModel,AvatarLocomotion.State>)field.get(null);
        int poses=0;
        for(String sex:new String[]{"male","female"}) {
            File file=new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae");ColladaModel model;
            try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
            model.setRootJointName("Root");ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
            ColladaAnimation walk=model.getColladaAnimation("walk");
            fk.calculateForwardKinematics(.25f,walk,false,false,false);
            Matrix4f neutral=new Matrix4f(model.getJoint("spine_02").getAnimationMatrix());
            Matrix4f rootMatrix=new Matrix4f(model.getJoint("Root").getAnimationMatrix());
            AvatarLocomotion.State s=new AvatarLocomotion.State();s.stationary=true;states.put(model,s);
            for(float sign:new float[]{-1,1})for(int frame=0;frame<120;frame++) {
                long now=System.nanoTime();s.turnAt=now;s.lag=sign*.7f;
                fk.calculateForwardKinematics(frame/120f,walk,false,false,false);
                fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                for(String arm:new String[]{"upperarm_l","upperarm_r","lowerarm_l","lowerarm_r"}) {
                    Matrix4f standing=AvatarWalkMotion.idleLocal(model,arm);
                    Matrix4f delta=standing.invert().mult(model.getJoint(arm).getAnimationMatrix());
                    double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(delta.m00+delta.m11+delta.m22-1)*.5))));
                    if(angle>18)throw new AssertionError("Turn spread arm away from relaxed Idle: "+arm+" "+angle);
                }
                for(Joint j:model.getJoints().values())if(j!=null) {
                    Matrix4f m=j.getAnimationMatrix();
                    if(!Float.isFinite(m.determinant())||Math.abs(m.determinant()-1)>.001f)
                        throw new AssertionError("Nonrigid turn joint "+j.getName());
                }
                same(rootMatrix,model.getJoint("Root").getAnimationMatrix(),"Turn moved Root");poses++;
            }
            s.turnAt=System.nanoTime();s.lag=.7f;fk.calculateForwardKinematics(.25f,walk,false,false,false);
            Matrix4f turning=new Matrix4f(model.getJoint("spine_02").getAnimationMatrix());
            if(Math.abs(neutral.m00-turning.m00)+Math.abs(neutral.m02-turning.m02)<.001)
                throw new AssertionError("Turn did not animate torso");
            fk.calculateForwardKinematics(.25f,walk,false,false,false);
            same(turning,model.getJoint("spine_02").getAnimationMatrix(),"Turn accumulated across samples");
            ColladaModel copy=new ColladaModel(model);
            if(states.containsKey(copy))throw new AssertionError("Cached copy inherited live turning clock");
            s.turnAt=0;fk.calculateForwardKinematics(.25f,walk,false,false,false);
            same(neutral,model.getJoint("spine_02").getAnimationMatrix(),"Turn failed to restore native stride");
            if(Math.abs(AvatarLocomotion.transitionDelta(walk,model.getColladaAnimation("idle"),.02f)-.05f)>1e-7)
                throw new AssertionError("Local transition did not use 80 ms crossfade");
            states.remove(model);
            if(AvatarLocomotion.transitionDelta(walk,model.getColladaAnimation("idle"),.02f)!=.02f)
                throw new AssertionError("Native fallback transition changed");
        }
        System.out.println("AVATAR2_TURN_RUNTIME_OK native patched FK poses="+poses+" both directions, arms close to relaxed Idle, rigid matrices, Root, no accumulation, copy isolation and exact rollback");
    }
    private static void same(Matrix4f a,Matrix4f b,String message) throws Exception {
        for(int i=0;i<4;i++)for(int j=0;j<4;j++)if(Math.abs(a.get(i,j)-b.get(i,j))>1e-6)
            throw new AssertionError(message);
    }
}
