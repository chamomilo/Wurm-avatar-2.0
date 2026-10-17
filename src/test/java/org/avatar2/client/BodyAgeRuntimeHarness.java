package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.*;
import org.avatar2.core.*;
import org.avatar2.lab.AnimationLab;
import java.io.*;
import java.util.*;

/** Native source/baked poses, exact released Young and reversible adult presets. */
public final class BodyAgeRuntimeHarness {
    public static void run(String root) throws Exception {
        java.nio.file.Files.createDirectories(new File(root,"build/reports/avatar2").toPath());
        int cases=0,poses=0;List<String> report=new ArrayList<>();
        report.add("version,sex,age,muscle,fullness,breast,height_dirts,cpu_poses");
        try {
            for(String sex:new String[]{"male","female"}) {
                AvatarProfile p=new AvatarProfile();p.sex=AvatarProfile.Sex.valueOf(sex.toUpperCase(Locale.ROOT));AvatarRuntimeState.apply(p);
                File file=new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae");
                ColladaModel template=GeometryRuntimeHarness.load(file);AvatarAnimationBridge.normalizeSkinWeights(template);
                try(InputStream in=new FileInputStream(new File(root,"assets/runtime/morph/"+sex+"-bodybuilder.avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(template,MorphBank.read(in));
                }
                AvatarWalkMotion.register(template);
                AvatarAgeSkin.register(template,new AgeSkinFixtureUrl(new File(root),"avatar2/body/"+sex+"-bodybuilder.dae"));
                ColladaModel world=new ColladaModel(template);ColladaModelData data=new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),world);
                List<TriangleMesh> sources=AvatarGeometryPrototype.sources(world),baked=new ArrayList<>();
                for(TriangleMesh mesh:sources){TriangleMesh b=data.bakeDefaultMesh(mesh);data.addBakedMeshToList(b,null);baked.add(b);}
                AnimationLab.start();AnimationLab.registerLocalModel(world);sample(world,data,"idle",0);
                MorphBank previous;
                File current=new File(root,"assets/runtime/morph/"+sex+"-bodybuilder.avm");
                verifyReleasedTargets(root,current);
                try(InputStream in=new FileInputStream(current)) {previous=MorphBank.read(in);}
                List<Map<String,Object>> descriptors=AvatarGeometryPrototype.describe(template);
                for(float muscle:new float[]{0,.5f,1})for(float fat:new float[]{0,.5f,1})for(float breast:sex.equals("female")?new float[]{0,.5f,1}:new float[]{.5f}) {
                    GeometryProbe young=new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat,breast);
                    submit(young,0);sample(world,data,"idle",0);
                    List<float[]> originals=new ArrayList<>();
                    for(int mi=0;mi<sources.size();mi++) {
                        TriangleMesh base=AvatarGeometryPrototype.sources(template).get(mi);float[] expected=new float[base.getOriginalVertexBuffer().length],normals=new float[expected.length];
                        previous.matching((String)descriptors.get(mi).get("basisSha256")).deform(young,base.getOriginalVertexBuffer(),base.getOriginalNormalBuffer(),expected,normals);
                        require(Arrays.equals(expected,sources.get(mi).getOriginalVertexBuffer()),"Young changed released Build");
                        originals.add(expected);
                    }
                    for(String age:BodyAge.PRESETS)for(int dirt:new int[]{13,20}) {
                        GeometryProbe g=young.withAgePreset(age);submit(g,dirt);sample(world,data,"idle",0);
                        for(int mi=0;mi<sources.size();mi++) {
                            TriangleMesh m=sources.get(mi);float[] before=originals.get(mi),after=m.getOriginalVertexBuffer(),normals=m.getOriginalNormalBuffer();
                            require(Arrays.equals(after,baked.get(mi).getOriginalVertexBuffer()),"Age source/baked mismatch");
                            if(m.getName().startsWith("Avatar2EyesMesh"))require(Arrays.equals(before,after),"Body age moved eyes");
                            double movement=0;
                            for(int i=0;i<after.length;i+=3) {
                                double n=0;for(int a=0;a<3;a++){require(Float.isFinite(after[i+a])&&Float.isFinite(normals[i+a]),"Nonfinite age");n+=normals[i+a]*normals[i+a];movement+=Math.abs(after[i+a]-before[i+a]);}
                                require(Math.abs(n-1)<.002,"Age normal not unit");
                            }
                            for(int vertex=0;vertex<m.getVertexCount();vertex++) {
                                float protectedWeight=0;
                                for(int slot=0;slot<4;slot++) {
                                    String bone=m.getBones()[m.getBoneBuffer().get(vertex*4+slot)].getName();
                                    if(bone.equals("head")||bone.startsWith("hand_")||bone.startsWith("foot_")||bone.startsWith("ball_")||bone.startsWith("toe")||bone.startsWith("thumb_")||bone.startsWith("index_")||bone.startsWith("middle_")||bone.startsWith("ring_")||bone.startsWith("pinky_"))protectedWeight+=m.getWeightBuffer().get(vertex*4+slot);
                                }
                                if(protectedWeight>.99999f)for(int axis=0;axis<3;axis++)require(Math.abs(before[vertex*3+axis]-after[vertex*3+axis])<.000002f,"Age moves head/grips/soles");
                            }
                            if(age.equals("old")&&mi==0)require(movement>.01,"Old body is empty");
                            try {triangles(m,before,after);}catch(AssertionError error){throw new AssertionError(sex+" "+age+" muscle="+muscle+" fat="+fat+" breast="+breast+" mesh="+m.getName()+": "+error.getMessage());}
                        }
                        for(String clip:new String[]{"idle","walk","run"})for(int phase=0;phase<4;phase++) {
                            sample(world,data,clip,phase/4f);for(TriangleMesh m:baked)GeometryRuntimeHarness.verifyCpuSkin(m);poses++;
                        }
                        if(muscle==.5f&&fat==.5f&&breast==.5f&&dirt==20){sample(world,data,"idle",0);GeometryRuntimeHarness.writePose(new File(root,"build/geometry-poses/"+sex+"-bodybuilder-age-"+age+".obj"),baked.toArray(new TriangleMesh[0]));}
                        report.add(Avatar2Mod.VERSION+","+sex+","+age+","+muscle+","+fat+","+breast+","+dirt+",12");cases++;
                    }
                }
                AnimationLab.studioAge("old");AnimationLab.studioBuild("Muscle",.37f);
                require(AnimationLab.studioControls(false).agePreset.equals("old"),"Muscle edit lost age");
                AvatarProfile saved=AnimationLab.studioProfile();
                require(saved.agePreset.equals("old")&&saved.ageSkin.equals("old"),"Lab Save lost adult age/skin");
                AvatarRuntimeState.apply(saved);AnimationLab.studioReset();sample(world,data,"idle",0);
                require(AnimationLab.studioControls(false).agePreset.equals("old"),"Reset lost applied age");
                AnimationLab.studioAge("middle");AnimationLab.stop();sample(world,data,"idle",0);
                require(AvatarRuntimeState.profile().agePreset.equals("old"),"Trial overwrote profile");
                require(AvatarGeometryPrototype.describe(world).get(0).get("agePreset").equals("old"),"Lab off did not restore age");
                AnimationLab.registerLocalModel(null);
                for(TriangleMesh mesh:AvatarGeometryPrototype.sources(template))require(AvatarGeometryPrototype.describe(template).get(0).get("agePreset").equals("young"),"Age mutated template");
            }
            java.nio.file.Files.write(new File(root,"build/reports/avatar2/body-age-runtime.csv").toPath(),report,java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("AVATAR2_BODY_AGE_OK cases="+cases+" CPU poses="+poses+" both sexes, exact released Young, muscle/fullness/breast grid, source/baked, normals/triangles, eyes, Lab edits/Save/reset/off and templates");
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);AvatarRuntimeState.apply(new AvatarProfile());}
    }
    private static void submit(GeometryProbe g,int dirt)throws Exception {
        Map<String,Object> geometry=new LinkedHashMap<>();geometry.put("shape",g.shape);geometry.put("shapeMix",g.shapeMix);geometry.put("buildMuscle",g.buildMuscle);geometry.put("buildFat",g.buildFat);geometry.put("buildBreast",g.buildBreast);geometry.put("agePreset",g.agePreset);
        Map<String,Object> command=new LinkedHashMap<>();command.put("geometry",geometry);if(dirt!=0)command.put("heightDirts",dirt);
        java.lang.reflect.Method method=AnimationLab.class.getDeclaredMethod("submit",Map.class);method.setAccessible(true);method.invoke(null,command);
    }
    private static void sample(ColladaModel model,ColladaModelData data,String clip,float phase) {
        ForwardKinematics fk=new ForwardKinematics();AnimationLab.beginFrame(model);
        try {fk.calculateForwardKinematics(phase,model.getColladaAnimation(clip),false,false,false);fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            if(AnimationLab.beforeForwardKinematics(model))fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            AnimationLab.afterForwardKinematics(model);data.calculateSkins(TriangleMesh.LODLevel.NONE);AnimationLab.afterSkinning(model);
        }finally{AnimationLab.endFrame();}
    }
    private static void triangles(TriangleMesh mesh,float[] before,float[] after) {
        java.nio.IntBuffer ids=mesh.getIndexBuffer();
        for(int i=0;i<ids.capacity();i+=3){double[] a=cross(before,ids.get(i),ids.get(i+1),ids.get(i+2)),b=cross(after,ids.get(i),ids.get(i+1),ids.get(i+2));double aa=dot(a,a),bb=dot(b,b);
            if(aa>1e-18)require(bb>aa*.05&&dot(a,b)>0,"Age triangle collapses or reverses at "+i/3+" area_ratio="+bb/aa+" cos="+dot(a,b)/Math.sqrt(aa*bb)+" ids="+ids.get(i)+","+ids.get(i+1)+","+ids.get(i+2));}
    }
    private static double[] cross(float[] v,int a,int b,int c){double x=v[b*3]-v[a*3],y=v[b*3+1]-v[a*3+1],z=v[b*3+2]-v[a*3+2],u=v[c*3]-v[a*3],w=v[c*3+1]-v[a*3+1],t=v[c*3+2]-v[a*3+2];return new double[]{y*t-z*w,z*u-x*t,x*w-y*u};}
    private static double dot(double[]a,double[]b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static void verifyReleasedTargets(String root,File bank)throws Exception {
        Map<String,Object> metadata=org.avatar2.lab.LabJson.object(org.avatar2.lab.LabJson.parse(new String(java.nio.file.Files.readAllBytes(new File(root,"assets/metadata/body-age-0140.json").toPath()),java.nio.charset.StandardCharsets.UTF_8)));
        Map<String,Object> expected=null;
        for(Object value:(List<?>)metadata.get("banks")){Map<String,Object> row=org.avatar2.lab.LabJson.object(value);if(bank.getName().equals(row.get("body")+".avm"))expected=row;}
        require(expected!=null,"Missing released morph hashes");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(bank)))) {
            require(in.readInt()==MorphBank.MAGIC,"Invalid bank");in.readUTF();require(in.readUTF().equals(expected.get("sourceDaeSha256")),"Age changed approved DAE");
            Map<String,Object> meshes=org.avatar2.lab.LabJson.object(expected.get("meshes"));int count=in.readInt();
            for(int mi=0;mi<count;mi++) {
                Map<String,Object> mesh=org.avatar2.lab.LabJson.object(meshes.get(in.readUTF()));require(in.readUTF().equals(mesh.get("name")),"Age changed mesh identity");
                int components=in.readInt(),targets=in.readInt();Map<String,Object> hashes=org.avatar2.lab.LabJson.object(mesh.get("targets"));Set<String> seen=new HashSet<>();
                for(int t=0;t<targets;t++){String name=in.readUTF();byte[] bytes=new byte[components*8];in.readFully(bytes);
                    if(hashes.containsKey(name)){StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));require(hash.toString().equals(hashes.get(name)),"Age changed released target: "+name);seen.add(name);}}
                require(seen.equals(hashes.keySet()),"Age removed a released target");
            }
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
