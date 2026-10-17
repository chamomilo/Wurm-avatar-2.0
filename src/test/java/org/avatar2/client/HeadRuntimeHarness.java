package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Vector;
import org.avatar2.core.*;
import org.avatar2.lab.AnimationLab;
import java.io.*;
import java.util.*;

/** Real imported meshes, worker lifecycle, eyes, CPU skinning, camera and exact rollback. */
public final class HeadRuntimeHarness {
    public static void run(String root) throws Exception {
        int cases=0, poses=0;
        try {
            for(String sex:new String[]{"male","female"}) {
                AvatarProfile neutral=new AvatarProfile();neutral.sex=AvatarProfile.Sex.valueOf(sex.toUpperCase(Locale.ROOT));
                AvatarRuntimeState.apply(neutral);AnimationLab.start();
                File file=new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae");
                ColladaModel template=GeometryRuntimeHarness.load(file);
                AvatarAnimationBridge.normalizeSkinWeights(template);
                try(InputStream in=new FileInputStream(new File(root,"assets/runtime/morph/"+sex+"-bodybuilder.avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(template,MorphBank.read(in));
                }
                AvatarWalkMotion.register(template);
                ColladaModel world=new ColladaModel(template);
                ColladaModelData data=new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),world);
                List<TriangleMesh> sources=AvatarGeometryPrototype.sources(world), baked=new ArrayList<>();
                for(TriangleMesh mesh:sources) {TriangleMesh b=data.bakeDefaultMesh(mesh);data.addBakedMeshToList(b,null);baked.add(b);}
                AnimationLab.registerLocalModel(world);sample(world,data,"idle",0);
                float crown=world.calculateBoundingBox().getH1(), floor=world.calculateBoundingBox().getH0();
                Matrix4f head=world.getJoint("head").getOriginalBindMatrix(), neck=world.getJoint("neck_01").getOriginalBindMatrix();
                Matrix4f camera=world.getJoint("NullCamera0").getOriginalBindMatrix();
                System.out.println("HEAD_LANDMARKS "+sex+" neck="+neck.m13+" head="+head.m13+" crown="+crown);
                for(int scenario=0;scenario<8;scenario++) {
                    GeometryProbe base=scenario==0?GeometryProbe.NEUTRAL:scenario<4?
                            new GeometryProbe(0,0,new String[]{"mpfb-athletic","mpfb-heavy","mpfb-older"}[scenario-1],1):
                            new GeometryProbe(0,0,"proportions",1,0,0,0,scenario%2,scenario<6?0:1,
                                    sex.equals("female")?(scenario%2):.5f,scenario%2);
                    for(float value:new float[]{0,.25f,.5f,.75f,1}) {
                        submit(Collections.singletonMap("geometry",command(base.withHeadSize(.5f))));sample(world,data,"idle",0);
                        float baseCrown=world.calculateBoundingBox().getH1();
                        List<float[]> originals=new ArrayList<>();for(TriangleMesh m:sources) originals.add(m.getOriginalVertexBuffer().clone());
                        submit(Collections.singletonMap("geometry",command(base.withHeadSize(value))));sample(world,data,"idle",0);
                        float scale=1+(value-.5f)*.16f;
                        for(int mi=0;mi<sources.size();mi++) {
                            TriangleMesh m=sources.get(mi);float[] before=originals.get(mi), after=m.getOriginalVertexBuffer(), normals=m.getOriginalNormalBuffer();
                            require(Arrays.equals(after,baked.get(mi).getOriginalVertexBuffer()),"Baked head mismatch");
                            if(value==.5f) require(Arrays.equals(before,after),"Head center changes released surface");
                            for(int i=0;i<after.length;i+=3) {
                                if(before[i+1]<=neck.m13) for(int a=0;a<3;a++) near(before[i+a],after[i+a],0,"Head changes torso/grips/feet");
                                if(m.getName().startsWith("Avatar2EyesMesh")) {
                                    near(head.m03+(before[i]-head.m03)*scale,after[i],.000002f,"Eye horizontal agreement");
                                    near(baseCrown+(before[i+1]-baseCrown)*scale,after[i+1],.000002f,"Eye vertical agreement");
                                    near(head.m23+(before[i+2]-head.m23)*scale,after[i+2],.000002f,"Eye depth agreement");
                                }
                                double length=0;for(int a=0;a<3;a++){require(Float.isFinite(after[i+a])&&Float.isFinite(normals[i+a]),"Nonfinite head data");length+=normals[i+a]*normals[i+a];}
                                require(Math.abs(length-1)<.001,"Head normal not unit");
                            }
                            verifyTriangles(m,before,after);
                        }
                        near(baseCrown,world.calculateBoundingBox().getH1(),.000003f,"Head changes standing height");
                        float[] actual=data.getOriginalNullOffset("NullCamera0",new Vector()).vector;
                        near(head.m03+(camera.m03-head.m03)*scale,actual[0],.00001f,"Camera horizontal fit");
                        near(baseCrown+(camera.m13-baseCrown)*scale,actual[1],.00001f,"Camera vertical fit");
                        near(head.m23+(camera.m23-head.m23)*scale,actual[2],.00001f,"Camera depth fit");
                        for(String clip:new String[]{"idle","walk","run"}) for(int phase=0;phase<4;phase++) {
                            sample(world,data,clip,phase/4f);
                            for(TriangleMesh m:baked)GeometryRuntimeHarness.verifyCpuSkin(m);
                            if(scenario==0 && phase==0 && clip.equals("idle") && (value==0||value==.5f||value==1))
                                GeometryRuntimeHarness.writePose(new File(root,"build/geometry-poses/"+sex+"-bodybuilder-head-"+value+".obj"),baked.toArray(new TriangleMesh[0]));
                            poses++;
                        }
                        cases++;
                    }
                }
                submit(Collections.singletonMap("reset",true));sample(world,data,"idle",0);
                for(int i=0;i<sources.size();i++)require(Arrays.equals(sources.get(i).getOriginalVertexBuffer(),AvatarGeometryPrototype.sources(template).get(i).getOriginalVertexBuffer()),"Head rollback/template isolation");
                near(crown-floor,world.calculateBoundingBox().getH1()-world.calculateBoundingBox().getH0(),.000003f,"Reset extent");
                AnimationLab.stop();AnimationLab.registerLocalModel(null);
            }
            System.out.println("AVATAR2_HEAD_RUNTIME_OK cases="+cases+" CPU poses="+poses+", both sexes, authored and Build combinations, smooth neck triangles, uniform eyes, camera, source/baked, crown height, exact center/reset and template isolation");
            java.nio.file.Files.createDirectories(new File(root,"build/reports/avatar2").toPath());
            java.nio.file.Files.write(new File(root,"build/reports/avatar2/head-size-runtime.txt").toPath(),
                    Arrays.asList("Avatar "+Avatar2Mod.VERSION,"Head combinations="+cases,"Native CPU source/baked poses="+poses,
                            "PASS: uniform eyes, crown height, neck triangle orientation/area, finite unit normals, camera original offset, exact neutral/reset and template isolation",
                            "Installed visual check and final hair/helmet adapters remain pending."),java.nio.charset.StandardCharsets.UTF_8);
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);AvatarRuntimeState.apply(new AvatarProfile());}
    }
    private static Map<String,Object> command(GeometryProbe g) {
        Map<String,Object> m=new LinkedHashMap<>();m.put("shape",g.shape);m.put("shapeMix",g.shapeMix);
        m.put("buildMuscle",g.buildMuscle);m.put("buildFat",g.buildFat);m.put("buildBreast",g.buildBreast);m.put("buildShoulders",g.buildShoulders);m.put("headSize",g.headSize);return m;
    }
    private static void submit(Map<String,?> command) throws Exception {
        java.lang.reflect.Method method=AnimationLab.class.getDeclaredMethod("submit",Map.class);method.setAccessible(true);method.invoke(null,command);
    }
    private static void sample(ColladaModel model,ColladaModelData data,String clip,float phase) {
        ForwardKinematics fk=new ForwardKinematics();AnimationLab.beginFrame(model);
        try {
            fk.calculateForwardKinematics(phase,model.getColladaAnimation(clip),false,false,false);
            fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            if(AnimationLab.beforeForwardKinematics(model))fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            AnimationLab.afterForwardKinematics(model);data.calculateSkins(TriangleMesh.LODLevel.NONE);AnimationLab.afterSkinning(model);
        } finally {AnimationLab.endFrame();}
    }
    private static void verifyTriangles(TriangleMesh mesh,float[] before,float[] after) {
        java.nio.IntBuffer ids=mesh.getIndexBuffer();
        for(int i=0;i<ids.capacity();i+=3) {
            double[] a=cross(before,ids.get(i),ids.get(i+1),ids.get(i+2)), b=cross(after,ids.get(i),ids.get(i+1),ids.get(i+2));
            double aa=dot(a,a),bb=dot(b,b);if(aa<1e-18)continue;
            require(bb>aa*.25 && dot(a,b)/Math.sqrt(aa*bb)>.8,"Head triangle collapses or reverses");
        }
    }
    private static double[] cross(float[] v,int a,int b,int c) {
        double x=v[b*3]-v[a*3], y=v[b*3+1]-v[a*3+1], z=v[b*3+2]-v[a*3+2];
        double u=v[c*3]-v[a*3], w=v[c*3+1]-v[a*3+1], t=v[c*3+2]-v[a*3+2];
        return new double[]{y*t-z*w,z*u-x*t,x*w-y*u};
    }
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static void near(float a,float b,float tolerance,String message){require(Math.abs(a-b)<=tolerance,message+": "+a+" / "+b);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
