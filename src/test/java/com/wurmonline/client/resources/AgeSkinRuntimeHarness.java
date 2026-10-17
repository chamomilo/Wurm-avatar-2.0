package com.wurmonline.client.resources;

import com.wurmonline.client.renderer.model.collada.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.textures.*;
import org.avatar2.client.*;
import org.avatar2.core.AgeSkin;
import org.avatar2.core.AvatarProfile;
import org.avatar2.lab.AnimationLab;
import javax.imageio.ImageIO;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class AgeSkinRuntimeHarness {
    private static Texture drawn;
    private static final Map<String,Integer> uploads = new LinkedHashMap<>();
    private static final List<String> report = new ArrayList<>();
    private static String scenario;
    private static int deferredUploads;
    public static void initializeTexture(ResourceTexture texture) throws Exception {
        check(!texture.isValid()&&texture.needReinit(),"Real native loader returned a deferred GL upload");
        String path=texture.getUrl().getFilePath();uploads.put(path,uploads.getOrDefault(path,0)+1);
        Field id=Texture.class.getDeclaredField("id");id.setAccessible(true);id.setInt(texture,100+uploads.size());
        for(String dimension:new String[]{"width","height"}) {
            Field size=Texture.class.getDeclaredField(dimension);size.setAccessible(true);size.setInt(texture,2048);
        }
        texture.setNeedReinit(false);deferredUploads++;
    }
    private static ResourceTexture texture(ResourceUrl url) throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        ResourceTexture result=(ResourceTexture)unsafe.allocateInstance(ResourceTexture.class);
        Field id=Texture.class.getDeclaredField("id");id.setAccessible(true);id.setInt(result,100+uploads.size());
        for(String dimension:new String[]{"width","height"}) {
            Field size=Texture.class.getDeclaredField(dimension);size.setAccessible(true);size.setInt(result,2048);
        }
        Field path=ResourceTexture.class.getDeclaredField("url");path.setAccessible(true);path.set(result,url);
        return result;
    }
    public static void draw(AbstractColladaModelData data,TriangleMesh mesh,Texture texture,boolean gpu) {
        // Native source draws normally pass null and resolve the material map inside the renderer.
        if(texture==null&&mesh.getColladaMaterial()!=null)texture=data.getLoadedTextures().get(mesh.getColladaMaterial());
        // Native Queue checks this flag and performs the upload on the GL thread.
        if(texture!=null&&texture.needReinit())texture.reinit();
        drawn=texture;
        report.add(Avatar2Mod.VERSION+","+scenario+","+mesh.getName()+","+gpu+","+
                (texture instanceof ResourceTexture?((ResourceTexture)texture).getUrl().getFilePath():"original"));
    }
    public static void run(String root,String packPath) throws Exception {
        report.add("version,scenario,mesh,gpu_entry,texture_path");
        JarPack pack=new JarPack(new File(packPath));
        try {
            AnimationLab.start();
            for(String name:new String[]{"male-bodybuilder","female-bodybuilder"}) {
                ResourceUrl url=new PackResourceUrl(pack,"avatar2/body/"+name+".dae");
                ColladaModel template;
                AvatarAnimationBridge.beginImport(url);
                try(InputStream in=url.openStream()){template=ColladaImporter.loadModel(url.toString(),in);}
                finally{AvatarAnimationBridge.endImport();}
                check(AvatarAnimationBridge.loadIfAvatar(template,url),"Production resource registration");
                check(AvatarAgeSkin.available(template).equals(AgeSkin.PRESETS),"Four adult choices");
                ColladaModel world=new ColladaModel(template),paper=new ColladaModel(template);
                AnimationLab.registerLocalModel(world);
                ColladaModelData data=new ColladaModelData(url,world);
                ResourceTexture original=texture(url.derive(AgeSkin.file(name.startsWith("male")?"male":"female","young")));
                TriangleMesh body=mesh(world,"Avatar2BodyMesh"),eyes=mesh(world,"Avatar2EyesMesh");
                data.getLoadedTextures().put(body.getColladaMaterial(),original);
                data.getLoadedTextures().put(eyes.getColladaMaterial(),original);
                Object material=body.getColladaMaterial();float[] bind=body.getOriginalVertexBuffer().clone();
                float[] normals=body.getOriginalNormalBuffer().clone();java.nio.FloatBuffer uv=body.getTextureCoordBuffer(0);
                int[] indices=indices(body);java.nio.FloatBuffer weights=body.getWeightBuffer();
                TriangleMesh baked=data.bakeDefaultMesh(body),bakedEyes=data.bakeDefaultMesh(eyes);
                data.addBakedMeshToList(baked,original);data.setShouldBeRendered(true);
                List<Texture> bakedTextures=new ArrayList<>(data.getBakedMeshTextures());
                for(String preset:new String[]{"old","middle","young","old","original"}) {
                    AnimationLab.studioSkin(preset);
                    String expected=AgeSkin.file(name.startsWith("male")?"male":"female",preset);
                    for(boolean gpu:new boolean[]{false,true}) {
                        scenario=name+"/"+preset+"/source";invoke(data,body,original,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Source draw skin: "+name+"/"+preset+"/"+gpu
                                +" material="+body.getColladaMaterial().getTextureName()+" request="+AnimationLab.skinFor(world)
                                +" actual="+((ResourceTexture)drawn).getUrl().getFilePath()+" expected="+expected);
                        scenario=name+"/"+preset+"/source-material";invoke(data,body,null,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Native source material lookup skin: "+name+"/"+preset+"/"+gpu);
                        scenario=name+"/"+preset+"/eyes-material";invoke(data,eyes,null,gpu);
                        check(drawn==original,"Source eyes material lookup unchanged");
                        scenario=name+"/"+preset+"/baked";invoke(data,baked,original,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Real singleton baker skin");
                        scenario=name+"/"+preset+"/eyes";invoke(data,eyes,original,gpu);check(drawn==original,"Eyes unchanged");
                        invoke(data,bakedEyes,original,gpu);check(drawn==original,"Baked eyes unchanged");
                        ColladaModelData other=new ColladaModelData(url,paper);
                        scenario=name+"/"+preset+"/paper";invoke(other,mesh(paper,"Avatar2BodyMesh"),original,gpu);
                        check(drawn==original,"Other model remains original");
                        ColladaModelData cached=new ColladaModelData(url,template);
                        scenario=name+"/"+preset+"/template";invoke(cached,mesh(template,"Avatar2BodyMesh"),original,gpu);
                        check(drawn==original,"Cached template remains original");
                    }
                    scenario=name+"/"+preset+"/native-baked-data";
                    data.render(null,null,null,null,null,null,null,null,0,1,TriangleMesh.LODLevel.NONE);
                    check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Native baked-list dispatch");
                    check(data.getBakedMeshTextures().equals(bakedTextures)&&data.getLoadedTextures().size()==2
                            &&data.getLoadedTextures().get(body.getColladaMaterial())==original
                            &&data.getLoadedTextures().get(eyes.getColladaMaterial())==original,"No baked/material-map mutation");
                    check(body.getOverridedTexture()==null&&baked.getOverridedTexture()==null,"No mesh override mutation");
                    check(body.getColladaMaterial()==material&&Arrays.equals(bind,body.getOriginalVertexBuffer())
                            &&Arrays.equals(normals,body.getOriginalNormalBuffer())&&uv==body.getTextureCoordBuffer(0)
                            &&weights==body.getWeightBuffer()&&Arrays.equals(indices,indices(body)),"No geometry/UV/skin mutation");
                }
                AnimationLab.studioSkin("old");
                ColladaModel lateCopy=new ColladaModel(world);ColladaModelData lateData=new ColladaModelData(url,lateCopy);
                scenario=name+"/old/late-copy";
                invoke(lateData,mesh(lateCopy,"Avatar2BodyMesh"),original,false);check(drawn==original,"Copy made during test remains original");
                AnimationLab.studioReset();scenario=name+"/original/reset";
                invoke(data,baked,original,false);check(drawn==original,"Reset rollback");
                AnimationLab.studioSkin("old");Field contact=AnimationLab.class.getDeclaredField("lastContact");contact.setAccessible(true);
                contact.setLong(null,System.nanoTime()-31_000_000_000L);
                scenario=name+"/original/lease-expired";
                invoke(data,baked,original,true);check(drawn==original,"Lease timeout rollback without an animation frame");
                check(AnimationLab.studioControls(true).ageSkin.equals("original"),"Controller reconnect does not revive expired skin");
                scenario=name+"/original/lease-reconnect";
                invoke(data,baked,original,false);check(drawn==original,"Reconnect keeps original until a fresh choice");
                contact.setLong(null,System.nanoTime()-31_000_000_000L);
                scenario=name+"/old/lease-fresh-command";
                AnimationLab.studioSkin("old");invoke(data,baked,original,true);
                check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(AgeSkin.file(name.startsWith("male")?"male":"female","old")),"Fresh explicit skin after expiry applies");
                AnimationLab.studioSkin("old");AnimationLab.stop();scenario=name+"/original/lab-off";
                invoke(data,baked,original,false);check(drawn==original,"Lab off rollback");
                AnimationLab.start();AnimationLab.studioSkin("old");AnimationLab.registerLocalModel(paper);
                scenario=name+"/original/body-change";
                invoke(data,baked,original,false);check(drawn==original,"Body change rollback");
                AnimationLab.stop();AnimationLab.registerLocalModel(world);AnimationLab.registerPaperModel(paper);
                ColladaModelData paperData=new ColladaModelData(url,paper);
                TriangleMesh paperBody=mesh(paper,"Avatar2BodyMesh");paperData.getLoadedTextures().put(paperBody.getColladaMaterial(),original);
                java.lang.reflect.Constructor<com.wurmonline.client.renderer.model.ModelResourceWrapper> wrapperConstructor=
                        com.wurmonline.client.renderer.model.ModelResourceWrapper.class.getDeclaredConstructor(ResourceUrl.class,com.wurmonline.client.renderer.model.AbstractModelData.class);
                wrapperConstructor.setAccessible(true);Object paperWrapper=wrapperConstructor.newInstance(url,paperData);
                Method prepare=Avatar2Mod.class.getDeclaredMethod("prepareAvatarPaperDoll",com.wurmonline.client.renderer.model.ModelResourceWrapper.class);prepare.setAccessible(true);
                check(!(Boolean)prepare.invoke(null,paperWrapper)&&paperData.getBakedMeshTextures().isEmpty(),"Private paper copy waits for all textures without partial bake");
                paperData.getLoadedTextures().put(mesh(paper,"Avatar2EyesMesh").getColladaMaterial(),original);
                check((Boolean)prepare.invoke(null,paperWrapper)&&paperData.getBakedMeshTextures().size()==2,"Private paper copy bakes when its textures complete");
                Field bakedList=ColladaModelData.class.getDeclaredField("bakedMeshes");bakedList.setAccessible(true);
                TriangleMesh paperBaked=((java.util.List<TriangleMesh>)bakedList.get(paperData)).get(0);
                for(String skin:new String[]{"middle","old"}) {
                    AvatarProfile profile=new AvatarProfile();profile.ageSkin=skin;AvatarRuntimeState.apply(profile);
                    for(boolean gpu:new boolean[]{false,true}) {
                        String expected=AgeSkin.file(name.startsWith("male")?"male":"female",skin);
                        scenario=name+"/saved/"+skin+"/world-source";invoke(data,body,null,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Persistent source draw without Lab");
                        scenario=name+"/saved/"+skin+"/world-baked";invoke(data,baked,original,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Persistent baked world draw");
                        scenario=name+"/saved/"+skin+"/paper-source";invoke(paperData,paperBody,null,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Persistent paper source draw");
                        scenario=name+"/saved/"+skin+"/paper-baked";invoke(paperData,paperBaked,original,gpu);
                        check(((ResourceTexture)drawn).getUrl().getFilePath().endsWith(expected),"Persistent paper baked draw");
                        scenario=name+"/saved/"+skin+"/eyes";invoke(data,eyes,null,gpu);check(drawn==original,"Persistent eyes unchanged");
                        scenario=name+"/saved/"+skin+"/template";invoke(new ColladaModelData(url,template),mesh(template,"Avatar2BodyMesh"),original,gpu);
                        check(drawn==original,"Persistent skin does not affect cached templates");
                    }
                }
                AvatarRuntimeState.apply(new AvatarProfile());AnimationLab.registerPaperModel(null);AnimationLab.start();
            }
            check(uploads.size()==4&&uploads.values().stream().allMatch(n->n==1),"Four bounded uploads reused across repeated draws");
            check(deferredUploads==4,"All age skins survive first-use deferred GL upload in CPU/GPU queue construction");
            Path output=Paths.get(root,"build/reports/avatar2/age-skin-runtime.csv");Files.createDirectories(output.getParent());
            Files.write(output,report,java.nio.charset.StandardCharsets.UTF_8);
            Files.write(Paths.get(root,"build/reports/avatar2/age-skin-deferred-runtime.txt"),
                    Arrays.asList("Avatar "+Avatar2Mod.VERSION,"PASS: actual ResourceTextureLoader PNG preparation with Backend.isGLThread=false",
                            "PASS: four id=-1 / needReinit=true textures reach native draw entries and receive one queue upload each",
                            "Deferred uploads="+deferredUploads,"Draw checks="+(report.size()-1),"Actual GL upload/submission replaced."),
                    java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("AVATAR2_AGE_SKIN_OK two canonical JarPack bodies, production registration/copy/draw hooks, CPU/GPU entries, native baker, eyes/template/paper isolation, immutable material/geometry, reset/off/change/lease rollback; four reusable uploads; GL submission/upload replaced");
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);AvatarRuntimeState.apply(new AvatarProfile());pack.close();}
    }
    private static TriangleMesh mesh(ColladaModel model,String prefix) {
        for(ColladaGeometry geometry:model.getGeometryArray())for(TriangleMesh mesh:geometry.getTriangleMeshArray())
            if(mesh.getName().startsWith(prefix))return mesh;
        throw new AssertionError("Missing "+prefix);
    }
    private static int[] indices(TriangleMesh mesh) {java.nio.IntBuffer b=mesh.getIndexBuffer().duplicate();b.rewind();int[] r=new int[b.remaining()];b.get(r);return r;}
    private static void invoke(ColladaModelData data,TriangleMesh mesh,Texture texture,boolean gpu) throws Exception {
        Field renderer=AbstractColladaModelData.class.getDeclaredField("COLLADA_MODEL_RENDER");renderer.setAccessible(true);
        for(Method method:ColladaModelRender.class.getDeclaredMethods())if(method.getName().equals(gpu?"renderGPUSkinningTriangleList":"renderTriangleList")) {
            method.invoke(renderer.get(null),null,null,null,null,null,null,null,data,mesh,texture,null,0);return;
        }
        throw new AssertionError("Missing draw entry");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
