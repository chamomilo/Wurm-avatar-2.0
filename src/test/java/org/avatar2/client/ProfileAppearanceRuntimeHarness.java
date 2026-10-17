package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.*;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.*;
import org.avatar2.client.*;
import org.avatar2.lab.AnimationLab;
import org.avatar2.core.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Persist/reload through the real repository, then native source/baked animation and skinning. */
public final class ProfileAppearanceRuntimeHarness {
    private static final Map<ColladaModelData,List<TriangleMesh>> BAKED=new IdentityHashMap<>();
    public static void run(String root) throws Exception {
        File folder=new File(root,"build/reports/avatar2/profile");folder.mkdirs();
        AvatarProfileRepository repo=new AvatarProfileRepository(new File(folder,"fixture.properties"));
        int poses=0;
        try {
            AnimationLab.stop();
            for(AvatarProfile.Sex sex:AvatarProfile.Sex.values()) {
                String name=sex.name().toLowerCase(Locale.ROOT)+"-bodybuilder";
                File file=new File(root,"assets/runtime/body/"+name+".dae");
                ColladaModel template=GeometryRuntimeHarness.load(file);
                AvatarAnimationBridge.normalizeSkinWeights(template);
                try(InputStream in=new FileInputStream(new File(root,"assets/runtime/morph/"+name+".avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(template,MorphBank.read(in));
                }
                AvatarWalkMotion.register(template);
                AvatarAgeSkin.register(template,new AgeSkinFixtureUrl(new File(root),"avatar2/body/"+name+".dae"));
                near(template.calculateBoundingBox().getH1()-template.calculateBoundingBox().getH0(),AvatarProfile.nativeHeightMetres(sex),"Studio native height matches imported binding");
                float[] baseline=body(template).getOriginalVertexBuffer().clone();
                ColladaModel world=new ColladaModel(template),paper=new ColladaModel(template),remote=new ColladaModel(template);
                AnimationLab.registerLocalModel(world);AnimationLab.registerPaperModel(paper);
                ColladaModelData worldData=data(world,file,false),paperData=data(paper,file,true);
                TriangleMesh baked=BAKED.get(paperData).get(0);
                if(sex==AvatarProfile.Sex.MALE) {
                    AvatarProfile pending=new AvatarProfile();pending.sex=AvatarProfile.Sex.FEMALE;
                    pending.shape="proportions";pending.shapeMix=1;pending.buildBreast=.75f;AvatarRuntimeState.apply(pending);
                    sample(worldData,"idle",0);sample(paperData,"idle",0);
                    check(Arrays.equals(baseline,body(world).getOriginalVertexBuffer())&&Arrays.equals(baseline,body(paper).getOriginalVertexBuffer()),"Pending female profile changed the old male binding");
                    System.out.println("AVATAR2_SEX_SWITCH_BREAST_OK pending female profile preserves previous male world/paper binding");
                    AvatarProfile restore=new AvatarProfile();restore.sex=sex;AvatarRuntimeState.apply(restore);
                }
                int shoulderStart=sex==AvatarProfile.Sex.FEMALE?23:13;
                for(int scenario=0;scenario<shoulderStart+10;scenario++) for(float height:new float[]{1.3f,2}) {
                    AvatarProfile p=new AvatarProfile();p.sex=sex;p.shape=scenario<3?new String[]{"mpfb-athletic","mpfb-heavy","mpfb-older"}[scenario]:"blend";
                    p.shapeMix=scenario==7?1:.5f;p.heightMetres=height;p.ageSkin="old";
                    if(scenario>=3&&scenario<8) {
                        float[][] shares={{.5f,.5f,0},{.25f,.25f,.25f},{1,1,1},{1,0,1},{0,1,1}};
                        p.blendAthletic=shares[scenario-3][0];p.blendHeavy=shares[scenario-3][1];p.blendOlder=shares[scenario-3][2];
                    }
                    if(scenario>=8&&scenario<13) {
                        float[][] build={{0,0},{1,1},{0,1},{.5f,.5f},{.27123457f,.6312345f}};
                        p.shape="proportions";p.shapeMix=1;p.buildMuscle=build[scenario-8][0];p.buildFat=build[scenario-8][1];
                    }
                    if(scenario>=13 && scenario<shoulderStart) {
                        float[][] values={{1,0,0},{0,0,0},{1,1,0},{0,1,0},{1,0,1},{0,0,1},{1,1,1},{0,1,1},{.5f,.5f,.25f},{.5f,.5f,.75f}};
                        p.shape="proportions";p.shapeMix=1;p.buildMuscle=values[scenario-13][0];p.buildFat=values[scenario-13][1];p.buildBreast=values[scenario-13][2];
                    }
                    if(scenario>=shoulderStart && scenario<shoulderStart+4) {
                        int t=scenario-shoulderStart;p.shape="proportions";p.shapeMix=1;p.buildShoulders=t%2==0?0:1;
                        p.buildMuscle=t<2?1:.5f;p.buildFat=t<2?0:.75f;p.buildBreast=sex==AvatarProfile.Sex.FEMALE&&t>=2?.75f:.5f;
                    }
                    if(scenario>=shoulderStart+4 && scenario<shoulderStart+6) {p.shape="proportions";p.shapeMix=1;p.headSize=scenario==shoulderStart+4?0:1;p.buildShoulders=1;p.buildFat=1;}
                    if(scenario>=shoulderStart+6) {
                        int t=scenario-shoulderStart-6;p.shape="proportions";p.shapeMix=1;
                        p.agePreset=t<2?"middle":"old";p.buildMuscle=t%2;p.buildFat=1;
                        p.buildBreast=sex==AvatarProfile.Sex.FEMALE?t%2:.5f;p.buildShoulders=t%2;p.headSize=t%2;
                    }
                    repo.save(p);AvatarRuntimeState.apply(repo.load());
                    check(!AnimationLab.isEnabled(),"Persistence must not start Lab");
                    for(boolean gpu:new boolean[]{false,true}) {
                        gpu(worldData,gpu);gpu(paperData,gpu);
                        for(String clip:new String[]{"idle","walk","run"}) for(int phase=0;phase<4;phase++) {
                            sample(worldData,clip,phase/4f);sample(paperData,clip,phase/4f);poses+=2;
                            check(Arrays.equals(body(world).getOriginalVertexBuffer(),body(paper).getOriginalVertexBuffer()),"World/paper bind mismatch");
                            check(Arrays.equals(body(world).getOriginalVertexBuffer(),baked.getOriginalVertexBuffer()),"Baked bind mismatch");
                            check(!Arrays.equals(baseline,body(world).getOriginalVertexBuffer()),"Saved shape was not applied");
                            near(AvatarHeightPrototype.scale(world),AvatarHeightPrototype.limits(world).fromMetres(height==1.3f?1.3:2),"World saved height");
                            near(AvatarHeightPrototype.scale(paper),AvatarHeightPrototype.scale(world),"Paper saved height");
                            near(worldData.getBounds().getH1(),paperData.getBounds().getH1(),"World/paper bounds");
                            check(AnimationLab.skinFor(world).equals("old")&&AnimationLab.skinFor(paper).equals("old"),"Saved skin without Lab");
                            check(AnimationLab.skinFor(remote).equals("original"),"Remote copy gained profile skin");
                            check(Arrays.equals(baseline,body(template).getOriginalVertexBuffer())&&Arrays.equals(baseline,body(remote).getOriginalVertexBuffer()),"Profile contaminated shared/remote geometry");
                            for(int i=0;i<baked.getVertexBuffer().capacity();i++)near(body(world).getVertexBuffer().get(i),baked.getVertexBuffer().get(i),"Source/baked skinned output");
                        }
                    }
                }
                AvatarProfile saved=repo.load();byte[] bytes=Files.readAllBytes(repo.file().toPath());
                AnimationLab.start();AnimationLab.studioShape("mpfb-heavy");AnimationLab.studioMix(1);AnimationLab.studioHeight(13);AnimationLab.studioSkin("middle");
                sample(worldData,"walk",.25f);sample(paperData,"walk",.25f);
                check(AnimationLab.skinFor(world).equals("middle")&&AnimationLab.skinFor(paper).equals("old"),"Lab must override world only");
                check(!Arrays.equals(body(world).getOriginalVertexBuffer(),body(paper).getOriginalVertexBuffer()),"Trial leaked to paper");
                AnimationLab.studioReset();sample(worldData,"walk",.25f);
                check(Arrays.equals(body(world).getOriginalVertexBuffer(),body(paper).getOriginalVertexBuffer()),"Reset did not return to saved shape");
                near(AvatarHeightPrototype.scale(world),AvatarHeightPrototype.scale(paper),"Reset saved height");
                check(AnimationLab.skinFor(world).equals("old"),"Reset saved skin");
                AnimationLab.studioSkin("middle");Field contact=AnimationLab.class.getDeclaredField("lastContact");contact.setAccessible(true);contact.setLong(null,System.nanoTime()-31_000_000_000L);
                check(AnimationLab.skinFor(world).equals("old"),"Expired lease must reveal saved skin before animation");
                sample(worldData,"walk",.5f);AnimationLab.stop();sample(worldData,"walk",.5f);
                check(Arrays.equals(bytes,Files.readAllBytes(repo.file().toPath())),"Trials wrote saved profile");
                verifyStudioSave(worldData,paperData,repo);
                AvatarProfile neutral=new AvatarProfile();neutral.sex=sex;AvatarRuntimeState.apply(neutral);
                sample(worldData,"idle",0);sample(paperData,"idle",0);
                check(Arrays.equals(baseline,body(world).getOriginalVertexBuffer())&&Arrays.equals(baseline,body(paper).getOriginalVertexBuffer()),"Neutral rollback geometry");
                near(AvatarHeightPrototype.scale(world),1,"Neutral rollback height");
                check(AnimationLab.skinFor(world).equals("original"),"Neutral rollback skin");
                AvatarRuntimeState.apply(saved);sample(worldData,"idle",0);sample(paperData,"idle",0);
                saved.paperDollUsesAvatar=false;AvatarRuntimeState.apply(saved);sample(paperData,"idle",0);
                check(Arrays.equals(baseline,body(paper).getOriginalVertexBuffer())&&AnimationLab.skinFor(paper).equals("original"),"Paper profile gate rollback");
                near(AvatarHeightPrototype.scale(paper),1,"Paper gate height rollback");
                AnimationLab.registerLocalModel(null);
            }
            System.out.println("AVATAR2_PROFILE_OK schema 7 save/reload, "+poses+" native CPU/GPU source/baked poses, both sexes, shapes/blends/builds/breast/shoulders/head, height endpoints, world/paper equivalence, template/remote isolation, Lab reset/off/expiry and neutral rollback");
            Files.write(Paths.get(root,"build/reports/avatar2/profile-appearance-runtime.txt"),
                    Arrays.asList("Avatar "+Avatar2Mod.VERSION,"native CPU/GPU source/baked poses="+poses,
                            "PASS: both sexes, three authored shapes and five blends, height endpoints, save/reload, source/baked equivalence",
                            "PASS: world/paper profile, template/remote isolation, Lab reset/off/expiry, neutral rollback",
                            "PASS: explicit Studio Lab save retains geometry, height, skin and temporary motion; reload/reset keep appearance",
                            "Installed visual restart/paper-doll review pending."),java.nio.charset.StandardCharsets.UTF_8);
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);AvatarRuntimeState.apply(new AvatarProfile());BAKED.clear();}
    }
    private static void verifyStudioSave(ColladaModelData world,ColladaModelData paper,AvatarProfileRepository repo) throws Exception {
        AvatarStudioController studio=new AvatarStudioController(null,repo);studio.initialize();
        AnimationLab.start();AnimationLab.studioShape("mpfb-heavy");AnimationLab.studioMix(.37123457f);
        AnimationLab.studioShape("blend");AnimationLab.studioContribution("Athletic",.25f);AnimationLab.studioContribution("Older",.5f);
        AnimationLab.studioHeight(14.654321f);AnimationLab.studioSkin("middle");AnimationLab.studioMotion("run");
        AnimationLab.studioHeadSize(.27123455f);
        sample(world,"run",.25f);
        float[] before=body(world.getModel()).getOriginalVertexBuffer().clone();
        float scale=AvatarHeightPrototype.scale(world.getModel());
        studio.perform("saveTestedBody");
        check(studio.status().startsWith("Saved and applied"),"Studio Lab save failed: "+studio.status());
        sample(world,"run",.25f);sample(paper,"run",.25f);
        check(Arrays.equals(before,body(world.getModel()).getOriginalVertexBuffer()),"Save changed the tested geometry");
        check(Arrays.equals(before,body(paper.getModel()).getOriginalVertexBuffer()),"Saved Lab appearance did not reach paper");
        near(scale,AvatarHeightPrototype.scale(world.getModel()),"Save changed tested height");
        check(AnimationLab.studioControls(false).clip.equals("run"),"Save reset the temporary motion");
        check(AnimationLab.skinFor(world.getModel()).equals("middle")&&AnimationLab.skinFor(paper.getModel()).equals("middle"),"Save changed selected skin");
        AvatarProfile saved=repo.load();check(saved.geometry().equals(AnimationLab.studioProfile().geometry())
                &&saved.shape.equals("blend")&&saved.shapeMix==1&&saved.blendOlder==.5f,"Lab slider persistence");
        check(saved.headSize==.27123455f,"Lab head Save lost exact value");
        AnimationLab.studioReset();sample(world,"run",.25f);
        check(Arrays.equals(before,body(world.getModel()).getOriginalVertexBuffer()),"Reset after Save changed shape");
        AnimationLab.stop();AvatarRuntimeState.apply(saved);sample(world,"run",.25f);sample(paper,"run",.25f);
        check(Arrays.equals(before,body(world.getModel()).getOriginalVertexBuffer()),"Reload changed the selected shape");
        near(scale,AvatarHeightPrototype.scale(world.getModel()),"Reload changed selected height");
        if(saved.sex==AvatarProfile.Sex.FEMALE) {
            AnimationLab.start();AnimationLab.studioShape("proportions");AnimationLab.studioBuild("Breast",.73123455f);
            AnimationLab.studioBuild("Fat",.75f);AnimationLab.studioBuild("Shoulders",.25f);sample(world,"walk",.25f);
            before=body(world.getModel()).getOriginalVertexBuffer().clone();studio.perform("saveTestedBody");
            sample(world,"walk",.25f);sample(paper,"walk",.25f);
            check(Arrays.equals(before,body(world.getModel()).getOriginalVertexBuffer())&&Arrays.equals(before,body(paper.getModel()).getOriginalVertexBuffer()),"Breast Lab Save changed appearance");
            check(repo.load().buildBreast==.73123455f,"Breast Lab Save lost exact value");
            check(repo.load().buildShoulders==.25f,"Shoulders Lab Save lost exact value");
            AnimationLab.studioReset();AnimationLab.stop();AvatarRuntimeState.apply(repo.load());sample(world,"walk",.25f);
            check(Arrays.equals(before,body(world.getModel()).getOriginalVertexBuffer()),"Breast reset/off/reload changed saved appearance");
        }
    }
    private static ColladaModelData data(ColladaModel model,File file,boolean bake) throws Exception {
        ColladaModelData d=new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),model);d.setSpecificMeshesToDraw(null);
        List<TriangleMesh> meshes=new ArrayList<>();
        if(bake)for(TriangleMesh mesh:AvatarGeometryPrototype.sources(model)){TriangleMesh baked=d.bakeDefaultMesh(mesh);d.addBakedMeshToList(baked,null);meshes.add(baked);}
        BAKED.put(d,meshes);
        return d;
    }
    private static void gpu(ColladaModelData d,boolean enabled) throws Exception {
        Field gpu=TriangleMesh.class.getDeclaredField("hasGPUSkinning");gpu.setAccessible(true);
        Field material=TriangleMesh.class.getDeclaredField("materialInstance");material.setAccessible(true);
        java.lang.reflect.Constructor<?> empty=com.wurmonline.client.renderer.MaterialInstance.class.getDeclaredConstructor();empty.setAccessible(true);
        List<TriangleMesh> meshes=new ArrayList<>(AvatarGeometryPrototype.sources(d.getModel()));meshes.addAll(BAKED.get(d));
        for(TriangleMesh mesh:meshes){gpu.setBoolean(mesh,enabled);if(enabled)material.set(mesh,empty.newInstance());}
    }
    private static void sample(ColladaModelData d,String clip,float phase) {
        ColladaModel m=d.getModel();AnimationLab.beginFrame(m);
        try {ForwardKinematics fk=new ForwardKinematics();fk.calculateForwardKinematics(phase,m.getColladaAnimation(clip),false,false,false);
            fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,0,0);
            if(AnimationLab.beforeForwardKinematics(m))fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,0,0);
            AnimationLab.afterForwardKinematics(m);d.calculateSkins(TriangleMesh.LODLevel.NONE);AnimationLab.afterSkinning(m);
        } finally {AnimationLab.endFrame();}
    }
    private static TriangleMesh body(ColladaModel m){return AvatarGeometryPrototype.sources(m).get(0);}
    private static void near(float a,float b,String label){if(!Float.isFinite(a)||Math.abs(a-b)>.00004f)throw new AssertionError(label+": "+a+" != "+b);}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
