package org.avatar2.client;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.*;
import com.wurmonline.client.renderer.cell.*;
import com.wurmonline.client.renderer.model.*;
import com.wurmonline.client.renderer.model.collada.*;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector3f;
import com.wurmonline.client.renderer.model.wom.WOMLoader;
import com.wurmonline.client.resources.*;
import org.avatar2.core.*;
import org.avatar2.lab.AnimationLab;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Native hairstyle choice, attachment matrix and completed CPU/GPU world/paper poses. */
public final class HairRuntimeHarness {
    private static sun.misc.Unsafe unsafe;
    private static Method worker, renderHair;
    private static final Map<String,ColladaModel> HAIRS=new HashMap<>();
    private static final Map<ColladaModelData,List<TriangleMesh>> BAKED=new IdentityHashMap<>();
    private static Matrix captured;
    private static Color capturedColour;
    private static String createdName;
    private static ColladaModel drawnHair;
    private static int scalpSubmissions,detailSubmissions;
    private static int created, draws;
    private static boolean createdMale;
    private static volatile boolean pause;
    private static CountDownLatch prepared,resume;

    public static void awaitBeforeSkin() throws Exception {
        if(pause){prepared.countDown();if(!resume.await(5,TimeUnit.SECONDS))throw new AssertionError("Paused hair worker timeout");}
    }

    public static AttachedCellRenderable createHair(String name,ModelResourceWrapper parent,boolean male)throws Exception {
        created++;createdName=name;createdMale=male;
        AttachedCellRenderable hair=(AttachedCellRenderable)unsafe.allocateInstance(AttachedCellRenderable.class);
        set(hair,AttachedCellRenderable.class,"nullTransform",new com.wurmonline.math.Transform());
        for(String field:new String[]{"nullMatrix","tempMatrix","finalMatrix"})set(hair,AttachedCellRenderable.class,field,new Matrix());
        set(hair,AttachedCellRenderable.class,"rotationQuaternion",new com.wurmonline.client.renderer.model.collada.math.Quaternion());
        set(hair,AttachedCellRenderable.class,"rotationMatrix",new com.wurmonline.client.renderer.model.collada.math.Matrix3f());
        set(hair,AttachedCellRenderable.class,"transform",new Matrix4f());
        set(hair,AttachedCellRenderable.class,"axisOffset",new com.wurmonline.math.Vector3f());
        set(hair,AttachedCellRenderable.class,"axis",new com.wurmonline.math.Vector3f());
        set(hair,AttachedCellRenderable.class,"isMale",male);
        for(Class<?> type=AttachedCellRenderable.class;type!=null;type=type.getSuperclass()) {
            try{set(hair,type,"currentLod",TriangleMesh.LODLevel.NONE);break;}catch(NoSuchFieldException ignored){}
        }
        String type=name.replace(".female","");
        int style=Integer.parseInt(type.substring(4))+(male?0:1);
        ColladaModel model=HAIRS.get((male?"male":"female")+"-"+style);
        if(model==null)model=HAIRS.get((male?"male":"female")+"-1");
        String path="player/hair/"+(male?"male_hair":"femaleHair")+"0"+style+".wom";
        set(hair,StaticModelRenderable.class,"model",wrapper(new ColladaModelData(new HairFixtureUrl(new File("C:/Program Files (x86)/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/packs/graphics.jar"),path),model)));
        return hair;
    }

    public static void draw(Matrix parent,ModelResourceWrapper wrapper,AttachedCellRenderable hair,String name,ModelRenderMode mode,Color colour) {
        require(name.equals("NullHelmet"),"Native hairstyle offset name changed");
        hair.setParentMatrix(parent);hair.setParentModel(wrapper);hair.setNullName(name);
        hair.prepareRender();captured=new Matrix(hair.getModelMatrix());capturedColour=colour;drawnHair=((ColladaModelData)hair.getModelWrapper().getModelData()).getModel();draws++;
        int before=scalpSubmissions,detailBefore=detailSubmissions;hair.render(null,mode,null,colour);
        if(drawnHair.getGeometryArray().length>1)require(scalpSubmissions==before+1,"Native render dispatch omitted the scalp");
        int expected=0;for(ColladaGeometry g:drawnHair.getGeometryArray())for(TriangleMesh mesh:g.getTriangleMeshArray())if(mesh.getName().endsWith("Avatar2Detail")&&mesh.shouldRender)expected++;
        require(detailSubmissions-detailBefore==expected,"Native render dispatch omitted refined hairstyle meshes");
    }

