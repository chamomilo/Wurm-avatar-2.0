package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.renderer.model.collada.math.Quaternion;
import com.wurmonline.client.renderer.model.wom.WOMLoader;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Vector3f;
import java.io.File;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Actual native sampling, WOM shield triangles and arm/leg clearance proxies. */
public final class ShieldWalkRuntimeHarness {
    private static final String SHIELD = "weapon/Shields/LargeMetalShield.wom";
    private static final Map<ColladaModel, Map<String, Matrix4f>> UNSCALED = new IdentityHashMap<>();
    public static void run(String root, String graphics) throws Exception {
        List<NativeShield> shields = new ArrayList<>();
        for (String type : new String[]{"LargeMetalShield", "MediumMetalShield", "SmallMetalShield"})
            shields.add(new NativeShield(graphics, "weapon/Shields/" + type + ".wom"));
        require(AvatarShieldWalk.isLargeMetalShield(SHIELD + "?largemetalshield_lms1.emissive=0,0,0,1"), "Material variant lost");
        for (String other : new String[]{"weapon/Shields/LargeWoodenShield.wom", "weapon/Shields/MediumMetalShield.wom",
                "weapon/Shields/SmallMetalShield.wom", "weapon/Shields/shellShield.wom", "other/LargeMetalShield.wom"})
            require(!AvatarShieldWalk.isLargeMetalShield(other), "Another item acquired large fit: " + other);
        List<String> report = new ArrayList<>();
        List<String> meshReport=new ArrayList<>();
        meshReport.add("version,shield,body,height_dirt,phase,arm_triangles,intersected_arm_triangles,arm_mesh_clearance_lower_bound_m,front_strap_hand_gap_m,front_strap_phalange_distance_m,rear_strap_forearm_gap_lower_bound_m,front_strap_grip_contact_triangles");
        report.add("version,shield,body,height_dirt,phases,shield_triangles,baseline_min_leg_clearance_m,corrected_min_leg_clearance_m,min_upper_arm_proxy_clearance_m,min_forearm_proxy_clearance_m,min_shield_floor_m");
        String selectedBody=System.getProperty("avatar2.shieldTestBody");
        int checkedBodies=0;
        for (String body : new String[]{"male-athletic", "male-bodybuilder", "female-athletic", "female-bodybuilder"}) {
            if(selectedBody!=null&&!selectedBody.equals(body))continue;
            checkedBodies++;
            ColladaModel template = GeometryRuntimeHarness.load(new File(root, "assets/runtime/body/" + body + ".dae"));
            AvatarAnimationBridge.normalizeSkinWeights(template);
            AvatarWalkMotion.register(template); AvatarEquipmentSockets.register(template); AvatarHeightPrototype.register(template);
            for(NativeShield shield:shields) {
                if(Boolean.getBoolean("avatar2.onlyTransitions"))verifySocketBlend(new ForwardKinematics(),template,root,body,shield);
                else verifyShield(template,shield,body,root,report,meshReport);
            }
        }
        require(checkedBodies>0,"Unknown shield test body: "+selectedBody);
        if(Boolean.getBoolean("avatar2.onlyTransitions"))return;
        String suffix=selectedBody==null?"":"-"+selectedBody;
        File csv = new File(root, "build/reports/avatar2/shield-walk-runtime"+suffix+".csv");
        Files.createDirectories(csv.getParentFile().toPath()); Files.write(csv.toPath(), report, StandardCharsets.UTF_8);
        Files.write(new File(csv.getParentFile(),"shield-arm-mesh-runtime"+suffix+".csv").toPath(),meshReport,StandardCharsets.UTF_8);
        System.out.println("AVATAR2_SHIELD_WALK_OK three native metal shields; "+checkedBodies*2880+" actual arm mesh and leg clearance poses; "+checkedBodies*384+" front/rear strap fit poses; 13..20 dirt; bodies="+checkedBodies+"; every shield carries at the flank; run unchanged");
    }

