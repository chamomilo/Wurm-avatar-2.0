package com.wurmonline.client.renderer.model.collada.animation;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fresh runner comparisons retain imported sway/bob and native joint rotations. */
public class RunCandidatesTest {
    @Test public void bothSupplierRunsAreMobileRigidAndInPlaceOnEveryBody() throws Exception {
        for(String body:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
            File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/"+body+".dae");
            ColladaModel model;
            try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
            model.setRootJointName("Root");
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AvatarWalkMotion.register(model);ForwardKinematics fk=new ForwardKinematics();
            for(String name:new String[]{"gait.jog.clean","gait.sprint.clean","run","run.p1","gait.sprint"}){
                ColladaAnimation clip=model.getColladaAnimation(name);assertNotNull(name,clip);
                assertEquals(name,name.equals("gait.jog.clean")?.9f:19/30f,clip.getLength(),.0001f);
                float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                for(int i=0;i<120;i++){
                    float phase=i/120f;fk.calculateForwardKinematics(phase,clip,false,false,false);
                    Map<Joint,Matrix4f> before=new IdentityHashMap<Joint,Matrix4f>();
                    for(Joint j:model.getJoints().values())before.put(j,new Matrix4f(j.getAnimationMatrix()));
                    AvatarWalkMotion.sampled(phase,clip,false,false);
                    for(Joint j:model.getJoints().values()){
                        Matrix4f a=before.get(j),b=j.getAnimationMatrix();
                        for(int row=0;row<4;row++)for(int col=0;col<4;col++){
                            assertTrue(body+" "+name+" "+j.getName(),Float.isFinite(b.get(row,col)));
                            if(!("pelvis".equals(j.getName())&&row<3&&col==3))
                                assertEquals(a.get(row,col),b.get(row,col),0);
                        }
                    }
                    Matrix4f p=model.getJoint("pelvis").getAnimationMatrix();
                    float[] point={p.m03,p.m13,p.m23};
                    for(int axis=0;axis<3;axis++){low[axis]=Math.min(low[axis],point[axis]);high[axis]=Math.max(high[axis],point[axis]);}
                    fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                    Matrix4f root=model.getJoint("Root").getBindMatrix(),rest=model.getJoint("Root").getOriginalBindMatrix();
                    assertEquals(rest.m03,root.m03,.000001f);assertEquals(rest.m13,root.m13,.000001f);assertEquals(rest.m23,root.m23,.000001f);
                    Matrix4f cache=clip.getAnimationMatrix(model.getJoint("pelvis").getBoneIndex());
                    assertEquals(p.m03,cache.m03,0);assertEquals(p.m13,cache.m13,0);assertEquals(p.m23,cache.m23,0);
                }
                assertTrue(body+" "+name+" lost source sway",high[0]-low[0]>.005f);
                assertTrue(body+" "+name+" lost source bob",high[1]-low[1]>.01f);
                // The unmodified supplier Jog has 244.8 mm vertical travel,
                // about 256 mm after native male leg-height scaling.
                for(int axis=0;axis<3;axis++)assertTrue(body+" "+name+" axis "+axis+" travel="+(high[axis]-low[axis]),high[axis]-low[axis]<(axis==1?.30f:.10f));
                System.out.printf(Locale.ROOT,"AVATAR2_RUN_CANDIDATE_OK %s %s sway=%.4fm bob=%.4fm%n",body,name,high[0]-low[0],high[1]-low[1]);
            }
        }
    }
}