    public static void submitted(TriangleMesh mesh,Matrix parent,Color colour) {
        if(mesh.getName().endsWith("Avatar2Detail"))detailSubmissions++;
        if(mesh.getName().equals("Avatar2HairScalp")) {
            scalpSubmissions++;same(parent,captured,"Scalp render matrix differs from hairstyle");
            require(colour==capturedColour,"Scalp did not retain native hair colour");
        }
    }

    public static void run(String root)throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);unsafe=(sun.misc.Unsafe)access.get(null);
        Method setWorld=CellRenderable.class.getDeclaredMethod("setWorld",World.class);setWorld.setAccessible(true);setWorld.invoke(null,unsafe.allocateInstance(World.class));
        worker=ColladaAnimationJob.class.getDeclaredMethod("applyAnimations",ColladaModelData.class,ColladaModel.class,boolean.class,boolean.class,boolean.class,TriangleMesh.LODLevel.class);worker.setAccessible(true);
        renderHair=PlayerBodyRenderable.class.getDeclaredMethod("renderHair",com.wurmonline.client.renderer.backend.Queue.class,Matrix.class,ModelResourceWrapper.class,ModelRenderMode.class,com.wurmonline.client.renderer.backend.RenderState.class,Color.class);renderHair.setAccessible(true);
        List<String> report=new ArrayList<>();report.add("version,sex,mode,scenario,height,head_size,clip,phase,model_poses,hairstyle_placements");
        List<String> cameraReport=new ArrayList<>();cameraReport.add("version,sex,mode,height,head_size,free_camera,render_pass,dx,dy,dz,head_masked,world_hair_draws,portrait_hair_draws,scope_restored");
        List<String> detailReport=new ArrayList<>();detailReport.add("version,sex,style,native_vertices,native_triangles,detail_vertices,detail_triangles,max_patch_delta_m,validation");
        Map<String,Long> originalBuffers=new HashMap<>();
        int poses=0,placements=0,interleavings=0;
        File graphics=new File("C:/Program Files (x86)/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/packs/graphics.jar");
        for(String sex:new String[]{"male","female"})for(int n=1;n<=(sex.equals("male")?5:7);n++) {
            String path="player/hair/"+(sex.equals("male")?"male_hair":"femaleHair")+"0"+n+".wom";
            FakeResourceUrl url=new FakeResourceUrl(new URL("jar:"+graphics.toURI().toURL()+"!/"+path));
            ColladaModel hair=WOMLoader.loadeNewModel(url);ColladaPropertiesXml.load(hair,url,true);HAIRS.put(sex+"-"+n,hair);
            originalBuffers.put(sex+"-"+n,hash(hair));
        }
        try {
            for(String sex:new String[]{"male","female"}) {
                boolean male=sex.equals("male");int styles=male?5:7;
                File file=new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae");ColladaModel template=GeometryRuntimeHarness.load(file);
                AvatarAnimationBridge.normalizeSkinWeights(template);
                try(InputStream in=new FileInputStream(new File(root,"assets/runtime/morph/"+sex+"-bodybuilder.avm"))){AvatarGeometryPrototype.registerWithMorphs(template,MorphBank.read(in));}
                AvatarWalkMotion.register(template);AvatarHair.register(template,male);AvatarFirstPerson.register(template);
                Matrix4f untouched=AvatarHair.frame(template);
                ColladaModel world=new ColladaModel(template),paper=new ColladaModel(template);
                ColladaModelData wd=data(world,file,sex),pd=data(paper,file,sex);
                AnimationLab.registerLocalModel(world);AnimationLab.registerPaperModel(paper);
                ModelResourceWrapper wrapper=wrapper(wd),paperWrapper=wrapper(pd);PlayerBodyRenderable body=body(wrapper,paperWrapper,!male);
                AvatarProfile profile=new AvatarProfile();profile.sex=AvatarProfile.Sex.valueOf(sex.toUpperCase(Locale.ROOT));
                for(boolean gpu:new boolean[]{false,true}) {
                    gpu(wd,gpu);gpu(pd,gpu);
                    for(int scenario=0;scenario<6;scenario++)for(float head:new float[]{0,.5f,1})for(float height:new float[]{1.3f,profile.nativeHeightMetres(profile.sex),2})for(String clip:new String[]{"idle","walk","run"})for(int phase=0;phase<4;phase++) {
                        profile.shape=scenario==0?"none":scenario<4?new String[]{"mpfb-athletic","mpfb-heavy","mpfb-older"}[scenario-1]:"proportions";
                        profile.shapeMix=scenario==0?0:1;profile.buildMuscle=scenario==4?0:1;profile.buildFat=scenario==4?1:0;
                        profile.agePreset=scenario==4?"middle":"old";profile.headSize=head;profile.heightMetres=height;
                        AvatarRuntimeState.apply(profile);sample(wd,clip,phase/4f);sample(pd,clip,phase/4f);poses+=2;
                        Matrix4f frame=AvatarHair.frame(world);same(frame,AvatarHair.frame(paper),"World/paper hair pose differs");
                        Matrix nativeParent=new Matrix().fromTranslationRotation(.17f,-.04f,.12f,0,.3f,0);
                        Matrix parentCopy=new Matrix(nativeParent);
                        for(int n=1;n<=styles;n++) {
                            int nativeId=male?n:n-1;
                            PlayerFace face=face(body);set(face,PlayerFace.class,"hairTypeModelName","hair"+nativeId);face.setChangeHair(true);
                            renderHair.invoke(body,null,nativeParent,wrapper,ModelRenderMode.RENDER_NORMAL,null,null);
                            require(createdMale==male&&createdName.equals("hair"+nativeId+(male?"":".female")),"Hair resource does not follow Avatar sex");
                            same(captured,AvatarHair.parentMatrix(wrapper,nativeParent),"Native attachment matrix differs from complete fit");
                            require(capturedColour==face.getHairColor(),"Native hair colour changed");
                            ColladaModel originalHair=HAIRS.get(sex+"-"+n);
                            require(drawnHair!=originalHair&&drawnHair.getGeometryArray().length==originalHair.getGeometryArray().length+1,"Scalp copy was not private");
                            if(!gpu&&scenario==0&&head==.5f&&height==profile.nativeHeightMetres(profile.sex)&&clip.equals("idle")&&phase==0)
                                verifyDetail(sex,n,originalHair,drawnHair,detailReport);
                            TriangleMesh scalp=drawnHair.getGeometryArray()[drawnHair.getGeometryArray().length-1].getTriangleMeshArray()[0];
                            require(scalp.getName().equals("Avatar2HairScalp")&&scalp.getIndexCount()>300,"Missing fitted scalp triangles");
                            verifyScalp(scalp,AvatarGeometryPrototype.scalp(world),AvatarGeometryPrototype.headHairFit(world,male));
                            finitePlacement(drawnHair,frame);placements++;
                            if(!gpu&&scenario==0&&height==profile.nativeHeightMetres(profile.sex)&&clip.equals("idle")&&phase==0)
                                exportHair(new File(root,"build/geometry-poses/"+sex+"-hair-"+n+"-"+head+".obj"),drawnHair,frame);
                            AvatarHair.endDraw();
                        }
                        same(parentCopy,nativeParent,"Hair mutates body parent matrix");
                        if(scenario==0&&clip.equals("idle")&&phase==0)
                            cameraDraws(body,wd,pd,wrapper,paperWrapper,sex,gpu,height,head,cameraReport);
                        report.add(Avatar2Mod.VERSION+","+sex+","+(gpu?"GPU":"CPU")+","+scenario+","+height+","+head+","+clip+","+phase+",2,"+styles);
                        if(!gpu&&scenario==0&&height==profile.nativeHeightMetres(profile.sex)&&clip.equals("idle")&&phase==0) {
                            GeometryRuntimeHarness.writePose(new File(root,"build/geometry-poses/"+sex+"-hair-body-"+head+".obj"),BAKED.get(wd).toArray(new TriangleMesh[0]));
                        }
                    }
                }
                gpu(wd,false);profile.shape="none";profile.shapeMix=0;profile.heightMetres=profile.nativeHeightMetres(profile.sex);profile.headSize=.5f;AvatarRuntimeState.apply(profile);sample(wd,"idle",0);
                Matrix4f complete=AvatarHair.frame(world);profile.headSize=1;profile.heightMetres=2;AvatarRuntimeState.apply(profile);
                prepared=new CountDownLatch(1);resume=new CountDownLatch(1);pause=true;AtomicReference<Throwable> failure=new AtomicReference<>();
                Thread thread=new Thread(()->{try{sample(wd,"walk",.25f);}catch(Throwable e){failure.set(e);}},"avatar-hair-worker");thread.start();
                require(prepared.await(5,TimeUnit.SECONDS),"Hair worker did not pause");
                try{for(int i=0;i<100;i++)same(complete,AvatarHair.frame(world),"Reader observed unfinished hair pose");}
                finally{pause=false;resume.countDown();thread.join(5000);}
                require(!thread.isAlive()&&failure.get()==null,"Hair worker failed: "+failure.get());
                require(!equal(complete,AvatarHair.frame(world)),"Hair completed frame was not published");interleavings++;
                int before=draws;AvatarFirstPerson.begin(body,true);
                try{renderHair.invoke(body,null,new Matrix(),wrapper,ModelRenderMode.RENDER_NORMAL,null,null);require(draws==before,"First-person hair remains visible");
                    renderHair.invoke(body,null,new Matrix(),paperWrapper,ModelRenderMode.RENDER_NORMAL,null,null);require(draws==before+1,"Portrait hair hidden by world first-person scope");}
                finally{AvatarFirstPerson.end();}
                renderHair.invoke(body,null,new Matrix(),wrapper,ModelRenderMode.RENDER_NORMAL,null,null);require(draws==before+2,"Third-person hair did not resume");
                int count=created;renderHair.invoke(body,null,new Matrix(),wrapper,ModelRenderMode.RENDER_NORMAL,null,null);require(created==count,"Stable hairstyle recreated every frame");
                ModelResourceWrapper vanilla=wrapper(new ColladaModelData(new FakeResourceUrl(new URL("file:player/vanilla.wom")),GeometryRuntimeHarness.load(file)));
                set(body,PlayerBodyRenderable.class,"playerModel",vanilla);renderHair.invoke(body,null,new Matrix(),vanilla,ModelRenderMode.RENDER_NORMAL,null,null);
                require(createdMale!=male,"Vanilla hair does not return to native sex");same(captured,new Matrix(),"Vanilla attachment changed");
                set(body,PlayerBodyRenderable.class,"playerModel",wrapper);renderHair.invoke(body,null,new Matrix(),wrapper,ModelRenderMode.RENDER_NORMAL,null,null);require(createdMale==male,"Re-enabled Avatar uses stale hair");
                same(untouched,AvatarHair.frame(template),"Shared template hair frame mutated");
                require(!world.hasNull("NullHelmet"),"Hair enabled unfitted helmet socket");
                Matrix4f leaked=AvatarHair.frame(world);leaked.m13+=10;require(!equal(leaked,AvatarHair.frame(world)),"Hair frame aliases renderer input");
                Matrix4f saved=AvatarHair.frame(world);
                AnimationLab.start();AnimationLab.studioHeadSize(0);AnimationLab.studioHeight(13);sample(wd,"idle",0);
                require(!equal(AvatarHair.frame(world),leaked),"Hair trial was not independent");AnimationLab.studioReset();AnimationLab.stop();sample(wd,"walk",.25f);
                same(saved,AvatarHair.frame(world),"Lab off/reset did not restore the saved hair fit");
                AnimationLab.registerLocalModel(null);AnimationLab.registerPaperModel(null);
            }
            Files.write(new File(root,"build/reports/avatar2/hair-runtime.csv").toPath(),report,StandardCharsets.UTF_8);
            Files.write(new File(root,"build/reports/avatar2/camera-head-visibility-runtime.csv").toPath(),cameraReport,StandardCharsets.UTF_8);
            for(Map.Entry<String,Long> original:originalBuffers.entrySet())require(hash(HAIRS.get(original.getKey()))==original.getValue(),"Refinement changed shared native hair buffers");
            require(detailReport.size()==13,"Missing hairstyle detail coverage");
            Files.write(new File(root,"build/reports/avatar2/hair-detail-runtime.csv").toPath(),detailReport,StandardCharsets.UTF_8);
            System.out.println("AVATAR2_HAIR_DETAIL_OK styles=12; curved private patches, UV/material preservation, bounded displacement, finite normals, native template buffers unchanged, textured cranial surface");
            require(cameraReport.size()==1009,"Camera visibility coverage missing");
            System.out.println("AVATAR2_CAMERA_HEAD_VISIBILITY_OK draws="+(cameraReport.size()-1)+"; free/ordinary camera enter/exit, head/eyes/scalp/hair, CPU/GPU, height/head endpoints, portrait and shadow isolation, scope restoration");
            System.out.println("AVATAR2_HAIR_OK native worker poses="+poses+" hairstyle placements="+placements+" completed-frame interleavings="+interleavings+"; 12 installed hairstyles; private scalp/native mesh dispatch, world/paper CPU/GPU, shape/head/height, native sex/colour/cache, first person, vanilla restoration; GL visual review pending");
        }finally{pause=false;if(resume!=null)resume.countDown();AvatarHair.endDraw();AnimationLab.stop();AnimationLab.registerLocalModel(null);AnimationLab.registerPaperModel(null);AvatarRuntimeState.apply(new AvatarProfile());HAIRS.clear();BAKED.clear();}
    }

    private static long hash(ColladaModel model) {
        long result=1;for(ColladaGeometry g:model.getGeometryArray())for(TriangleMesh mesh:g.getTriangleMeshArray()) {
            for(java.nio.FloatBuffer values:new java.nio.FloatBuffer[]{mesh.getVertexBuffer(),mesh.getNormalBuffer(),mesh.getTextureCoordBuffer(0)})
                for(int i=0;i<values.limit();i++)result=result*31+Float.floatToIntBits(values.get(i));
            for(int i=0;i<mesh.getIndexCount();i++)result=result*31+mesh.getIndexBuffer().get(i);
        }return result;
    }

    private static void verifyDetail(String sex,int style,ColladaModel nativeHair,ColladaModel own,List<String> report)throws Exception {
        int originalVertices=0,originalTriangles=0,vertices=0,triangles=0;float maxDelta=0;
        for(int g=0;g<nativeHair.getGeometryArray().length;g++) {
            TriangleMesh[] nativeMeshes=nativeHair.getGeometryArray()[g].getTriangleMeshArray(),privateMeshes=own.getGeometryArray()[g].getTriangleMeshArray();
            require(nativeMeshes.length==privateMeshes.length,"Refinement lost native geometry");
            for(int m=0;m<nativeMeshes.length;m++) {
                TriangleMesh source=nativeMeshes[m],mesh=privateMeshes[m];require(source!=mesh,"Refinement aliases native mesh");
                sameMaterial(source.getColladaMaterial(),mesh.getColladaMaterial());
                require(mesh.getIndexCount()==source.getIndexCount()*9,"Refinement topology incomplete");
                originalVertices+=source.getVertexCount();originalTriangles+=source.getIndexCount()/3;vertices+=mesh.getVertexCount();triangles+=mesh.getIndexCount()/3;
                int point=0;
                for(int t=0;t<source.getIndexCount();t+=3)for(int row=0;row<=3;row++)for(int col=0;col<=3-row;col++,point++) {
                    float b=row/3f,c=col/3f,a=1-b-c;int i=source.getIndexBuffer().get(t),j=source.getIndexBuffer().get(t+1),k=source.getIndexBuffer().get(t+2);float d2=0;
                    for(int axis=0;axis<3;axis++) {
                        float linear=source.getVertexBuffer().get(i*3+axis)*a+source.getVertexBuffer().get(j*3+axis)*b+source.getVertexBuffer().get(k*3+axis)*c;
                        float delta=mesh.getVertexBuffer().get(point*3+axis)-linear;d2+=delta*delta;
                        require(Float.isFinite(mesh.getNormalBuffer().get(point*3+axis)),"Refined normal invalid");
                    }
                    maxDelta=Math.max(maxDelta,(float)Math.sqrt(d2));require(d2<.00401f*.00401f,"Hair patch exceeds displacement limit");
                    if(a<.00001f||b<.00001f||c<.00001f)require(d2<1e-14f,"Refinement opens a native hair seam");
                    for(int axis=0;axis<2;axis++) {
                        float expected=source.getTextureCoordBuffer(0).get(i*2+axis)*a+source.getTextureCoordBuffer(0).get(j*2+axis)*b+source.getTextureCoordBuffer(0).get(k*2+axis)*c;
                        require(Math.abs(mesh.getTextureCoordBuffer(0).get(point*2+axis)-expected)<.000001f,"Refinement changed hairstyle texture mapping");
                    }
                }
                for(int t=0;t<mesh.getIndexCount();t+=3) {
                    int sourceTriangle=t/27*3;
                    Vector3f a=vertex(source,source.getIndexBuffer().get(sourceTriangle)),b=vertex(source,source.getIndexBuffer().get(sourceTriangle+1)),c=vertex(source,source.getIndexBuffer().get(sourceTriangle+2));
                    Vector3f n=b.subtract(a).cross(c.subtract(a));
                    Vector3f da=vertex(mesh,mesh.getIndexBuffer().get(t)),db=vertex(mesh,mesh.getIndexBuffer().get(t+1)),dc=vertex(mesh,mesh.getIndexBuffer().get(t+2));
                    require(n.lengthSquared()<1e-16f||db.subtract(da).cross(dc.subtract(da)).dot(n)>0,"Refinement inverted a native hair triangle");
                }
            }
        }
        require(maxDelta>.0001f,"Hair detail has no geometric effect");
        report.add(Avatar2Mod.VERSION+","+sex+","+style+","+originalVertices+","+originalTriangles+","+vertices+","+triangles+","+maxDelta+",private_curved_patches_native_UV_material_bounded");
    }
    private static Vector3f vertex(TriangleMesh mesh,int i){return new Vector3f(mesh.getVertexBuffer().get(i*3),mesh.getVertexBuffer().get(i*3+1),mesh.getVertexBuffer().get(i*3+2));}
    private static void sameMaterial(Object source,Object copy)throws Exception {
        // Wurm's TriangleMesh copy constructor duplicates the material object.
        for(Method getter:source.getClass().getDeclaredMethods())if((getter.getName().startsWith("get")||getter.getName().equals("hasProperties"))&&getter.getParameterTypes().length==0) {
            Object a=getter.invoke(source),b=getter.invoke(copy);
            boolean same=a instanceof Color&&b instanceof Color?Arrays.equals(((Color)a).array(),((Color)b).array()):Objects.equals(a,b);
            require(same,"Refinement changed material property "+getter.getName());
        }
    }

    private static void cameraDraws(PlayerBodyRenderable body,ColladaModelData world,ColladaModelData paper,
            ModelResourceWrapper wrapper,ModelResourceWrapper portrait,String sex,boolean gpu,float height,float head,List<String> report)throws Exception {
        // Fly into/out of the eye volume repeatedly, including the lower/back side.
        float[][] path={{1,0,0},{0,0,0},{0,-.2f,.1f},{-.35f,0,0},{-.351f,0,0},{0,0,0},{0,0,-1}};
        for(boolean free:new boolean[]{false,true})for(ModelRenderMode pass:new ModelRenderMode[]{ModelRenderMode.RENDER_NORMAL,ModelRenderMode.RENDER_SHADOW})for(float[] p:path) {
            boolean masked=pass==ModelRenderMode.RENDER_NORMAL&&p[0]*p[0]+p[1]*p[1]+p[2]*p[2]<=.35f*.35f;
            int before=draws,worldDraws;
            AvatarFirstPerson.begin(body,AvatarFirstPerson.nearEyes(free,pass,p[0],p[1],p[2]));
            try {
                for(TriangleMesh mesh:BAKED.get(world)) {
                    int[] cut=AvatarFirstPerson.indices(mesh);require(cut!=null,"Camera body mask missing");
                    require(AvatarFirstPerson.indexCount(mesh,mesh.getIndexCount())==(masked?cut.length:mesh.getIndexCount()),"Camera selected wrong body/head indices");
                    require(AvatarFirstPerson.hidden(world,mesh)==(masked&&cut.length==0),"Camera eyes visibility incorrect");
                }
                for(TriangleMesh mesh:BAKED.get(paper)) {
                    require(!AvatarFirstPerson.hidden(paper,mesh)&&AvatarFirstPerson.indexCount(mesh,mesh.getIndexCount())==mesh.getIndexCount(),"Camera hides portrait body/eyes");
                }
                renderHair.invoke(body,null,new Matrix(),wrapper,pass,null,null);worldDraws=draws-before;
                require(worldDraws==(masked?0:1),"Camera exposes head hair/scalp or hides exterior hair");
                java.nio.FloatBuffer uploaded=null;
                if(!masked)uploaded=drawnHair.getGeometryArray()[drawnHair.getGeometryArray().length-1].getTriangleMeshArray()[0].getVertexBuffer();
                renderHair.invoke(body,null,new Matrix(),portrait,pass,null,null);require(draws-before-worldDraws==1,"Camera hides portrait hair");
                if(!masked)require(uploaded==drawnHair.getGeometryArray()[drawnHair.getGeometryArray().length-1].getTriangleMeshArray()[0].getVertexBuffer(),"Equivalent portrait scalp rebuilt the uploaded geometry");
            } finally {AvatarFirstPerson.end();}
            require(!AvatarFirstPerson.hideHair(wrapper),"Camera hair scope leaked after draw");
            for(TriangleMesh mesh:BAKED.get(world))
                require(!AvatarFirstPerson.hidden(world,mesh)&&AvatarFirstPerson.indexCount(mesh,mesh.getIndexCount())==mesh.getIndexCount(),"Camera body scope leaked after draw");
            report.add(Avatar2Mod.VERSION+","+sex+","+(gpu?"GPU":"CPU")+","+height+","+head+","+free+","+pass+","+p[0]+","+p[1]+","+p[2]+","+masked+","+worldDraws+",1,true");
        }
    }

    private static ColladaModelData data(ColladaModel model,File file,String sex)throws Exception {
        ColladaModelData data=new ColladaModelData(new FakeResourceUrl(new URL("file:avatar2/body/"+sex+"-bodybuilder.dae")),model);
        List<TriangleMesh> baked=new ArrayList<>();for(TriangleMesh mesh:AvatarGeometryPrototype.sources(model)){TriangleMesh b=data.bakeDefaultMesh(mesh);data.addBakedMeshToList(b,null);baked.add(b);}BAKED.put(data,baked);return data;
    }
    private static void sample(ColladaModelData data,String name,float phase)throws Exception {
        ColladaModel model=data.getModel();model.getCurrentAnimations().clear();Arrays.fill(model.getBodyAnimation(),null);Arrays.fill(model.getLegsAnimation(),null);model.setBodyBlendingFraction(1);model.setLegsBlendingFraction(1);
        ColladaAnimation clip=model.getColladaAnimation(name);clip.addToModel();clip.resetBlending();clip.apply(phase);clip.setCurrentFraction(phase);
        worker.invoke(new ColladaAnimationJob(),data,model,!AvatarWalkMotion.isIdle(name),false,true,TriangleMesh.LODLevel.NONE);
    }
    private static void gpu(ColladaModelData data,boolean value)throws Exception {
        Field gpu=TriangleMesh.class.getDeclaredField("hasGPUSkinning"),material=TriangleMesh.class.getDeclaredField("materialInstance");gpu.setAccessible(true);material.setAccessible(true);
        Constructor<?> empty=MaterialInstance.class.getDeclaredConstructor();empty.setAccessible(true);
        List<TriangleMesh> meshes=new ArrayList<>(AvatarGeometryPrototype.sources(data.getModel()));meshes.addAll(BAKED.get(data));
        for(TriangleMesh mesh:meshes){gpu.setBoolean(mesh,value);if(value)material.set(mesh,empty.newInstance());}
    }
    private static ModelResourceWrapper wrapper(ColladaModelData data)throws Exception {Constructor<ModelResourceWrapper> c=ModelResourceWrapper.class.getDeclaredConstructor(ResourceUrl.class,AbstractModelData.class);c.setAccessible(true);return c.newInstance(data.getUrl(),data);}
    private static PlayerBodyRenderable body(ModelResourceWrapper wrapper,ModelResourceWrapper paper,boolean male)throws Exception {
        PlayerBodyRenderable b=(PlayerBodyRenderable)unsafe.allocateInstance(PlayerBodyRenderable.class);set(b,PlayerBodyRenderable.class,"playerModel",wrapper);set(b,PlayerBodyRenderable.class,"isMale",male);
        Field f=PlayerBodyRenderable.class.getDeclaredField("paperDoll");f.setAccessible(true);Object doll=unsafe.allocateInstance(f.getType());set(doll,f.getType(),"paperDollModel",paper);f.set(b,doll);
        set(b,PlayerBodyRenderable.class,"playerFace",new PlayerFace());return b;
    }
    private static PlayerFace face(PlayerBodyRenderable b)throws Exception {Field f=PlayerBodyRenderable.class.getDeclaredField("playerFace");f.setAccessible(true);return (PlayerFace)f.get(b);}
    private static void set(Object o,Class<?> type,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static void finitePlacement(ColladaModel hair,Matrix4f frame) {
        require(frame.determinant()>0&&Float.isFinite(frame.determinant()),"Hair fit lost orientation");
        for(ColladaGeometry g:hair.getGeometryArray())for(TriangleMesh mesh:g.getTriangleMeshArray())for(int v=0;v<mesh.getVertexCount();v++) {
            Vector3f p=frame.mult(new Vector3f(mesh.getVertexBuffer().get(v*3),mesh.getVertexBuffer().get(v*3+1),mesh.getVertexBuffer().get(v*3+2)));
            require(Float.isFinite(p.x)&&Float.isFinite(p.y)&&Float.isFinite(p.z)&&p.y>.8f&&p.y<2.2f,"Hair placement invalid");
        }
    }
    private static void verifyScalp(TriangleMesh mesh,AvatarScalp expected,Matrix4f fit) {
        require(mesh.getVertexCount()*3==expected.vertices.length&&mesh.getIndexCount()==expected.indices.length,"Scalp topology mismatch");
        float u0=Float.POSITIVE_INFINITY,u1=-u0,v0=u0,v1=-u0;
        for(int i=0;i<mesh.getVertexCount();i++) {
            Vector3f p=fit.mult(new Vector3f(mesh.getVertexBuffer().get(i*3),mesh.getVertexBuffer().get(i*3+1),mesh.getVertexBuffer().get(i*3+2)));
            require(Math.abs(p.x-expected.vertices[i*3])<.000005f&&Math.abs(p.y-expected.vertices[i*3+1])<.000005f&&Math.abs(p.z-expected.vertices[i*3+2])<.000005f,"Scalp does not follow changed head geometry");
            Vector3f n=new Vector3f(mesh.getNormalBuffer().get(i*3)/fit.m00,mesh.getNormalBuffer().get(i*3+1)/fit.m11,mesh.getNormalBuffer().get(i*3+2)/fit.m22).normalize();
            require(Math.abs(n.x-expected.normals[i*3])<.00002f&&Math.abs(n.y-expected.normals[i*3+1])<.00002f&&Math.abs(n.z-expected.normals[i*3+2])<.00002f,"Scalp normal changed orientation");
            float u=mesh.getTextureCoordBuffer(0).get(i*2),v=mesh.getTextureCoordBuffer(0).get(i*2+1);u0=Math.min(u0,u);u1=Math.max(u1,u);v0=Math.min(v0,v);v1=Math.max(v1,v);
        }
        require(u1-u0>.1f&&v1-v0>.1f,"Scalp still samples one flat texel");
    }
    private static void exportHair(File file,ColladaModel hair,Matrix4f frame)throws Exception {
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){int base=1;for(ColladaGeometry g:hair.getGeometryArray())for(TriangleMesh mesh:g.getTriangleMeshArray()){
            for(int v=0;v<mesh.getVertexCount();v++){Vector3f p=frame.mult(new Vector3f(mesh.getVertexBuffer().get(v*3),mesh.getVertexBuffer().get(v*3+1),mesh.getVertexBuffer().get(v*3+2)));out.println("v "+p.x+" "+-p.z+" "+p.y);}
            Matrix4f inverse=frame.invert().transpose();for(int v=0;v<mesh.getVertexCount();v++){Vector3f n=inverse.mult(new Vector3f(mesh.getNormalBuffer().get(v*3),mesh.getNormalBuffer().get(v*3+1),mesh.getNormalBuffer().get(v*3+2))).normalize();out.println("vn "+n.x+" "+-n.z+" "+n.y);}
            java.nio.FloatBuffer uv=mesh.getTextureCoordBuffer(0);
            for(int v=0;v<mesh.getVertexCount();v++)out.println("vt "+uv.get(v*2)+" "+uv.get(v*2+1));
            for(int i=0;i<mesh.getIndexCount();i+=3){out.print("f");for(int j=0;j<3;j++){int v=base+mesh.getIndexBuffer().get(i+j);out.print(" "+v+"/"+v+"/"+v);}out.println();}base+=mesh.getVertexCount();}}
    }
    private static boolean equal(Matrix4f a,Matrix4f b)throws Exception {float[] aa=new float[16],bb=new float[16];a.get(aa,true);b.get(bb,true);for(int i=0;i<16;i++)if(Math.abs(aa[i]-bb[i])>.00002f)return false;return true;}
    private static void same(Matrix4f a,Matrix4f b,String message)throws Exception {require(equal(a,b),message);}
    private static void same(Matrix a,Matrix b,String message){float[] aa=new float[16],bb=new float[16];a.get(aa);b.get(bb);for(int i=0;i<16;i++)require(Math.abs(aa[i]-bb[i])<.00003f,message+" #"+i+" "+aa[i]+" / "+bb[i]);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