    private static final class NativeShield {
        final String path;
        final ColladaOffset offset;
        final List<Vector3f[]> triangles = new ArrayList<>();
        final List<Vector3f[]> frontStrap = new ArrayList<>();
        final List<Vector3f[]> rearStrap = new ArrayList<>();
        final List<Vector3f[]> panelAndRear = new ArrayList<>();
        final Vector3f contact;
        NativeShield(String graphics, String path) throws Exception {
        this.path = path;
        boolean small=path.contains("SmallMetalShield"),medium=path.contains("MediumMetalShield");
        contact=small?new Vector3f(.07269516f,.002376f,-.00904402f):medium?
                new Vector3f(-.135985f,.004388f,-.1250585f):new Vector3f(-.1352075f,0,-.134484f);
        FakeResourceUrl url = new FakeResourceUrl(new URL("jar:" + new File(graphics).toURI().toURL() + "!/" + path));
        ColladaModel item = WOMLoader.loadeNewModel(url);
        ColladaPropertiesXml.load(item, url, true);
        offset = item.getModelsOffsetToNullList().get("NullShield");
        require(offset != null, "Native shield offset missing");
        for (ColladaGeometry geometry : item.getGeometryArray()) for (TriangleMesh mesh : geometry.getTriangleMeshArray()) {
            FloatBuffer vertices = mesh.getVertexBuffer();
            IntBuffer indices = mesh.getIndexBuffer();
            for (int i = 0; i < mesh.getIndexCount(); i += 3) {
                Vector3f[] triangle = new Vector3f[3];
                for (int j = 0; j < 3; j++) {
                    int index = indices.get(i+j)*3;
                    triangle[j] = new Vector3f(vertices.get(index), vertices.get(index+1), vertices.get(index+2));
                }
                triangles.add(triangle);
                boolean front=true;
                for(Vector3f v:triangle)front &= small?v.x>.05f&&v.x<.095f&&v.y<.061f:
                        v.x<-.11f&&v.x>-.16f&&v.y<.065f;
                if(front)frontStrap.add(triangle);
                else panelAndRear.add(triangle);
                boolean rear=true;
                for(Vector3f v:triangle)rear &= small?v.x>-.16f&&v.x<-.09f&&v.y<.080f:
                        medium?v.x>.015f&&v.x<.10f&&v.y<.080f:
                        v.x>.029f&&v.x<.071f&&v.y<.066f;
                if(rear)rearStrap.add(triangle);
            }
        }
        require(!triangles.isEmpty(), "Native shield mesh missing");
        require(frontStrap.size()==70,"Native front strap component changed: "+path);
        require(rearStrap.size()==70,"Native rear strap component changed: "+path+" triangles="+rearStrap.size());
        }
    }

