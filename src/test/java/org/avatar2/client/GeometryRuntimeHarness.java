package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.FakeResourceUrl;
import org.avatar2.core.GeometryProbe;
import org.avatar2.core.MorphBank;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;

public final class GeometryRuntimeHarness {
    public static void run(String root) throws Exception {
        List<String> report = new ArrayList<>();
        List<String> fingerReport = new ArrayList<>();
        List<String> surfaceReport = new ArrayList<>();
        surfaceReport.add("version,body,shape,mix,region,edges,dihedral_p99_degrees,limit_degrees");
        fingerReport.add("version,body,shape,cpu_poses,max_finger_displacement_m,limit_m");
        report.add("version,body,mesh,vertices,indices,basis_sha256,authored_shapes,validation");
        for (String name : new String[]{"male-athletic", "male-bodybuilder", "female-athletic", "female-bodybuilder"}) {
            File file = new File(root, "assets/runtime/body/" + name + ".dae");
            ColladaModel template = load(file);
            AvatarAnimationBridge.normalizeSkinWeights(template);
            if (name.endsWith("bodybuilder")) {
                try (InputStream in = new FileInputStream(new File(root, "assets/runtime/morph/" + name + ".avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(template, MorphBank.read(in));
                }
            } else AvatarGeometryPrototype.register(template);
            AvatarWalkMotion.register(template);
            verifyAuthored(template, name, fingerReport, surfaceReport, root);
            ColladaModel world = new ColladaModel(template), paper = new ColladaModel(template);
            TriangleMesh original = body(template), a = body(world), b = body(paper);
            float[] baseline = original.getOriginalVertexBuffer().clone();
            float[] normalBaseline = original.getOriginalNormalBuffer().clone();
            require(a.getOriginalVertexBuffer() != original.getOriginalVertexBuffer(), "Shared original vertices");
            require(a.getOriginalNormalBuffer() != original.getOriginalNormalBuffer(), "Shared original normals");
            require(a.getVertexBuffer() != original.getVertexBuffer(), "Shared NIO vertices");
            require(a.getNormalBuffer() != original.getNormalBuffer(), "Shared NIO normals");
            require(a.getVertexBuffer() != b.getVertexBuffer(), "Shared world/paper buffers");

            ColladaModelData data = new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()), world);
            // Wurm's one-source bake path really clones the mesh; no GL or mock
            // implementation is required. Production also bakes each source separately.
            data.setSpecificMeshesToDraw(null);
            TriangleMesh baked = data.bakeDefaultMesh(a);
            require(baked != null && AvatarGeometryPrototype.isManaged(baked), "Baker lost geometry ownership");
            require(baked.getOriginalVertexBuffer() != a.getOriginalVertexBuffer(), "Baker shared bind arrays");
            baked.slim(); a.optimize(); a.slim();
            require(!a.isSlimmed() && !baked.isSlimmed() && a.isOptimized(), "Compacted mutable geometry was slimmed");
            data.addBakedMeshToList(baked, null);
            AvatarGeometryPrototype.request(world, new GeometryProbe(1, -.75f));
            ForwardKinematics fk = new ForwardKinematics();
            fk.calculateForwardKinematics(.25f, world.getColladaAnimation("walk"), false, false, false);
            fk.updateBoneTransformations(world, world.getJoint("Root"), null, false, 0, 0);
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            float[] first = a.getOriginalVertexBuffer().clone();
            require(!Arrays.equals(baseline, first), "Probe did not change source geometry");
            same(first, baked.getOriginalVertexBuffer(), 0, "Baked source mismatch");
            same(baseline, b.getOriginalVertexBuffer(), 0, "Paper doll changed with world");
            same(baseline, original.getOriginalVertexBuffer(), 0, "Cached template was mutated");
            same(normalBaseline, original.getOriginalNormalBuffer(), 0, "Template normals were mutated");
            require(data.getBounds() != null && data.getPickingBounds() != null, "Probe bounds were not rebuilt");
            require(data.getBounds().getH1() >= 1, "Invalid probe render height");
            for (String clip : new String[]{"idle", "walk", "run"}) {
                for (int phase = 0; phase < 12; phase++) {
                    fk.calculateForwardKinematics(phase/12f, world.getColladaAnimation(clip), false, false, false);
                    fk.updateBoneTransformations(world, world.getJoint("Root"), null, false, 0, 0);
                    data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    same(first, a.getOriginalVertexBuffer(), 0, "Probe accumulated on repeated frames");
                    verifyCpuSkin(baked);
                }
            }
            Number builds = (Number)AvatarGeometryPrototype.describe(world).get(0).get("builds");
            require(builds.intValue() == 1, "Unchanged frames rebuild the geometry");
            ColladaModel secondGeneration = new ColladaModel(world);
            TriangleMesh secondMesh = body(secondGeneration);
            AvatarGeometryPrototype.beforeSkins(secondGeneration);
            same(baseline, secondMesh.getOriginalVertexBuffer(), 0, "New model copy retained the temporary world probe");
            AvatarGeometryPrototype.request(secondGeneration, new GeometryProbe(-1, .5f));
            AvatarGeometryPrototype.beforeSkins(secondGeneration);
            same(first, a.getOriginalVertexBuffer(), 0, "Second generation changed parent");
            AvatarGeometryPrototype.request(secondGeneration, GeometryProbe.NEUTRAL);
            AvatarGeometryPrototype.beforeSkins(secondGeneration);
            same(baseline, secondMesh.getOriginalVertexBuffer(), 0, "Copied modified mesh lost its neutral basis");
            AvatarGeometryPrototype.request(world, GeometryProbe.NEUTRAL);
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(baseline, a.getOriginalVertexBuffer(), 0, "Reset source failed");
            same(baseline, baked.getOriginalVertexBuffer(), 0, "Reset baked failed");
            same(normalBaseline, baked.getOriginalNormalBuffer(), 0, "Reset baked normals failed");

            // Exercise the GPU branch's INPUT contract without pretending to
            // render a shader. A real material/GL upload needs the live client.
            java.lang.reflect.Field gpuFlag = TriangleMesh.class.getDeclaredField("hasGPUSkinning");
            gpuFlag.setAccessible(true); gpuFlag.setBoolean(baked, true);
            AvatarGeometryPrototype.request(world, new GeometryProbe(-.5f, 1));
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(baked.getOriginalVertexBuffer(), values(baked.getVertexBuffer()), 0, "GPU branch lost bind vertices");
            same(baked.getOriginalNormalBuffer(), values(baked.getNormalBuffer()), 0, "GPU branch lost bind normals");
            AvatarGeometryPrototype.request(world, GeometryProbe.NEUTRAL);
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(baseline, values(baked.getVertexBuffer()), 0, "GPU input reset failed");
            gpuFlag.setBoolean(baked, false);
            data.calculateSkins(TriangleMesh.LODLevel.NONE); verifyCpuSkin(baked);

            // Ordinary resources with the same mesh names must retain native behavior.
            ColladaModel unmanaged = load(file, false), copy = new ColladaModel(unmanaged);
            require(!AvatarGeometryPrototype.isManaged(body(copy)), "Unregistered resource was modified");
            require(body(copy).getOriginalVertexBuffer() == body(unmanaged).getOriginalVertexBuffer(), "Native sharing changed");
            String fingerprint = (String)AvatarGeometryPrototype.describe(template).get(0).get("basisSha256");
            System.out.println("GEOMETRY_BASIS " + name + " " + fingerprint);
            for (Map<String,Object> row : AvatarGeometryPrototype.describe(template))
                report.add(Avatar2Mod.VERSION + "," + name + "," + row.get("name") + "," + row.get("vertices")
                        + "," + row.get("indices") + "," + row.get("basisSha256")
                        + "," + String.join("|", AvatarGeometryPrototype.availableShapes(template)) + ",offline_cpu_baker_gpu_input");
        }
        File output = new File(root, "build/reports/avatar2/geometry-runtime.csv");
        java.nio.file.Files.createDirectories(output.getParentFile().toPath());
        java.nio.file.Files.write(output.toPath(), report, java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(new File(output.getParentFile(), "morph-finger-runtime.csv").toPath(),
                fingerReport, java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(new File(output.getParentFile(), "morph-surface-runtime.csv").toPath(),
                surfaceReport, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void verifyAuthored(ColladaModel template, String name, List<String> report, List<String> surfaces, String root) throws Exception {
        Set<String> shapes = AvatarGeometryPrototype.availableShapes(template);
        if (!name.endsWith("bodybuilder")) {
            require(shapes.equals(Collections.singleton("none")), "MPFB bank matched a different topology");
            try {
                AvatarGeometryPrototype.request(template, new GeometryProbe(0, 0, "mpfb-heavy", 1));
                throw new AssertionError("Wrong topology accepted an authored morph");
            } catch (IllegalArgumentException expected) { }
            return;
        }
        require(shapes.containsAll(GeometryProbe.SHAPES), "Approved imported fingerprints did not match all MPFB shapes: " + shapes);
        ColladaModel world = new ColladaModel(template), paper = new ColladaModel(template);
        TriangleMesh source = body(world), neutral = body(template);
        TriangleMesh eyeSource = eyes(world);
        float[] base = neutral.getOriginalVertexBuffer().clone(), baseNormals = neutral.getOriginalNormalBuffer().clone();
        float[] eyeBase = eyes(template).getOriginalVertexBuffer().clone(), eyeNormals = eyes(template).getOriginalNormalBuffer().clone();
        ColladaModelData data = new ColladaModelData(new FakeResourceUrl(new java.net.URL("file:avatar2/body/" + name + ".dae")), world);
        data.setSpecificMeshesToDraw(null);
        TriangleMesh baked = data.bakeDefaultMesh(source);
        data.addBakedMeshToList(baked, null);
        TriangleMesh bakedEyes = data.bakeDefaultMesh(eyeSource);
        data.addBakedMeshToList(bakedEyes, null);
        ColladaModel reference = new ColladaModel(template);
        TriangleMesh referenceBody = body(reference);
        ColladaModelData referenceData = new ColladaModelData(new FakeResourceUrl(new java.net.URL("file:avatar2/body/" + name + ".dae")), reference);
        referenceData.setSpecificMeshesToDraw(null);
        TriangleMesh referenceBaked = referenceData.bakeDefaultMesh(referenceBody);
        referenceData.addBakedMeshToList(referenceBaked, null);
        ForwardKinematics fk = new ForwardKinematics();
        ForwardKinematics referenceFk = new ForwardKinematics();
        for (String shape : shapes) {
            if (shape.equals("none") || shape.equals("blend") || shape.equals("proportions")) continue;
            AvatarGeometryPrototype.request(world, new GeometryProbe(0, 0, shape, 1));
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            float[] endpoint = source.getOriginalVertexBuffer().clone();
            verifySurface(source, base, name, shape, 1, surfaces);
            require(!Arrays.equals(endpoint, base), "Authored shape is empty: " + shape);
            if(shape.equals("mpfb-athletic")||shape.equals("mpfb-older")) {
                // Measured on 0.8.0's exact native optimized body binding. Guard
                // the requested endpoint contrast rather than only nonzero data.
                boolean male=name.startsWith("male");
                double previous=shape.equals("mpfb-athletic")?(male?.003019837469:.002726242123):(male?.010066816423:.009327238341);
                double sum=0;for(int i=0;i<base.length;i++){double delta=endpoint[i]-base[i];sum+=delta*delta;}
                double rms=Math.sqrt(sum/(base.length/3)),ratio=rms/previous;
                require(ratio>(shape.equals("mpfb-athletic")?2.0:1.4),"Authored endpoint lost stronger contrast: "+name+" "+shape+" ratio="+ratio);
                System.out.println("AVATAR2_SHAPE_CONTRAST "+name+" "+shape+" rms_m="+rms+" vs_080="+ratio);
            }
            same(endpoint, baked.getOriginalVertexBuffer(), 0, "Baked authored shape mismatch");
            same(eyeSource.getOriginalVertexBuffer(), bakedEyes.getOriginalVertexBuffer(), 0, "Baked authored eyes mismatch");
            same(eyeBase, eyes(paper).getOriginalVertexBuffer(), 0, "Authored eyes leaked to paper doll");
            same(base, body(paper).getOriginalVertexBuffer(), 0, "Authored shape leaked to paper doll");
            same(base, neutral.getOriginalVertexBuffer(), 0, "Authored shape mutated the template");
            float maxFingerDisplacement = 0;
            for (String clip : new String[]{"idle", "walk", "run"}) for (int phase = 0; phase < 12; phase++) {
                fk.calculateForwardKinematics(phase/12f, world.getColladaAnimation(clip), false, false, false);
                fk.updateBoneTransformations(world, world.getJoint("Root"), null, false, 0, 0);
                data.calculateSkins(TriangleMesh.LODLevel.NONE); verifyCpuSkin(baked); verifyCpuSkin(bakedEyes);
                referenceFk.calculateForwardKinematics(phase/12f, reference.getColladaAnimation(clip), false, false, false);
                referenceFk.updateBoneTransformations(reference, reference.getJoint("Root"), null, false, 0, 0);
                referenceData.calculateSkins(TriangleMesh.LODLevel.NONE);
                maxFingerDisplacement = Math.max(maxFingerDisplacement,
                        verifyFingerPlacement(baked, referenceBaked, name + " " + shape + " " + clip + " " + phase));
                if (clip.equals("walk") && phase == 3) {
                    writePose(new File(root, "build/geometry-poses/" + name + "-" + shape.substring(5) + ".obj"), baked, bakedEyes);
                    writePose(new File(root, "build/geometry-poses/" + name + "-neutral.obj"), referenceBaked);
                }
                same(endpoint, source.getOriginalVertexBuffer(), 0, "Authored geometry accumulated between phases");
            }
            System.out.println("AVATAR2_FINGER_PLACEMENT " + name + " " + shape + " max_delta_m=" + maxFingerDisplacement);
            report.add(Avatar2Mod.VERSION + "," + name + "," + shape + ",36," + maxFingerDisplacement + ",0.015");
            for(float mix:new float[]{.25f,.5f,.75f}) {
                AvatarGeometryPrototype.request(world, new GeometryProbe(0, 0, shape, mix));
                data.calculateSkins(TriangleMesh.LODLevel.NONE);
                float[] interpolated = source.getOriginalVertexBuffer();
                for (int i = 0; i < base.length; i++)
                    require(Math.abs(interpolated[i] - (base[i] + (endpoint[i] - base[i])*mix)) < 2e-7,
                            "Authored endpoint interpolation mismatch");
                verifySurface(source, base, name, shape, mix, surfaces);
            }
            float[] middle = source.getOriginalVertexBuffer();
            java.lang.reflect.Field gpu = TriangleMesh.class.getDeclaredField("hasGPUSkinning");
            gpu.setAccessible(true); gpu.setBoolean(baked, true);
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(middle, values(baked.getVertexBuffer()), 0, "GPU authored input mismatch");
            same(source.getOriginalNormalBuffer(), values(baked.getNormalBuffer()), 0, "GPU authored normals mismatch");
            AvatarGeometryPrototype.request(world, GeometryProbe.NEUTRAL);
            data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(base, source.getOriginalVertexBuffer(), 0, "Authored reset failed");
            same(baseNormals, baked.getOriginalNormalBuffer(), 0, "Authored normals reset failed");
            same(eyeBase, eyeSource.getOriginalVertexBuffer(), 0, "Authored eyes reset failed");
            same(eyeNormals, bakedEyes.getOriginalNormalBuffer(), 0, "Authored eye normals reset failed");
            gpu.setBoolean(baked, false); data.calculateSkins(TriangleMesh.LODLevel.NONE); verifyCpuSkin(baked);
        }
        float[][] endpoints=new float[3][];
        String[] names={"mpfb-athletic","mpfb-heavy","mpfb-older"};
        for(int t=0;t<3;t++) {
            AvatarGeometryPrototype.request(world,new GeometryProbe(0,0,names[t],1));data.calculateSkins(TriangleMesh.LODLevel.NONE);
            endpoints[t]=source.getOriginalVertexBuffer().clone();
        }
        float[][] shares={{0,0,0},{1,0,0},{0,1,0},{0,0,1},{.25f,.5f,.25f},{1,1,0},{1,0,1},{0,1,1},{1,1,1}};
        java.lang.reflect.Field gpu=TriangleMesh.class.getDeclaredField("hasGPUSkinning");gpu.setAccessible(true);
        int blendCases=0;
        for(int s=0;s<shares.length;s++)for(float amount:new float[]{.5f,1}) {
            GeometryProbe blend=new GeometryProbe(0,0,"blend",amount,shares[s][0],shares[s][1],shares[s][2]);
            AvatarGeometryPrototype.request(world,blend);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            float[] actual=source.getOriginalVertexBuffer(),weights=blend.weights();
            for(int i=0;i<base.length;i++) {
                double expected=base[i];for(int t=0;t<3;t++)expected+=(endpoints[t][i]-base[i])*weights[t];
                require(Math.abs(actual[i]-expected)<3e-7,"Mixed endpoint interpolation mismatch");
            }
            if(s>0)for(int i=0;i<baseNormals.length;i+=3) {
                float[] n=source.getOriginalNormalBuffer();
                double length=Math.sqrt(n[i]*n[i]+n[i+1]*n[i+1]+n[i+2]*n[i+2]);
                require(Math.abs(length-1)<1e-6,"Mixed normal is not normalized");
            }
            verifySurface(source,base,name,"blend-"+s,weights[0]+weights[1]+weights[2],surfaces);
            same(actual,baked.getOriginalVertexBuffer(),0,"Mixed baked geometry mismatch");verifyCpuSkin(baked);
            gpu.setBoolean(baked,true);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(actual,values(baked.getVertexBuffer()),0,"Mixed GPU input mismatch");
            same(source.getOriginalNormalBuffer(),values(baked.getNormalBuffer()),0,"Mixed GPU normal mismatch");
            gpu.setBoolean(baked,false);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(base,body(paper).getOriginalVertexBuffer(),0,"Mixed shape leaked to a copy");
            same(base,neutral.getOriginalVertexBuffer(),0,"Mixed shape mutated the template");blendCases++;
        }
        AvatarGeometryPrototype.request(world,GeometryProbe.NEUTRAL);data.calculateSkins(TriangleMesh.LODLevel.NONE);
        same(base,source.getOriginalVertexBuffer(),0,"Mixed reset geometry mismatch");
        same(baseNormals,source.getOriginalNormalBuffer(),0,"Mixed reset normal mismatch");
        System.out.println("AVATAR2_BLEND_RUNTIME_OK "+name+" "+blendCases+" combinations; convex output, surface, CPU/baked/GPU input, normals, isolation and reset");
        float[][] builds={{0,0},{1,1},{0,1}};
        float[][] grid=new float[3][];
        for(int t=0;t<3;t++) {
            AvatarGeometryPrototype.request(world,new GeometryProbe(0,0,"proportions",1,0,0,0,builds[t][0],builds[t][1]));
            data.calculateSkins(TriangleMesh.LODLevel.NONE);grid[t]=source.getOriginalVertexBuffer().clone();
            require(!Arrays.equals(grid[t],base),"Empty Build corner");
        }
        double interaction=0;
        for(int i=0;i<base.length;i++) {double delta=grid[2][i]-grid[0][i]-grid[1][i]+base[i];interaction+=delta*delta;}
        require(Math.sqrt(interaction/(base.length/3))>.0001,"Build combination lost its authored interaction");
        int buildCases=0;
        for(float muscle:new float[]{0,.25f,.5f,.75f,1}) for(float fat:new float[]{0,.25f,.5f,.75f,1}) {
            GeometryProbe build=new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat);
            AvatarGeometryPrototype.request(world,build);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            float[] actual=source.getOriginalVertexBuffer(),weights=build.buildWeights();
            for(int i=0;i<base.length;i++) {
                double expected=base[i];for(int t=0;t<3;t++)expected+=(grid[t][i]-base[i])*weights[t];
                require(Math.abs(actual[i]-expected)<3e-7,"Build grid interpolation mismatch");
            }
            same(eyeBase,eyeSource.getOriginalVertexBuffer(),0,"Build displaced the approved eyes");
            same(actual,baked.getOriginalVertexBuffer(),0,"Build baked geometry mismatch");
            verifySurface(source,base,name,"build-"+muscle+"-"+fat,weights[0]+weights[1]+weights[2],surfaces);
            for(int i=0;i<baseNormals.length;i+=3) {
                float[] n=source.getOriginalNormalBuffer();
                require(Math.abs(n[i]*n[i]+n[i+1]*n[i+1]+n[i+2]*n[i+2]-1)<2e-6,"Build normal is not normalized");
            }
            for(String clip:new String[]{"idle","walk","run"}) for(int phase=0;phase<4;phase++) {
                fk.calculateForwardKinematics(phase/4f,world.getColladaAnimation(clip),false,false,false);
                fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);
                data.calculateSkins(TriangleMesh.LODLevel.NONE);verifyCpuSkin(baked);
                referenceFk.calculateForwardKinematics(phase/4f,reference.getColladaAnimation(clip),false,false,false);
                referenceFk.updateBoneTransformations(reference,reference.getJoint("Root"),null,false,0,0);
                referenceData.calculateSkins(TriangleMesh.LODLevel.NONE);
                verifyFingerPlacement(baked,referenceBaked,name+" Build "+muscle+" "+fat+" "+clip+" "+phase);
                if(clip.equals("walk")&&phase==1&&(muscle==0||fat==1))
                    writePose(new File(root,"build/geometry-poses/"+name+"-build-"+muscle+"-"+fat+".obj"),baked,bakedEyes);
            }
            gpu.setBoolean(baked,true);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(actual,values(baked.getVertexBuffer()),0,"Build GPU input mismatch");
            same(source.getOriginalNormalBuffer(),values(baked.getNormalBuffer()),0,"Build GPU normals mismatch");
            gpu.setBoolean(baked,false);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            same(base,neutral.getOriginalVertexBuffer(),0,"Build mutated template");
            same(base,body(paper).getOriginalVertexBuffer(),0,"Build leaked to an independent copy");buildCases++;
        }
        AvatarGeometryPrototype.request(world,GeometryProbe.NEUTRAL);data.calculateSkins(TriangleMesh.LODLevel.NONE);
        same(base,source.getOriginalVertexBuffer(),0,"Build reset geometry mismatch");
        same(baseNormals,source.getOriginalNormalBuffer(),0,"Build reset normal mismatch");
        System.out.println("AVATAR2_BUILD_RUNTIME_OK "+name+" "+buildCases+" grid cases, authored interaction, 300 animated CPU poses, surface, fingers, eyes, baked/GPU input, copies and rollback");
        if(!name.startsWith("female")) {
            require(!AvatarGeometryPrototype.breastSupported(world),"Male body exposed female breast controls");
            try{AvatarGeometryPrototype.request(world,new GeometryProbe(0,0,"proportions",1,0,0,0,1,0,1));
                throw new AssertionError("Male body accepted female breast volume");}catch(IllegalArgumentException expected){ }
        } else {
            require(AvatarGeometryPrototype.breastSupported(world),"Female body lacks breast grid");
            int breastCases=0;float maxOutside=0,maxChange=0;
            for(float muscle:new float[]{0,.25f,.5f,.75f,1})for(float fat:new float[]{0,.25f,.5f,.75f,1}) {
                AvatarGeometryPrototype.request(world,new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat));
                data.calculateSkins(TriangleMesh.LODLevel.NONE);float[] middle=source.getOriginalVertexBuffer().clone();
                float smallFront=0,largeFront=0;
                for(float breast:new float[]{0,.25f,.75f,1}) {
                    GeometryProbe probe=new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat,breast);
                    AvatarGeometryPrototype.request(world,probe);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    float[] actual=source.getOriginalVertexBuffer();double sum=0;
                    for(int i=0;i<base.length;i+=3) {
                        float x=Math.abs(base[i]),y=base[i+1],z=base[i+2];
                        boolean outside=y<=.98f||y>=1.42f||x>=.29f||z<=0;
                        for(int a=0;a<3;a++){float change=Math.abs(actual[i+a]-middle[i+a]);maxChange=Math.max(maxChange,change);
                            sum+=change*change;if(outside){maxOutside=Math.max(maxOutside,change);require(change<3e-7,"Breast moved non-chest surface");}}
                        if(y>1.06f&&y<1.34f&&x<.24f&&z>.04f){if(breast==0)smallFront=Math.max(smallFront,actual[i+2]);
                            if(breast==1)largeFront=Math.max(largeFront,actual[i+2]);}
                    }
                    require(Math.sqrt(sum/(base.length/3))>.0002,"Breast volume has no visible geometric effect");
                    float[] bodyWeights=probe.buildWeights();
                    verifySurface(source,base,name,"breast-"+muscle+"-"+fat+"-"+breast,bodyWeights[0]+bodyWeights[1]+bodyWeights[2],surfaces);
                    same(eyeBase,eyeSource.getOriginalVertexBuffer(),0,"Breast displaced eyes");
                    same(actual,baked.getOriginalVertexBuffer(),0,"Breast baked bind mismatch");
                    float[] normals=source.getOriginalNormalBuffer();for(int i=0;i<normals.length;i+=3)
                        require(Math.abs(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2]-1)<2e-6,"Breast normal not normalized");
                    for(String clip:new String[]{"idle","walk","run"})for(int phase=0;phase<4;phase++) {
                        fk.calculateForwardKinematics(phase/4f,world.getColladaAnimation(clip),false,false,false);
                        fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);
                        data.calculateSkins(TriangleMesh.LODLevel.NONE);verifyCpuSkin(baked);
                        referenceFk.calculateForwardKinematics(phase/4f,reference.getColladaAnimation(clip),false,false,false);
                        referenceFk.updateBoneTransformations(reference,reference.getJoint("Root"),null,false,0,0);
                        referenceData.calculateSkins(TriangleMesh.LODLevel.NONE);
                        verifyFingerPlacement(baked,referenceBaked,name+" breast "+muscle+" "+fat+" "+breast);
                        if(clip.equals("walk")&&phase==1&&muscle==1&&fat==0&&(breast==0||breast==1))
                            writePose(new File(root,"build/geometry-poses/"+name+"-breast-"+breast+".obj"),baked,bakedEyes);
                    }
                    gpu.setBoolean(baked,true);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    same(actual,values(baked.getVertexBuffer()),0,"Breast GPU bind mismatch");
                    same(normals,values(baked.getNormalBuffer()),0,"Breast GPU normal mismatch");
                    gpu.setBoolean(baked,false);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    same(base,neutral.getOriginalVertexBuffer(),0,"Breast mutated template");
                    same(base,body(paper).getOriginalVertexBuffer(),0,"Breast leaked to independent copy");breastCases++;
                }
                require(largeFront-smallFront>.003f,"Breast extremes do not increase local volume");
                AvatarGeometryPrototype.request(world,new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat));
                data.calculateSkins(TriangleMesh.LODLevel.NONE);same(middle,source.getOriginalVertexBuffer(),0,"Breast 50% changed released Build");
            }
            System.out.println("AVATAR2_BREAST_RUNTIME_OK "+breastCases+" new combinations, 1200 animated CPU poses, surface, normal, eyes/fingers, outside_chest="+maxOutside+", max_delta_m="+maxChange+", exact center, baked/GPU input and isolation");
        }
        require(AvatarGeometryPrototype.shouldersSupported(world),"Missing shoulder width bank");
        int shoulderCases=0;float maxShoulder=0;
        for(float muscle:new float[]{0,.5f,1})for(float fat:new float[]{0,.5f,1})
            for(float breast:name.startsWith("female")?new float[]{0,.5f,1}:new float[]{.5f}) {
                GeometryProbe centre=new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat,breast);
                AvatarGeometryPrototype.request(world,centre);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                float[] middle=source.getOriginalVertexBuffer().clone();
                if(muscle==1&&fat==0&&breast==.5f) {
                    fk.calculateForwardKinematics(0,world.getColladaAnimation("idle"),false,false,false);
                    fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    writePose(new File(root,"build/geometry-poses/"+name+"-shoulders-0.5.obj"),baked,bakedEyes);
                }
                for(float shoulders:new float[]{0,.25f,.75f,1}) {
                    GeometryProbe probe=new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat,breast,shoulders);
                    AvatarGeometryPrototype.request(world,probe);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                    float[] actual=source.getOriginalVertexBuffer();
                    float changed=0;
                    for(int i=0;i<actual.length;i+=3) {
                        float delta=(float)Math.sqrt(Math.pow(actual[i]-middle[i],2)+Math.pow(actual[i+1]-middle[i+1],2)+Math.pow(actual[i+2]-middle[i+2],2));
                        changed=Math.max(changed,delta);
                        if(base[i+1]<=.88f||base[i+1]>=1.54f||Math.abs(base[i])<=.10f)
                            same(java.util.Arrays.copyOfRange(middle,i,i+3),java.util.Arrays.copyOfRange(actual,i,i+3),0,"Shoulders leaked outside local envelope");
                    }
                    require(changed>.001f&&changed<=.03001f,"Shoulder range invalid: "+changed);maxShoulder=Math.max(maxShoulder,changed);
                    float[] shoulderWeights=probe.buildWeights();
                    verifySurface(source,base,name,"shoulders-"+muscle+"-"+fat+"-"+breast+"-"+shoulders,shoulderWeights[0]+shoulderWeights[1]+shoulderWeights[2],surfaces);
                    for(String clip:new String[]{"idle","walk","run"})for(int phase=0;phase<4;phase++) {
                        fk.calculateForwardKinematics(phase/4f,world.getColladaAnimation(clip),false,false,false);
                        fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                        fk.calculateForwardKinematics(phase/4f,reference.getColladaAnimation(clip),false,false,false);
                        fk.updateBoneTransformations(reference,reference.getJoint("Root"),null,false,0,0);referenceData.calculateSkins(TriangleMesh.LODLevel.NONE);
                        verifyCpuSkin(baked);verifyFingerPlacement(baked,referenceBaked,name+" shoulders "+shoulders);
                        same(actual,baked.getOriginalVertexBuffer(),0,"Shoulders source/baked mismatch");
                        if(clip.equals("idle")&&phase==0&&muscle==1&&fat==0&&breast==.5f&&(shoulders==0||shoulders==1))
                            writePose(new File(root,"build/geometry-poses/"+name+"-shoulders-"+shoulders+".obj"),baked,bakedEyes);
                    }
                    same(base,body(paper).getOriginalVertexBuffer(),0,"Shoulders leaked to independent model");shoulderCases++;
                }
                AvatarGeometryPrototype.request(world,centre);data.calculateSkins(TriangleMesh.LODLevel.NONE);
                same(middle,source.getOriginalVertexBuffer(),0,"Shoulders 50% failed exact restore");
            }
        System.out.println("AVATAR2_SHOULDERS_RUNTIME_OK "+name+" cases="+shoulderCases+" CPU poses="+(shoulderCases*12)+" max_delta_m="+maxShoulder+" exact envelope/center/reset, source/baked, eyes/grips and isolation");
        AvatarGeometryPrototype.request(world,GeometryProbe.NEUTRAL);data.calculateSkins(TriangleMesh.LODLevel.NONE);
        same(base,source.getOriginalVertexBuffer(),0,"Breast reset failed");
        System.out.println("AVATAR2_MPFB_RUNTIME_OK " + name + " three shapes, endpoint/interpolation, copies, bake, 108 CPU poses and GPU input/reset");
    }

    /** Measure the marked shoulder/hip/calf creases on the actual imported topology.
     * Weld UV splits by their immutable bind position before finding adjacent faces.
     * This catches sharp surface folds that finite skinning/endpoint checks miss.
     */
    private static void verifySurface(TriangleMesh mesh,float[] base,String body,String shape,float mix,List<String> report) {
        int[] indices=new int[mesh.getIndexCount()],weld=new int[base.length/3];
        Map<String,Integer> vertices=new HashMap<>();
        for(int i=0;i<weld.length;i++) {
            String key=Float.floatToIntBits(base[i*3])+":"+Float.floatToIntBits(base[i*3+1])+":"+Float.floatToIntBits(base[i*3+2]);
            Integer first=vertices.get(key);if(first==null){first=i;vertices.put(key,i);}weld[i]=first;
        }
        Map<Long,int[]> edges=new HashMap<>();
        for(int i=0;i<indices.length;i++)indices[i]=mesh.getIndexBuffer().get(i);
        for(int face=0;face<indices.length/3;face++)for(int corner=0;corner<3;corner++) {
            int a=weld[indices[face*3+corner]],b=weld[indices[face*3+(corner+1)%3]];
            if(a==b)continue;long key=((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL);
            int[] adjacent=edges.get(key);
            if(adjacent==null)edges.put(key,new int[]{face,-1,1});
            else {adjacent[1]=face;adjacent[2]++;}
        }
        double[][] normals=faceNormals(mesh.getOriginalVertexBuffer(),indices),baseNormals=faceNormals(base,indices);
        double scale=body.startsWith("male")?1.729108/1.5912297:1;
        for(String region:new String[]{"hip","upperarm","calf"}) {
            List<Double> angles=new ArrayList<>(),neutral=new ArrayList<>();
            for(Map.Entry<Long,int[]> edge:edges.entrySet()) {
                int[] adjacent=edge.getValue();if(adjacent[2]!=2)continue;
                int a=(int)(edge.getKey()>>>32),b=(int)(long)edge.getKey();
                double x=Math.abs((base[a*3]+base[b*3])*.5)/scale,y=(base[a*3+1]+base[b*3+1])*.5/scale,z=(base[a*3+2]+base[b*3+2])*.5/scale;
                boolean selected=region.equals("hip")?y>.80&&y<1.04&&x>.11&&z<.04:
                        region.equals("upperarm")?y>1.04&&y<1.36&&x>.16:y>.18&&y<.68&&x>.065;
                if(selected){angles.add(angle(normals[adjacent[0]],normals[adjacent[1]]));neutral.add(angle(baseNormals[adjacent[0]],baseNormals[adjacent[1]]));}
            }
            require(angles.size()>100,"Missing surface crease sample: "+body+" "+region);
            Collections.sort(angles);Collections.sort(neutral);
            double p99=angles.get((int)Math.ceil(angles.size()*.99)-1),baseline=neutral.get((int)Math.ceil(neutral.size()*.99)-1);
            double endpointLimit=region.equals("hip")?(body.startsWith("male")?27:29):region.equals("upperarm")?(body.startsWith("male")?44:37):34;
            double limit=mix==1?endpointLimit:Math.max(baseline,endpointLimit)+5;
            require(p99<limit,"Surface crease returned: "+body+" "+shape+" "+mix+" "+region+" p99="+p99+" limit="+limit);
            report.add(Avatar2Mod.VERSION+","+body+","+shape+","+mix+","+region+","+angles.size()+","+p99+","+limit);
        }
    }

    private static double[][] faceNormals(float[] vertices,int[] indices) {
        double[][] result=new double[indices.length/3][3];
        for(int face=0;face<result.length;face++) {
            int a=indices[face*3]*3,b=indices[face*3+1]*3,c=indices[face*3+2]*3;
            double x=vertices[b]-vertices[a],y=vertices[b+1]-vertices[a+1],z=vertices[b+2]-vertices[a+2];
            double u=vertices[c]-vertices[a],v=vertices[c+1]-vertices[a+1],w=vertices[c+2]-vertices[a+2];
            double[] n=result[face];n[0]=y*w-z*v;n[1]=z*u-x*w;n[2]=x*v-y*u;
            double length=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
            if(length>1e-10)for(int i=0;i<3;i++)n[i]/=length;
        }
        return result;
    }
    private static double angle(double[] a,double[] b) {
        return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a[0]*b[0]+a[1]*b[1]+a[2]*b[2]))));
    }

    static void writePose(File output, TriangleMesh... meshes) throws IOException {
        java.nio.file.Files.createDirectories(output.getParentFile().toPath());
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), java.nio.charset.StandardCharsets.UTF_8))) {
            int offset = 1;
            for (TriangleMesh mesh : meshes) {
                writer.println("o " + mesh.getName());
                FloatBuffer vertices = mesh.getVertexBuffer();
                // Export the actual native CPU result in Blender's Z-up axes.
                for (int i = 0; i < mesh.getVertexCount(); i++)
                    writer.println("v " + vertices.get(i*3) + " " + -vertices.get(i*3+2) + " " + vertices.get(i*3+1));
                FloatBuffer normals = mesh.getNormalBuffer();
                for (int i = 0; i < mesh.getVertexCount(); i++)
                    writer.println("vn " + normals.get(i*3) + " " + -normals.get(i*3+2) + " " + normals.get(i*3+1));
                for (int i = 0; i < mesh.getIndexCount(); i += 3)
                    writer.println("f " + poseCorner(offset + mesh.getIndexBuffer().get(i)) + " "
                            + poseCorner(offset + mesh.getIndexBuffer().get(i+1)) + " " + poseCorner(offset + mesh.getIndexBuffer().get(i+2)));
                offset += mesh.getVertexCount();
            }
            if (writer.checkError()) throw new IOException("Could not write CPU pose: " + output);
        }
    }
    private static String poseCorner(int index) { return index+"//"+index; }

    private static float verifyFingerPlacement(TriangleMesh mesh, TriangleMesh reference, String label) {
        float maximum = 0;
        int checked = 0;
        for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
            float fingerWeight = 0;
            for (int slot = 0; slot < 4; slot++) {
                String bone = mesh.getBones()[mesh.getBoneBuffer().get(vertex*4 + slot)].getName();
                if (bone.startsWith("thumb_") || bone.startsWith("index_") || bone.startsWith("middle_")
                        || bone.startsWith("ring_") || bone.startsWith("pinky_"))
                    fingerWeight += mesh.getWeightBuffer().get(vertex*4 + slot);
            }
            if (fingerWeight < .5f) continue;
            checked++;
            float distance2 = 0;
            for (int axis = 0; axis < 3; axis++) {
                int index = vertex*3 + axis;
                float delta = mesh.getVertexBuffer().get(index) - reference.getVertexBuffer().get(index);
                distance2 += delta*delta;
            }
            maximum = Math.max(maximum, (float)Math.sqrt(distance2));
        }
        require(checked > 300, "Finger regression did not select the actual hand vertices");
        // These volume/age probes use the fixed adult skeleton. A surface change
        // may move fingers by millimetres, not by an entire finger-bone length.
        // The former raw age bank exceeds this limit in the real native sampler.
        require(maximum < .015f, "Finger drift exceeds 15 mm: " + label + " displacement=" + maximum);
        return maximum;
    }

    public static ColladaModel load(File file) throws Exception {
        return load(file, true);
    }
    private static ColladaModel load(File file, boolean avatarImport) throws Exception {
        ColladaModel model;
        if (avatarImport) AvatarAnimationBridge.beginImport(new FakeResourceUrl(new java.net.URL("file:avatar2/body/" + file.getName())));
        try (InputStream input = new FileInputStream(file)) { model = ColladaImporter.loadModel(file.toURI().toString(), input); }
        finally { AvatarAnimationBridge.endImport(); }
        AvatarAnimationBridge.ensureRoot(model);
        ColladaPropertiesXml.load(model, new FakeResourceUrl(file.toURI().toURL()), false);
        return model;
    }
    private static TriangleMesh body(ColladaModel model) {
        for (ColladaGeometry geometry : model.getGeometryArray())
            for (TriangleMesh mesh : geometry.getTriangleMeshArray())
                if (mesh.getName().startsWith("Avatar2BodyMesh")) return mesh;
        throw new AssertionError("No body");
    }

    private static TriangleMesh eyes(ColladaModel model) {
        for (ColladaGeometry g : model.getGeometryArray()) for (TriangleMesh mesh : g.getTriangleMeshArray())
            if (mesh.getName().startsWith("Avatar2EyesMesh")) return mesh;
        throw new AssertionError("Missing eyes");
    }
    static void verifyCpuSkin(TriangleMesh mesh) {
        require(!mesh.hasGPUSkinning(), "Harness is not using CPU skinning");
        float[] vertices = mesh.getOriginalVertexBuffer();
        FloatBuffer weights = mesh.getWeightBuffer(), actual = mesh.getVertexBuffer();
        for (int vertex = 0; vertex < mesh.getVertexCount(); vertex += 43) {
            com.wurmonline.math.Vector3f point = new com.wurmonline.math.Vector3f(vertices[vertex*3], vertices[vertex*3+1], vertices[vertex*3+2]);
            com.wurmonline.math.Vector3f expected = new com.wurmonline.math.Vector3f();
            float[] bindNormals = mesh.getOriginalNormalBuffer();
            com.wurmonline.math.Vector3f expectedNormal = new com.wurmonline.math.Vector3f();
            for (int slot = 0; slot < 4; slot++) {
                float weight = weights.get(vertex*4 + slot); if (weight <= 0) continue;
                Joint joint = mesh.getBones()[mesh.getBoneBuffer().get(vertex*4 + slot)];
                com.wurmonline.math.Vector3f transformed = joint.getBindMatrix().mult(joint.getOriginalInvertBindMatrix()).mult(point);
                expected.x += transformed.x*weight; expected.y += transformed.y*weight; expected.z += transformed.z*weight;
                com.wurmonline.client.renderer.model.collada.math.Matrix4f skin = joint.getBindMatrix().mult(joint.getOriginalInvertBindMatrix());
                int n = vertex*3;
                expectedNormal.x += (skin.m00*bindNormals[n] + skin.m01*bindNormals[n+1] + skin.m02*bindNormals[n+2])*weight;
                expectedNormal.y += (skin.m10*bindNormals[n] + skin.m11*bindNormals[n+1] + skin.m12*bindNormals[n+2])*weight;
                expectedNormal.z += (skin.m20*bindNormals[n] + skin.m21*bindNormals[n+1] + skin.m22*bindNormals[n+2])*weight;
            }
            same(new float[]{expected.x, expected.y, expected.z},
                    new float[]{actual.get(vertex*3), actual.get(vertex*3+1), actual.get(vertex*3+2)}, .00001f, "CPU skin differs from bind data");
            expectedNormal.normalizeLocal();
            FloatBuffer normals = mesh.getNormalBuffer();
            same(new float[]{expectedNormal.x, expectedNormal.y, expectedNormal.z},
                    new float[]{normals.get(vertex*3), normals.get(vertex*3+1), normals.get(vertex*3+2)}, .00001f, "CPU normals did not follow animation");
        }
    }
    private static float[] values(FloatBuffer buffer) {
        float[] result = new float[buffer.capacity()];
        for (int i = 0; i < result.length; i++) result[i] = buffer.get(i);
        return result;
    }
    private static void same(float[] expected, float[] actual, float epsilon, String message) {
        require(expected.length == actual.length, message + ": length");
        for (int i = 0; i < expected.length; i++) require(Float.isFinite(actual[i]) && Math.abs(expected[i] - actual[i]) <= epsilon, message + ": " + i);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
