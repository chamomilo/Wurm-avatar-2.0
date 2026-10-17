package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Vector;
import org.avatar2.client.*;
import org.avatar2.core.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Real patched copy/baker/socket methods and native CPU sampler; no renderer mocks. */
public final class HeightRuntimeHarness {
    private static final Map<ColladaModelData,List<TriangleMesh>> BAKED = new IdentityHashMap<>();
    public static void run(String root) throws Exception {
        List<String> report = new ArrayList<>();
        report.add("version,body,baseline_height_m,height_min_m,height_max_m,scale_min,scale_max,neutral_cpu_poses,age_cpu_poses,gpu_dq_poses,validation");
        try {
            AnimationLab.start();
            for (String name : new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}) {
                File file = new File(root,"assets/runtime/body/"+name+".dae");
                ColladaModel template = GeometryRuntimeHarness.load(file);
                AvatarAnimationBridge.normalizeSkinWeights(template);
                if (name.endsWith("bodybuilder")) try (InputStream stream = new FileInputStream(new File(root,"assets/runtime/morph/"+name+".avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(template,MorphBank.read(stream));
                } else AvatarGeometryPrototype.register(template);
                AvatarWalkMotion.register(template);
                ColladaModel world = new ColladaModel(template), reference = new ColladaModel(template);
                ColladaModelData data = data(world,file), neutral = data(reference,file);
                AnimationLab.registerLocalModel(world);
                float[] original = body(world).getOriginalVertexBuffer().clone();
                float baselineHeight = world.calculateBoundingBox().getH1();
                HeightLimits limits = AvatarHeightPrototype.limits(world);
                float minimumScale = limits.fromDirts(13), maximumScale = limits.fromDirts(20);
                float camera = world.getOriginalNullAttachmentOffset("NullCamera0",new Vector()).vector[1];
                ForwardKinematics fk = new ForwardKinematics(), referenceFk = new ForwardKinematics();
                float largestSoleBob=0;
                for (int dirt = 13; dirt <= 20; dirt++) {
                    float scale = AvatarHeightPrototype.limits(world).fromDirts(dirt);
                    AnimationLab.submit(LabJson.map("heightDirts",dirt));
                    near(baselineHeight*scale,dirt/10f,"Physical standing height");
                    float[] soleStart=null;
                    for (String clip : new String[]{"idle","walk","run"}) for (int phase = 0; phase < 12; phase++) {
                        sample(world,data,fk,reference,neutral,referenceFk,clip,phase/12f);
                        compareMeshes(data,neutral,scale);
                        if(clip.equals("idle")){
                            TriangleMesh skinned=bakedBody(data);
                            if(soleStart==null){soleStart=new float[skinned.getVertexCount()];
                                for(int vertex=0;vertex<soleStart.length;vertex++)soleStart[vertex]=skinned.getVertexBuffer().get(vertex*3+1);}
                            float[] bind=skinned.getOriginalVertexBuffer();int soles=0;
                            for(int vertex=0;vertex<soleStart.length;vertex++)if(bind[vertex*3+1]<.003f && Math.abs(bind[vertex*3])>.08f){
                                float bob=Math.abs(skinned.getVertexBuffer().get(vertex*3+1)-soleStart[vertex]);
                                largestSoleBob=Math.max(largestSoleBob,bob);soles++;
                                // The sole band includes skin above the contact plane.
                                // Small calf-weight deformation is not rigid foot bob.
                                if(bob>.0005f)throw new AssertionError(name+" skinned Idle sole bobs: "+bob+" at "+dirt+" dirt, vertex "+vertex);
                            }
                            require(soles>10,"Missing skinned sole landmarks");
                        }
                        near(data.getBounds().getH1(),baselineHeight*scale,"Render bounds");
                        near(data.getPickingBounds().getH1(),baselineHeight*scale,"Picking bounds");
                        near(world.getOriginalNullAttachmentOffset("NullCamera0",new Vector()).vector[1],camera*scale,"Original camera socket");
                        near(data.getOriginalNullOffset("NullCamera0",new Vector()).vector[1],camera*scale,"WorldRender original camera API");
                        near(data.getNullOffset("NullCamera0",new Vector()).vector[1],world.getJoint("NullCamera0").getBindMatrix().m13,"WorldRender animated camera API");
                        verifySocket(world,reference,"hand_r");
                        verifySocket(world,reference,"NullCamera0");
                    }
                    ColladaModel copy = new ColladaModel(world);
                    near(copy.getJoint("hand_r").getBindMatrix().m13,reference.getJoint("hand_r").getBindMatrix().m13,"Copy retained temporary height");
                    require(Arrays.equals(original,body(world).getOriginalVertexBuffer()),"Height rewrote immutable bind vertices");
                    require(Arrays.equals(original,body(template).getOriginalVertexBuffer()),"Height contaminated the template");
                }
                int agePoses = 0;
                if (name.endsWith("bodybuilder")) {
                    AvatarGeometryPrototype.request(reference,new GeometryProbe(0,0,"mpfb-older",1));
                    for (float scale : limits.scaleRange()) {
                        AnimationLab.submit(LabJson.map("heightScale",scale,"geometry",LabJson.map("shape","mpfb-older","shapeMix",1)));
                        for (String clip : new String[]{"idle","walk","run"}) for (int phase = 0; phase < 4; phase++) {
                            sample(world,data,fk,reference,neutral,referenceFk,clip,phase/4f);
                            compareMeshes(data,neutral,scale); agePoses++;
                        }
                    }
                    AvatarGeometryPrototype.request(reference,GeometryProbe.NEUTRAL);
                }
                AnimationLab.submit(LabJson.map("reset",true));
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f); compareMeshes(data,neutral,1);
                near(data.getBounds().getH1(),baselineHeight,"Reset bounds");
                AnimationLab.submit(LabJson.map("heightDirts",13));
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f);
                // GPU receives scaled private positions; CPU/bind arrays stay intact.
                TriangleMesh baked = BAKED.get(data).get(0);
                java.lang.reflect.Field gpu = TriangleMesh.class.getDeclaredField("hasGPUSkinning");
                gpu.setAccessible(true); gpu.setBoolean(baked,true);
                data.calculateSkins(TriangleMesh.LODLevel.NONE);
                near(baked.getVertexBuffer().get(0),baked.getOriginalVertexBuffer()[0]*minimumScale,"GPU height input vertices");
                gpu.setBoolean(baked,false);
                java.lang.reflect.Field contact = AnimationLab.class.getDeclaredField("lastContact");
                contact.setAccessible(true); contact.setLong(null,System.nanoTime()-31_000_000_000L);
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f); compareMeshes(data,neutral,1);
                near(data.getBounds().getH1(),baselineHeight,"Lease reset bounds");
                AnimationLab.submit(LabJson.map("heightDirts",20));
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f);
                AnimationLab.stop();
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f); compareMeshes(data,neutral,1);
                near(data.getBounds().getH1(),baselineHeight,"Lab off bounds");
                AnimationLab.start();
                AnimationLab.submit(LabJson.map("heightDirts",13));
                sample(world,data,fk,reference,neutral,referenceFk,"walk",.25f);
                AnimationLab.registerLocalModel(null);
                AnimationLab.beginFrame(world); data.calculateSkins(TriangleMesh.LODLevel.NONE); AnimationLab.endFrame();
                near(world.getOriginalNullAttachmentOffset("NullCamera0",new Vector()).vector[1],camera,"Model change camera reset");
                near(data.getBounds().getH1(),baselineHeight,"Model change bounds reset");
                AnimationLab.registerLocalModel(world);
                int gpuPoses = verifyGpu(world,data,fk,reference,neutral,referenceFk,name.endsWith("bodybuilder"));
                report.add(Avatar2Mod.VERSION+","+name+","+baselineHeight+",1.3,2.0,"+minimumScale+","+maximumScale+",288,"+agePoses+","+gpuPoses+",native_cpu_socket_bounds_copy_rollback_native_dq_shader_math");
                System.out.println("AVATAR2_HEIGHT_OK "+name+" 8 heights (13..20 dirt), 288 CPU poses, age="+agePoses+", GPU DQ="+gpuPoses+", rigid sockets, camera, bounds, copies and rollback");
                java.nio.file.Files.write(new File(root,"build/reports/avatar2/"+name+"-idle-sole.csv").toPath(),
                        ("body,max_cpu_skinned_sole_y_change_m,heights,idle_phases_per_height\n"+name+","+largestSoleBob+",8,12\n").getBytes("UTF-8"));
                System.out.println("AVATAR2_IDLE_SOLE_OK "+name+" max_skinned_y_change_m="+largestSoleBob);
            }
        } finally { AnimationLab.stop(); AnimationLab.registerLocalModel(null); BAKED.clear(); }
        File output = new File(root,"build/reports/avatar2/height-runtime.csv");
        java.nio.file.Files.write(output.toPath(),report,java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Exercise Wurm's actual updateBoneBuffers and evaluate its pinned GLSL DLB formula. */
    private static int verifyGpu(ColladaModel world,ColladaModelData data,ForwardKinematics fk,
                                  ColladaModel reference,ColladaModelData neutral,ForwardKinematics rfk,boolean authored) throws Exception {
        java.lang.reflect.Field gpu = TriangleMesh.class.getDeclaredField("hasGPUSkinning"); gpu.setAccessible(true);
        java.lang.reflect.Field material = TriangleMesh.class.getDeclaredField("materialInstance"); material.setAccessible(true);
        java.lang.reflect.Field dqField = TriangleMesh.class.getDeclaredField("boneDQ"); dqField.setAccessible(true);
        java.lang.reflect.Constructor<?> empty = com.wurmonline.client.renderer.MaterialInstance.class.getDeclaredConstructor(); empty.setAccessible(true);
        for (ColladaModelData d : new ColladaModelData[]{data,neutral}) for (TriangleMesh mesh : allMeshes(d)) {
            gpu.setBoolean(mesh,true);
            // No GL context needed: empty native material allocates the actual
            // DQ buffer and skips binding it to an absent shader program.
            if (BAKED.get(d).contains(mesh)) material.set(mesh,empty.newInstance());
        }
        int count = 0;
        float maxGpuSoleBob=0;
        List<String> gpuShapes=new ArrayList<>(Arrays.asList(authored?new String[]{"none","mpfb-older","build-low","build-full","build-both","build-middle"}:new String[]{"none"}));
        if(AvatarGeometryPrototype.breastSupported(world))for(int i=0;i<10;i++)gpuShapes.add("breast-"+i);
        if(AvatarGeometryPrototype.shouldersSupported(world))for(int i=0;i<4;i++)gpuShapes.add("shoulders-"+i);
        if(AvatarGeometryPrototype.ageSupported(world,new GeometryProbe(0,0,"proportions",1)))for(int i=0;i<8;i++)gpuShapes.add("age-"+i);
        for(int i=0;i<2;i++)gpuShapes.add("head-"+i);
        for (String shape : gpuShapes) {
            GeometryProbe probe = shape.equals("none")||shape.startsWith("breast-")||shape.startsWith("shoulders-")||shape.startsWith("head-")||shape.startsWith("age-") ? GeometryProbe.NEUTRAL : shape.startsWith("build-")
                    ? new GeometryProbe(0,0,"proportions",1,0,0,0,shape.equals("build-full")?1:shape.equals("build-middle")?.5f:0,
                            shape.equals("build-low")?0:shape.equals("build-middle")?.5f:1)
                    : new GeometryProbe(0,0,shape,1);
            if(shape.startsWith("breast-")) {
                int variant=Integer.parseInt(shape.substring(7));
                float[][] values={{1,0,0},{0,0,0},{1,1,0},{0,1,0},{1,0,1},{0,0,1},{1,1,1},{0,1,1},{.5f,.5f,.25f},{.5f,.5f,.75f}};
                probe=new GeometryProbe(0,0,"proportions",1,0,0,0,values[variant][0],values[variant][1],values[variant][2]);
            }
            if(shape.startsWith("shoulders-")) {
                int variant=Integer.parseInt(shape.substring(10));
                probe=new GeometryProbe(0,0,"proportions",1,0,0,0,variant<2?1:.5f,variant<2?0:.75f,
                        AvatarGeometryPrototype.breastSupported(world)&&variant>=2?.75f:.5f,variant%2==0?0:1);
            }
            if(shape.startsWith("head-"))probe=probe.withHeadSize(shape.endsWith("0")?0:1);
            if(shape.startsWith("age-")) {
                int variant=Integer.parseInt(shape.substring(4));
                probe=new GeometryProbe(0,0,"proportions",1,0,0,0,variant%2,variant%4<2?0:1,
                        AvatarGeometryPrototype.breastSupported(world)?(variant%2):.5f,variant%2,variant%2,variant<4?"old":"middle");
            }
            AvatarGeometryPrototype.request(reference,probe);
            for (int dirt = 13; dirt <= 20; dirt++) {
                if (!shape.equals("none") && dirt != 13 && dirt != 20) continue;
                float scale = AvatarHeightPrototype.limits(world).fromDirts(dirt);
                AnimationLab.submit(LabJson.map("heightDirts",dirt,"geometry",LabCommand.geometryJson(probe)));
                float[] soleY=null;
                for (String clip : new String[]{"idle","walk","run"}) for (int phase = 0; phase < 4; phase++) {
                    sample(world,data,fk,reference,neutral,rfk,clip,phase/4f);
                    near(data.getBounds().getH1(),reference.calculateBoundingBox().getH1()*scale,"GPU render bounds scaled once");
                    com.wurmonline.client.renderer.model.ModelDataBounds referencePicking = reference.calculatePickingBox();
                    if (referencePicking == null) referencePicking = reference.calculateBoundingBox();
                    near(data.getPickingBounds().getH1(),referencePicking.getH1()*scale,"GPU picking bounds scaled once");
                    for (int index = 0; index < BAKED.get(data).size(); index++) {
                        TriangleMesh a = BAKED.get(data).get(index), b = BAKED.get(neutral).get(index);
                        a.updateBoneBuffers(); b.updateBoneBuffers();
                        FloatBuffer adq = ((com.wurmonline.client.renderer.DualQuaternionBuffer)dqField.get(a)).buffer;
                        FloatBuffer bdq = ((com.wurmonline.client.renderer.DualQuaternionBuffer)dqField.get(b)).buffer;
                        float[] ao = new float[6], bo = new float[6];
                        boolean bodyMesh=a==bakedBody(data);
                        if(bodyMesh && clip.equals("idle") && phase==0)soleY=new float[a.getVertexCount()];
                        for (int vertex = 0; vertex < a.getVertexCount(); vertex++) {
                            shader(a,adq,vertex,ao); shader(b,bdq,vertex,bo);
                            if(bodyMesh && clip.equals("idle") && shape.equals("none")){
                                if(phase==0)soleY[vertex]=ao[1];
                                float[] bind=a.getOriginalVertexBuffer();
                                if(bind[vertex*3+1]<.003f && Math.abs(bind[vertex*3])>.08f){
                                    float bob=Math.abs(ao[1]-soleY[vertex]);maxGpuSoleBob=Math.max(maxGpuSoleBob,bob);
                                    if(bob>.0005f)throw new AssertionError("GPU Idle sole bobs: "+bob+" at "+dirt+" dirt");
                                }
                            }
                            for (int axis = 0; axis < 3; axis++) {
                                near(ao[axis],bo[axis]*scale,"Native GPU DQ proportional position");
                                near(ao[axis+3],bo[axis+3],"Native GPU DQ normal");
                                near(a.getVertexBuffer().get(vertex*3+axis),a.getOriginalVertexBuffer()[vertex*3+axis]*scale,"GPU private bind position");
                            }
                        }
                    }
                    count++;
                }
            }
        }
        // Copies taken from a scaled GPU body must restore private buffers too.
        ColladaModel copy = new ColladaModel(world);
        ColladaModelData copied = data(copy,new File("height-copy.dae"));
        for (TriangleMesh mesh : BAKED.get(copied)) gpu.setBoolean(mesh,true);
        copied.calculateSkins(TriangleMesh.LODLevel.NONE);
        for (TriangleMesh mesh : BAKED.get(copied)) for (int i = 0; i < mesh.getVertexBuffer().capacity(); i++)
            near(mesh.getVertexBuffer().get(i),mesh.getOriginalVertexBuffer()[i],"GPU copy retained height buffer");
        AnimationLab.submit(LabJson.map("reset",true));
        AvatarGeometryPrototype.request(reference,GeometryProbe.NEUTRAL);
        sample(world,data,fk,reference,neutral,rfk,"walk",.25f);
        for (ColladaModelData d : new ColladaModelData[]{data,neutral}) for (TriangleMesh mesh : allMeshes(d)) {
            gpu.setBoolean(mesh,false); material.set(mesh,null);
        }
        sample(world,data,fk,reference,neutral,rfk,"walk",.25f); compareMeshes(data,neutral,1);
        System.out.println("AVATAR2_IDLE_GPU_SOLE_OK "+world.getUrl()+" max_skinned_y_change_m="+maxGpuSoleBob);
        return count;
    }

    private static List<TriangleMesh> allMeshes(ColladaModelData data) {
        List<TriangleMesh> meshes = new ArrayList<>(BAKED.get(data));
        for (ColladaGeometry geometry : data.getModel().getGeometryArray())
            Collections.addAll(meshes,geometry.getTriangleMeshArray());
        return meshes;
    }

    private static void shader(TriangleMesh mesh,FloatBuffer dq,int vertex,float[] output) {
        // Shader quaternion component order is x,y,z,w. Match antipodality,
        // weighted blending, joint normalization, cross products and translation.
        float nx=0,ny=0,nz=0,nw=0,dx=0,dy=0,dz=0,dw=0;
        int first = mesh.getBoneBuffer().get(vertex*4)*8;
        for (int influence=0; influence<4; influence++) {
            int slot=vertex*4+influence, base=mesh.getBoneBuffer().get(slot)*8;
            float sign=influence==0?1:Math.signum(dq.get(first)*dq.get(base)+dq.get(first+1)*dq.get(base+1)+dq.get(first+2)*dq.get(base+2)+dq.get(first+3)*dq.get(base+3));
            float weight=mesh.getWeightBuffer().get(slot)*sign;
            nx+=weight*dq.get(base); ny+=weight*dq.get(base+1); nz+=weight*dq.get(base+2); nw+=weight*dq.get(base+3);
            dx+=weight*dq.get(base+4); dy+=weight*dq.get(base+5); dz+=weight*dq.get(base+6); dw+=weight*dq.get(base+7);
        }
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz+nw*nw), inv=length>0?1/length:1;
        nx*=inv; ny*=inv; nz*=inv; nw*=inv; dx*=inv; dy*=inv; dz*=inv; dw*=inv;
        for (int normal=0;normal<2;normal++) {
            FloatBuffer input=normal==0?mesh.getVertexBuffer():mesh.getNormalBuffer();
            float x=input.get(vertex*3),y=input.get(vertex*3+1),z=input.get(vertex*3+2);
            float cx=ny*z-nz*y+nw*x,cy=nz*x-nx*z+nw*y,cz=nx*y-ny*x+nw*z;
            output[normal*3]=x+2*(ny*cz-nz*cy);
            output[normal*3+1]=y+2*(nz*cx-nx*cz);
            output[normal*3+2]=z+2*(nx*cy-ny*cx);
        }
        output[0]+=2*(nw*dx-dw*nx+ny*dz-nz*dy);
        output[1]+=2*(nw*dy-dw*ny+nz*dx-nx*dz);
        output[2]+=2*(nw*dz-dw*nz+nx*dy-ny*dx);
    }

    private static ColladaModelData data(ColladaModel model,File file) throws Exception {
        ColladaModelData data = new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),model);
        data.setSpecificMeshesToDraw(null);
        List<TriangleMesh> meshes = new ArrayList<>();
        for (ColladaGeometry geometry : model.getGeometryArray()) for (TriangleMesh mesh : geometry.getTriangleMeshArray()) {
            TriangleMesh baked = data.bakeDefaultMesh(mesh); meshes.add(baked);
            data.addBakedMeshToList(baked,null);
        }
        BAKED.put(data,meshes);
        return data;
    }
    private static void sample(ColladaModel world,ColladaModelData data,ForwardKinematics fk,
                               ColladaModel reference,ColladaModelData neutral,ForwardKinematics rfk,String clip,float phase) {
        AnimationLab.beginFrame(world);
        fk.calculateForwardKinematics(phase,world.getColladaAnimation(clip),false,false,false);
        fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);
        if (AnimationLab.beforeForwardKinematics(world)) fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);
        AnimationLab.afterForwardKinematics(world);
        data.calculateSkins(TriangleMesh.LODLevel.NONE); AnimationLab.afterSkinning(world); AnimationLab.endFrame();
        rfk.calculateForwardKinematics(phase,reference.getColladaAnimation(clip),false,false,false);
        rfk.updateBoneTransformations(reference,reference.getJoint("Root"),null,false,0,0);
        neutral.calculateSkins(TriangleMesh.LODLevel.NONE);
    }
    private static void compareMeshes(ColladaModelData actual,ColladaModelData neutral,float scale) {
        // Include eyes: independent baked output must follow the same pose scale.
        for (int mesh = 0; mesh < BAKED.get(actual).size(); mesh++) {
            TriangleMesh a = BAKED.get(actual).get(mesh), b = BAKED.get(neutral).get(mesh);
            for (int i = 0; i < a.getVertexBuffer().capacity(); i++) {
                near(a.getVertexBuffer().get(i),b.getVertexBuffer().get(i)*scale,"CPU proportional height");
                near(a.getNormalBuffer().get(i),b.getNormalBuffer().get(i),"CPU height normal");
            }
        }
    }
    private static void verifySocket(ColladaModel actual,ColladaModel neutral,String name) {
        Matrix4f a = actual.getNullTransformation(name), b = neutral.getNullTransformation(name);
        float scale = ((Number)AvatarHeightPrototype.describe(actual).get("scale")).floatValue();
        near(a.m03,b.m03*scale,"Socket position X"); near(a.m13,b.m13*scale,"Socket position Y"); near(a.m23,b.m23*scale,"Socket position Z");
        Matrix4f byIndex = actual.getNullTransformation(actual.getNullId(name),new Matrix4f());
        near(byIndex.m13,a.m13,"Indexed socket position"); near(byIndex.m00,a.m00,"Indexed socket rotation");
        Matrix4f into = actual.getNullTransformation(name,new Matrix4f());
        near(into.m13,a.m13,"Output-argument socket position"); near(into.m00,a.m00,"Output-argument socket rotation");
        Matrix4f missing = actual.getNullTransformation("does-not-exist");
        near(missing.m00,1,"Unknown socket fallback");
        for (int axis = 0; axis < 3; axis++) {
            com.wurmonline.math.Vector3f point = new com.wurmonline.math.Vector3f(axis==0?1:0,axis==1?1:0,axis==2?1:0);
            com.wurmonline.math.Vector3f ap = a.mult(point), bp = b.mult(point);
            near(ap.x-a.m03,bp.x-b.m03,"Rigid item X"); near(ap.y-a.m13,bp.y-b.m13,"Rigid item Y"); near(ap.z-a.m23,bp.z-b.m23,"Rigid item Z");
        }
        float[] av = actual.getNullScale(name).vector.clone(), bv = neutral.getNullScale(name).vector.clone();
        for (int i = 0; i < 3; i++) near(av[i],bv[i],"Native socket scale");
        av = actual.getNullRotation(name).vector.clone(); bv = neutral.getNullRotation(name).vector.clone();
        for (int i = 0; i < 3; i++) near(av[i],bv[i],"Native socket rotation");
    }
    private static TriangleMesh body(ColladaModel model) {
        for (ColladaGeometry geometry : model.getGeometryArray()) for (TriangleMesh mesh : geometry.getTriangleMeshArray())
            if (mesh.getName().startsWith("Avatar2BodyMesh")) return mesh;
        throw new AssertionError("Missing body");
    }
    private static TriangleMesh bakedBody(ColladaModelData data) {
        int length=body(data.getModel()).getOriginalVertexBuffer().length;
        for(TriangleMesh mesh:BAKED.get(data))if(mesh.getOriginalVertexBuffer().length==length)return mesh;
        throw new AssertionError("Missing independently baked body");
    }
    private static void near(float a,float b,String label) { if (!Float.isFinite(a) || Math.abs(a-b)>=.00003f) throw new AssertionError(label+": "+a+" != "+b); }
    private static void require(boolean condition,String text) { if (!condition) throw new AssertionError(text); }
}