    private static void verifyShield(ColladaModel template, NativeShield shield, String body, String root,
                                     List<String> report, List<String> meshReport) throws Exception {
            ColladaOffset offset = shield.offset;
            List<Vector3f[]> triangles = shield.triangles;
            ColladaModel base = new ColladaModel(template), fit = new ColladaModel(template);
            ArmMesh armMesh=new ArmMesh(fit);
            AvatarShieldWalk.equipped(fit, shield.path, true);
            ForwardKinematics fk = new ForwardKinematics();
            Matrix4f itemOffset = new Matrix4f();
            Quaternion q = new Quaternion();
            q.fromAngleAxis((float)Math.toRadians(offset.getAngleOffset()), offset.getAxisOffset());
            itemOffset.setRotationQuaternion(q);
            float itemScale = offset.getScale(body.startsWith("male-"));
            itemOffset.scale(new Vector3f(itemScale, itemScale, itemScale));
            Vector3f translation = offset.getPositionOffset();
            itemOffset.setTranslation(translation.x, translation.y, translation.z);
            for (int dirt = 13; dirt <= 20; dirt++) {
                float baselineMin = Float.POSITIVE_INFINITY, fitMin = Float.POSITIVE_INFINITY, floor = Float.POSITIVE_INFINITY;
                float upperArm=Float.POSITIVE_INFINITY, forearm=Float.POSITIVE_INFINITY;
                for (int phase = 0; phase < 120; phase++) {
                    pose(fk, base, "walk", phase/120f, false, false, dirt);
                    pose(fk, fit, "walk", phase/120f, false, false, dirt);
                    float[] before = clearance(base, itemOffset, triangles), after = clearance(fit, itemOffset, triangles);
                    baselineMin = Math.min(baselineMin, before[0]);
                    fitMin = Math.min(fitMin, after[0]); floor = Math.min(floor, after[1]);
                    upperArm=Math.min(upperArm,after[2]);forearm=Math.min(forearm,after[3]);
                    // Close-fitting loops need the actual surface at every phase,
                    // rather than oversized circular upper-arm/forearm proxies.
                    Matrix4f fittedFrame=fit.getNullTransformation("NullShield").mult(itemOffset);
                    Contact contact=clearanceOutsideGrip(armMesh,fittedFrame,shield);
                    require(contact.hits==0,"Shield cuts CPU arm mesh: "+shield.path+" "+body+"/"+dirt+" phase="+phase+" triangles="+contact.hits);
                    require(contact.gap>.003f,"Shield touches CPU arm mesh: "+shield.path+" "+body+"/"+dirt+" phase="+phase+" gap="+contact.gap);
                    if(phase==0||phase*16/120!=(phase-1)*16/120) {
                        Contact grip=armMesh.contact(fit.getNullTransformation("NullShield").mult(itemOffset),shield.frontStrap,true);
                        float anchor=gripDistance(fit,shield,itemOffset);
                        Contact rear=armMesh.rearContact(fit.getNullTransformation("NullShield").mult(itemOffset),shield.rearStrap);
                        meshReport.add(Avatar2Mod.VERSION+","+shield.path+","+body+","+dirt+","+phase/120f+","+armMesh.triangles.size()+","+contact.hits+","+contact.gap+","+grip.gap+","+anchor+","+rear.gap+","+grip.hits);
                        require(grip.gap<.012f,"Front strap floats away from fingers: "+shield.path+" "+body+"/"+dirt+" gap="+grip.gap);
                        require(anchor<.021f,"Front strap misses natural fist in 3D: "+shield.path+" "+body+"/"+dirt+" distance="+anchor);
                        require(rear.hits==0&&rear.gap>.003f,"Rear strap cuts forearm: "+shield.path+" "+body+"/"+dirt+" gap="+rear.gap);
                        if(AvatarShieldWalk.isLargeMetalShield(shield.path))require(rear.gap<.030f,"Large rear strap floats away from forearm: "+body+"/"+dirt+" gap="+rear.gap);
                        require(palmDirection(fit,itemOffset)>.5f,"Shield fist reversed by half a turn: "+shield.path+" "+body+"/"+dirt);
                    }
                    for(String[] segment:new String[][]{{"upperarm_l","lowerarm_l"},{"lowerarm_l","hand_l"}})
                        require(Math.abs(point(base.getJoint(segment[0])).subtract(point(base.getJoint(segment[1]))).length()
                                -point(fit.getJoint(segment[0])).subtract(point(fit.getJoint(segment[1]))).length())<.00002f,"Arm length changed");
                    for (Joint joint : base.getJoints().values()) {
                        if (!leftArm(joint.getName())) same(joint.getBindMatrix(), fit.getJoint(joint.getName()).getBindMatrix(), "Other body joint changed");
                    }
                }
                report.add(Avatar2Mod.VERSION + "," + shield.path + "," + body + "," + dirt + ",120," + triangles.size() + "," + baselineMin + "," + fitMin + "," + upperArm + "," + forearm + "," + floor);
                System.out.println("SHIELD_CLEARANCE " + shield.path + " " + body + " dirt=" + dirt + " legs=" + fitMin + " upper=" + upperArm + " forearm=" + forearm + " floor=" + floor);
                require(Float.isFinite(fitMin) && fitMin > .003f, "Shield intersects leg capsules: " + shield.path + " " + body + "/" + dirt + " gap=" + fitMin);
                require(Float.isFinite(upperArm)&&Float.isFinite(forearm),"Nonfinite arm proxy distances");
                require(Float.isFinite(floor) && floor > .005f, "Shield hits the floor: " + shield.path + " " + body + "/" + dirt);
            }
            // All walk aliases use the same corrected keys/cache. Other clips, skipped
            // blend groups, hiding, disarming and copies retain native motion.
            for (boolean armBlend : new boolean[]{false, true}) {
            for (ColladaModel model : new ColladaModel[]{base,fit}) for(Joint joint:model.getJoints().values())
                joint.setIsBlendOrChildOfBlend(armBlend && leftArm(joint.getName()));
            for (String clip : new String[]{"walk", "walk.p1", "gait.walk.relaxed", "idle", "idle.p1", "run", "run.p1", "gait.sprint"}) {
                for (int phase = 0; phase < 16; phase++) {
                    for (int scope = 0; scope < 4; scope++) {
                        boolean upperOnly = scope == 1 || scope == 3, lowerOnly = scope == 2 || scope == 3;
                        AvatarShieldWalk.equipped(fit, shield.path, false);
                        pose(fk, base, "idle", 0, false, false, 17);
                        pose(fk, fit, "idle", 0, false, false, 17);
                        AvatarShieldWalk.equipped(fit, shield.path, true);
                        pose(fk, base, clip, phase/16f, upperOnly, lowerOnly, 17);
                        pose(fk, fit, clip, phase/16f, upperOnly, lowerOnly, 17);
                        boolean armSelected = upperOnly ? armBlend : !lowerOnly || !armBlend;
                        if (!(AvatarWalkMotion.isWalk(clip)||AvatarWalkMotion.isIdle(clip)) || !armSelected) {
                            for (Joint joint : base.getJoints().values()) same(joint.getAnimationMatrix(), fit.getJoint(joint.getName()).getAnimationMatrix(), "Fallback changed " + clip);
                        } else {
                            for (String bone : new String[]{"upperarm_l", "lowerarm_l"}) {
                                Joint joint = fit.getJoint(bone);
                                same(joint.getAnimationMatrix(), fit.getColladaAnimation(clip).getAnimationMatrix(joint.getBoneIndex()), "Native blend cache missed corrected arm");
                            }
                            Matrix4f retained = new Matrix4f(fit.getJoint("lowerarm_l").getAnimationMatrix());
                            Matrix4f retainedHand = new Matrix4f(fit.getJoint("hand_l").getAnimationMatrix());
                            pose(fk, fit, clip, phase/16f, upperOnly, lowerOnly, 17);
                            same(retained, fit.getJoint("lowerarm_l").getAnimationMatrix(), "Repeated sampling accumulated correction " + clip + "/" + scope + "/" + phase);
                            same(retainedHand, fit.getJoint("hand_l").getAnimationMatrix(), "Repeated sampling accumulated wrist correction " + clip + "/" + scope + "/" + phase);
                        }
                    }
                }
            }
            }
            for(Joint j:fit.getJoints().values())j.setIsBlendOrChildOfBlend(false);
            for(Joint j:base.getJoints().values())j.setIsBlendOrChildOfBlend(false);
            AvatarShieldWalk.equipped(fit,shield.path,true);pose(fk,fit,"walk",.75f,false,false,13);
            Matrix4f heldUpper=new Matrix4f(fit.getJoint("upperarm_l").getBindMatrix());
            Matrix4f heldForearm=new Matrix4f(fit.getJoint("lowerarm_l").getBindMatrix());
            for(String path:new String[]{"weapon/Shields/LargeWoodenShield.wom","weapon/Shields/MediumMetalShield.wom","weapon/Shields/SmallMetalShield.wom","weapon/Shields/shellShield.wom"}) {
                AvatarShieldWalk.equipped(fit,path,true);pose(fk,fit,"walk",.75f,false,false,13);
                same(heldUpper,fit.getJoint("upperarm_l").getBindMatrix(),"Other shield lost base carry");
                same(heldForearm,fit.getJoint("lowerarm_l").getBindMatrix(),"Other shield lost base carry");
            }
            for (String path : new String[]{null}) {
                AvatarShieldWalk.equipped(fit, path, true);
                pose(fk, base, "walk", .75f, false, false, 13); pose(fk, fit, "walk", .75f, false, false, 13);
                same(base.getJoint("lowerarm_l").getBindMatrix(), fit.getJoint("lowerarm_l").getBindMatrix(), "Disarm/other shield did not restore native arm");
                same(base.getJoint("hand_l").getBindMatrix(), fit.getJoint("hand_l").getBindMatrix(), "Disarm did not restore native wrist");
            }
            AvatarShieldWalk.equipped(fit, shield.path, false);
            pose(fk, fit, "walk", .75f, false, false, 13);
            same(base.getJoint("lowerarm_l").getBindMatrix(), fit.getJoint("lowerarm_l").getBindMatrix(), "Hidden shield changed arm");
            same(base.getJoint("hand_l").getBindMatrix(), fit.getJoint("hand_l").getBindMatrix(), "Hidden shield changed wrist");
            AvatarShieldWalk.equipped(fit, shield.path, true);
            ColladaModel copy = new ColladaModel(fit);
            pose(fk, copy, "walk", .75f, false, false, 13);
            same(base.getJoint("lowerarm_l").getBindMatrix(), copy.getJoint("lowerarm_l").getBindMatrix(), "Cached copy inherited equipment");
            same(base.getJoint("hand_l").getBindMatrix(), copy.getJoint("hand_l").getBindMatrix(), "Cached copy inherited wrist fit");
            verifySocketBlend(fk,fit,root,body,shield);
    }

    private static boolean leftArm(String name) {
        return name.equals("upperarm_l") || name.equals("lowerarm_l") || name.equals("hand_l")
                || name.endsWith("_l") && (name.startsWith("thumb_") || name.startsWith("index_")
                || name.startsWith("middle_") || name.startsWith("ring_") || name.startsWith("pinky_"));
    }
    private static void pose(ForwardKinematics fk, ColladaModel model, String clip, float phase, boolean upper, boolean lower, int dirt) {
        Map<String, Matrix4f> previous = UNSCALED.get(model);
        if (previous != null) for (Map.Entry<String, Matrix4f> entry : previous.entrySet())
            model.getJoint(entry.getKey()).getBindMatrix().set(entry.getValue());
        AvatarHeightPrototype.prepare(model);
        fk.calculateForwardKinematics(phase, model.getColladaAnimation(clip), upper, lower, false);
        fk.updateBoneTransformations(model, model.getJoint("Root"), null, false, 0, 0);
        Map<String, Matrix4f> baseline = new LinkedHashMap<>();
        for (Joint joint : model.getJoints().values()) baseline.put(joint.getName(), new Matrix4f(joint.getBindMatrix()));
        UNSCALED.put(model, baseline);
        AvatarHeightPrototype.apply(model, AvatarHeightPrototype.limits(model).fromDirts(dirt));
        AvatarShieldWalk.publishFrame(model);
    }
    private static float[] clearance(ColladaModel model, Matrix4f offset, List<Vector3f[]> triangles) {
        Matrix4f frame = model.getNullTransformation("NullShield").mult(offset);
        float gap = Float.POSITIVE_INFINITY, floor = Float.POSITIVE_INFINITY;
        float upper=Float.POSITIVE_INFINITY,forearm=Float.POSITIVE_INFINITY;
        float scale = AvatarHeightPrototype.scale(model);
        for (Vector3f[] source : triangles) {
            Vector3f[] tri = new Vector3f[3];
            for (int i = 0; i < 3; i++) { tri[i] = frame.mult(source[i]); floor = Math.min(floor, tri[i].y); }
            for (String side : new String[]{"l", "r"}) {
                for (String[] segment : new String[][]{{"thigh_", "calf_"}, {"calf_", "foot_"}}) {
                    Vector3f a = point(model.getJoint(segment[0]+side)), b = point(model.getJoint(segment[1]+side));
                    float radius = segment[0].equals("thigh_") ? .11f : .065f;
                    gap = Math.min(gap, (float)Math.sqrt(segmentTriangle(a,b,tri)) - radius*scale);
                }
            }
            upper=Math.min(upper,(float)Math.sqrt(segmentTriangle(point(model.getJoint("upperarm_l")),point(model.getJoint("lowerarm_l")),tri))-.065f*scale);
            forearm=Math.min(forearm,(float)Math.sqrt(segmentTriangle(point(model.getJoint("lowerarm_l")),point(model.getJoint("hand_l")),tri))-.045f*scale);
        }
        return new float[]{gap, floor,upper,forearm};
    }

    private static void verifySocketBlend(ForwardKinematics fk,ColladaModel model,String root,String body,NativeShield shield) throws Exception {
        AvatarShieldWalk.equipped(model,shield.path,true);
        pose(fk,model,"walk",.5f,false,false,17);
        {
            Matrix4f completed=model.getNullTransformation("NullShield");
            Matrix4f index=model.getJoint("index_02_l").getBindMatrix(), middle=model.getJoint("middle_02_l").getBindMatrix();
            Matrix4f savedIndex=new Matrix4f(index), savedMiddle=new Matrix4f(middle);
            // Simulate an in-progress next sample before its successful skin publication.
            index.m03+=.2f;middle.m03+=.2f;
            same(completed,model.getNullTransformation("NullShield"),"Render consumed intermediate finger placement");
            index.set(savedIndex);middle.set(savedMiddle);
        }
        Matrix4f completed=model.getNullTransformation("NullShield");
        fk.calculateForwardKinematics(0,model.getColladaAnimation("run"),false,false,false);
        // No completed frame yet: sampling run must not publish its socket state.
        same(completed,model.getNullTransformation("NullShield"),"Sampling published an unfinished attachment");
        new ColladaModelData(new FakeResourceUrl(new File(root,"assets/runtime/body/"+body+".dae").toURI().toURL()),model)
                .calculateSkins(TriangleMesh.LODLevel.NONE);
        same(AvatarEquipmentSockets.unfittedShieldFrame(model),model.getNullTransformation("NullShield"),
                "Native successful skinning did not publish run attachment");
        Matrix4f item=nativeItemOffset(shield,body);
        for(float fraction:new float[]{0,.25f,.5f,.75f,1}) {
            poseBlend(fk,model,"walk",.5f,"run",0,fraction,17);
            Matrix4f metal=model.getNullTransformation("NullShield");
            if(fraction==1)same(AvatarEquipmentSockets.unfittedShieldFrame(model),metal,"Run endpoint retained walking fit");
            else if(fraction==0)require(gripDistance(model,shield,item)<.035f,"Walk endpoint lost fist attachment");
            float[] values=new float[16];metal.get(values,true);
            for(float value:values)require(Float.isFinite(value),"Nonfinite shield transition");
            for(int col=0;col<3;col++)require(Math.abs(new Vector3f(values[col],values[4+col],values[8+col]).length()-1)<.00005f,
                    "Shield transition changed item size");
            Contact transition=new ArmMesh(model).contact(metal.mult(item),shield.triangles);
            System.out.println("SHIELD_TRANSITION "+shield.path+" "+body+" run="+fraction+" hits="+transition.hits+" gap="+transition.gap);
        }
        for(int dirt:new int[]{13,20})for(int phase=0;phase<4;phase++) {
            pose(fk,model,"idle",phase/4f,false,false,dirt);
            Contact hold=clearanceOutsideGrip(new ArmMesh(model),model.getNullTransformation("NullShield").mult(item),shield);
            require(hold.hits==0&&hold.gap>.003f,"Idle shield carry cuts arm: "+shield.path+" "+body+"/"+dirt+" gap="+hold.gap);
            require(gripDistance(model,shield,item)<.035f,"Idle shield carry loses grip");
            require(palmDirection(model,item)>.5f,"Idle shield fist is reversed");
        }
        // Holding the shield through an actual native walk/idle crossfade must
        // preserve the grip and mesh clearance, including the shortest body.
        ArmMesh arm=new ArmMesh(model);
        for(int dirt:new int[]{13,20})for(int phase=0;phase<4;phase++)
            for(float fraction:new float[]{0,.25f,.5f,.75f,1}) {
                poseBlend(fk,model,"walk",phase/4f,"idle",(3-phase)/4f,fraction,dirt);
                Matrix4f frame=model.getNullTransformation("NullShield").mult(item);
                Contact hold=clearanceOutsideGrip(arm,frame,shield);
                Contact grip=arm.contact(frame,shield.frontStrap,true);
                String context=shield.path+" "+body+"/"+dirt+" phase="+phase+" idle="+fraction;
                require(hold.hits==0&&hold.gap>.003f,"Stopping shield cuts arm: "+context+" gap="+hold.gap);
                require(grip.gap<.012f&&gripDistance(model,shield,item)<.035f,"Stopping shield loses grip: "+context);
                require(palmDirection(model,item)>.5f,"Stopping shield fist is reversed: "+context);
            }
        pose(fk,model,"walk",.5f,false,false,17);
        Matrix4f retained=model.getNullTransformation("NullShield");
        for(String variant:new String[]{shield.path+"?material.diffuse=1,1,1,1",shield.path.replace('/','\\').toUpperCase(Locale.ROOT)}) {
            AvatarShieldWalk.equipped(model,variant,true);
            same(retained,model.getNullTransformation("NullShield"),"Shield material/path variant lost its fit");
        }
        AvatarShieldWalk.equipped(model,"other/"+new File(shield.path).getName(),true);
        Matrix4f unrelated=model.getNullTransformation("NullShield");
        AvatarShieldWalk.equipped(model,"weapon/Shields/LargeWoodenShield.wom",true);
        same(unrelated,model.getNullTransformation("NullShield"),"Unrelated resource acquired metal fit");
        AvatarShieldWalk.equipped(model,shield.path,true);
        for(boolean blend:new boolean[]{false,true}) {
            model.getJoint("lowerarm_l").setIsBlendOrChildOfBlend(blend);
            fk.blendAnimation(model.getColladaAnimation("walk"),model.getColladaAnimation("idle"),model,1,true,true);
            AvatarShieldWalk.publishFrame(model);
            same(retained,model.getNullTransformation("NullShield"),"Skipped native blend groups changed socket fit");
        }
    }

    private static void poseBlend(ForwardKinematics fk,ColladaModel model,String first,float firstPhase,
                                  String second,float secondPhase,float fraction,int dirt) {
        Map<String,Matrix4f> previous=UNSCALED.get(model);
        if(previous!=null)for(Map.Entry<String,Matrix4f> entry:previous.entrySet())
            model.getJoint(entry.getKey()).getBindMatrix().set(entry.getValue());
        AvatarHeightPrototype.prepare(model);
        fk.calculateForwardKinematics(firstPhase,model.getColladaAnimation(first),false,false,false);
        fk.calculateForwardKinematics(secondPhase,model.getColladaAnimation(second),false,false,false);
        fk.blendAnimation(model.getColladaAnimation(first),model.getColladaAnimation(second),model,fraction,false,false);
        fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
        Map<String,Matrix4f> unscaled=new LinkedHashMap<>();
        for(Joint bone:model.getJoints().values())unscaled.put(bone.getName(),new Matrix4f(bone.getBindMatrix()));
        UNSCALED.put(model,unscaled);
        AvatarHeightPrototype.apply(model,AvatarHeightPrototype.limits(model).fromDirts(dirt));
        AvatarShieldWalk.publishFrame(model);
    }

    private static Matrix4f nativeItemOffset(NativeShield shield,String body) {
        Quaternion q=new Quaternion();q.fromAngleAxis((float)Math.toRadians(shield.offset.getAngleOffset()),shield.offset.getAxisOffset());
        Matrix4f item=new Matrix4f();item.setRotationQuaternion(q);
        float size=shield.offset.getScale(body.startsWith("male-"));item.scale(new Vector3f(size,size,size));
        Vector3f offset=shield.offset.getPositionOffset();item.setTranslation(offset.x,offset.y,offset.z);return item;
    }
    private static Contact clearanceOutsideGrip(ArmMesh arm,Matrix4f frame,NativeShield shield) {
        if(!AvatarShieldWalk.isLargeMetalShield(shield.path))return arm.contact(frame,shield.triangles);
        // The native closed fist has no open tunnel for its handle. Only the
        // front strap may contact hand triangles at the separately checked grip.
        // Panel/rear strap still clear the entire arm; the front strap must
        // clear the upper arm, forearm and wrist blend outside that hand region.
        Contact panel=arm.contact(frame,shield.panelAndRear);
        Contact front=arm.nonHandContact(frame,shield.frontStrap);
        return new Contact(panel.hits+front.hits,Math.min(panel.gap,front.gap));
    }
    private static float gripDistance(ColladaModel model,NativeShield shield,Matrix4f item) {
        Vector3f fingers=point(model.getJoint("index_02_l")).add(point(model.getJoint("middle_02_l"))).mult(.5f);
        return model.getNullTransformation("NullShield").mult(item).mult(shield.contact).subtract(fingers).length();
    }
    private static float palmDirection(ColladaModel model,Matrix4f item) {
        Vector3f wrist=point(model.getJoint("hand_l"));
        Vector3f normal=point(model.getJoint("index_01_l")).subtract(wrist)
                .cross(point(model.getJoint("pinky_01_l")).subtract(wrist)).normalize();
        Matrix4f frame=model.getNullTransformation("NullShield").mult(item);
        return normal.dot(new Vector3f(frame.m01,frame.m11,frame.m21).normalize());
    }
    private static Vector3f point(Matrix4f m){return new Vector3f(m.m03,m.m13,m.m23);}

    private static final class Contact {
        final int hits;final float gap;
        Contact(int hits,float gap){this.hits=hits;this.gap=gap;}
    }

    /** CPU mesh triangles use the native imported weights and completed bind matrices. */
    private static final class ArmMesh {
        final TriangleMesh mesh;
        final List<int[]> triangles=new ArrayList<>();
        final List<int[]> handTriangles=new ArrayList<>();
        final List<int[]> forearmTriangles=new ArrayList<>();
        final List<int[]> nonHandTriangles=new ArrayList<>();
        ArmMesh(ColladaModel model) {
            TriangleMesh body=null;
            for(ColladaGeometry geometry:model.getGeometryArray())for(TriangleMesh candidate:geometry.getTriangleMeshArray())
                if(candidate.getName().startsWith("Avatar2BodyMesh"))body=candidate;
            require(body!=null,"No body mesh for arm check");mesh=body;
            boolean indexed=mesh.getIndexCount()>0;int count=indexed?mesh.getIndexCount():mesh.getVertexCount();
            for(int i=0;i<count;i+=3) {
                int[] triangle=new int[3];boolean arm=false,hand=false,forearm=false;
                for(int j=0;j<3;j++) {
                    int vertex=indexed?mesh.getIndexBuffer().get(i+j):i+j;triangle[j]=vertex;float weight=0;
                    for(int slot=0;slot<4;slot++)if(leftArm(mesh.getBones()[mesh.getBoneBuffer().get(vertex*4+slot)].getName()))
                        weight+=mesh.getWeightBuffer().get(vertex*4+slot);
                    if(weight>.15f)arm=true;
                    float handWeight=0;
                    for(int slot=0;slot<4;slot++) {
                        String bone=mesh.getBones()[mesh.getBoneBuffer().get(vertex*4+slot)].getName();
                        if(leftArm(bone)&&!bone.equals("upperarm_l")&&!bone.equals("lowerarm_l"))handWeight+=mesh.getWeightBuffer().get(vertex*4+slot);
                        if(bone.equals("lowerarm_l")&&mesh.getWeightBuffer().get(vertex*4+slot)>.15f)forearm=true;
                    }
                    if(handWeight>.5f)hand=true;
                }
                if(arm)triangles.add(triangle);
                if(hand)handTriangles.add(triangle);
                if(arm&&!hand)nonHandTriangles.add(triangle);
                if(forearm)forearmTriangles.add(triangle);
            }
            require(triangles.size()>1000,"Arm mesh selection was incomplete");
        }
        Contact contact(Matrix4f frame,List<Vector3f[]> source) {
            return contact(frame,source,false);
        }
        Contact contact(Matrix4f frame,List<Vector3f[]> source,boolean handOnly) {
            return contact(frame,source,handOnly?handTriangles:triangles);
        }
        Contact rearContact(Matrix4f frame,List<Vector3f[]> source) {
            return contact(frame,source,forearmTriangles);
        }
        Contact nonHandContact(Matrix4f frame,List<Vector3f[]> source) {
            return contact(frame,source,nonHandTriangles);
        }
        private Contact contact(Matrix4f frame,List<Vector3f[]> source,List<int[]> selectedTriangles) {
            List<Vector3f[]> shield=new ArrayList<>();
            for(Vector3f[] t:source)shield.add(new Vector3f[]{frame.mult(t[0]),frame.mult(t[1]),frame.mult(t[2])});
            Matrix4f[] skin=new Matrix4f[mesh.getBones().length];
            for(int i=0;i<skin.length;i++)skin[i]=mesh.getBones()[i].getBindMatrix().mult(mesh.getBones()[i].getOriginalInvertBindMatrix());
            float margin=selectedTriangles==forearmTriangles?.04f:.02f;
            Vector3f[] vertices=new Vector3f[mesh.getVertexCount()];int hits=0;float gapSquared=margin*margin;
            for(int[] indices:selectedTriangles) {
                Vector3f[] arm=new Vector3f[3];
                for(int j=0;j<3;j++) {
                    int vertex=indices[j];Vector3f value=vertices[vertex];
                    if(value==null) {
                        float[] raw=mesh.getOriginalVertexBuffer();Vector3f rest=new Vector3f(raw[vertex*3],raw[vertex*3+1],raw[vertex*3+2]);value=new Vector3f();
                        for(int slot=0;slot<4;slot++) {
                            float weight=mesh.getWeightBuffer().get(vertex*4+slot);
                            if(weight>0)value=value.add(skin[mesh.getBoneBuffer().get(vertex*4+slot)].mult(rest).mult(weight));
                        }
                        vertices[vertex]=value;
                    }
                    arm[j]=value;
                }
                boolean hit=false;
                for(Vector3f[] t:shield) {
                    if(!overlap(arm,t,margin))continue;
                    for(int edge=0;edge<3;edge++) {
                        float distance=Math.min(segmentTriangle(arm[edge],arm[(edge+1)%3],t),
                                segmentTriangle(t[edge],t[(edge+1)%3],arm));
                        gapSquared=Math.min(gapSquared,distance);
                        if(distance<1e-12f){hit=true;break;}
                    }
                    if(hit)break;
                }
                if(hit)hits++;
            }
            return new Contact(hits,(float)Math.sqrt(gapSquared));
        }
        private static boolean overlap(Vector3f[] a,Vector3f[] b,float margin) {
            return max(a,0)+margin>=min(b,0)&&max(b,0)+margin>=min(a,0)&&max(a,1)+margin>=min(b,1)&&max(b,1)+margin>=min(a,1)&&max(a,2)+margin>=min(b,2)&&max(b,2)+margin>=min(a,2);
        }
        private static float component(Vector3f v,int axis){return axis==0?v.x:axis==1?v.y:v.z;}
        private static float min(Vector3f[] t,int axis){return Math.min(component(t[0],axis),Math.min(component(t[1],axis),component(t[2],axis)));}
        private static float max(Vector3f[] t,int axis){return Math.max(component(t[0],axis),Math.max(component(t[1],axis),component(t[2],axis)));}
    }
    private static Vector3f point(Joint joint) { Matrix4f m = joint.getBindMatrix(); return new Vector3f(m.m03,m.m13,m.m23); }
    private static float segmentTriangle(Vector3f a, Vector3f b, Vector3f[] t) {
        Vector3f normal = t[1].subtract(t[0]).cross(t[2].subtract(t[0]));
        float da = normal.dot(a.subtract(t[0])), db = normal.dot(b.subtract(t[0]));
        if (da*db <= 0 && Math.abs(da-db) > 1e-10f) {
            Vector3f crossing = a.add(b.subtract(a).mult(da/(da-db)));
            if (pointTriangle(crossing,t) < 1e-10f) return 0;
        }
        float result = Math.min(pointTriangle(a,t),pointTriangle(b,t));
        for (int i=0;i<3;i++) result=Math.min(result,segments(a,b,t[i],t[(i+1)%3]));
        return result;
    }
    private static float pointTriangle(Vector3f p, Vector3f[] t) {
        Vector3f ab=t[1].subtract(t[0]), ac=t[2].subtract(t[0]), ap=p.subtract(t[0]);
        float d1=ab.dot(ap),d2=ac.dot(ap);
        if(d1<=0&&d2<=0)return ap.lengthSquared();
        Vector3f bp=p.subtract(t[1]);float d3=ab.dot(bp),d4=ac.dot(bp);
        if(d3>=0&&d4<=d3)return bp.lengthSquared();
        float vc=d1*d4-d3*d2;
        if(vc<=0&&d1>=0&&d3<=0)return p.subtract(t[0].add(ab.mult(d1/(d1-d3)))).lengthSquared();
        Vector3f cp=p.subtract(t[2]);float d5=ab.dot(cp),d6=ac.dot(cp);
        if(d6>=0&&d5<=d6)return cp.lengthSquared();
        float vb=d5*d2-d1*d6;
        if(vb<=0&&d2>=0&&d6<=0)return p.subtract(t[0].add(ac.mult(d2/(d2-d6)))).lengthSquared();
        float va=d3*d6-d5*d4;
        if(va<=0&&(d4-d3)>=0&&(d5-d6)>=0)return p.subtract(t[1].add(t[2].subtract(t[1]).mult((d4-d3)/((d4-d3)+(d5-d6))))).lengthSquared();
        float sum=va+vb+vc;
        if(Math.abs(sum)<1e-15f)return Math.min(ap.lengthSquared(),Math.min(bp.lengthSquared(),cp.lengthSquared()));
        return p.subtract(t[0].add(ab.mult(vb/sum)).add(ac.mult(vc/sum))).lengthSquared();
    }
    private static float segments(Vector3f p,Vector3f q,Vector3f a,Vector3f b) {
        Vector3f u=q.subtract(p),v=b.subtract(a),w=p.subtract(a);
        float aa=u.dot(u),bb=u.dot(v),cc=v.dot(v),dd=u.dot(w),ee=v.dot(w),den=aa*cc-bb*bb;
        float s=den>1e-12f?clamp((bb*ee-cc*dd)/den):0;
        float t=cc>1e-12f?(bb*s+ee)/cc:0;
        if(t<0){t=0;s=aa>1e-12f?clamp(-dd/aa):0;}
        else if(t>1){t=1;s=aa>1e-12f?clamp((bb-dd)/aa):0;}
        return w.add(u.mult(s)).subtract(v.mult(t)).lengthSquared();
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    private static void same(Matrix4f a, Matrix4f b, String why) throws Exception {
        float[] aa=new float[16],bb=new float[16];a.get(aa,true);b.get(bb,true);
        for(int i=0;i<16;i++)require(Math.abs(aa[i]-bb[i])<.00004f,why+" component="+i);
    }
    private static void require(boolean ok, String why) { if(!ok)throw new AssertionError(why); }
}
