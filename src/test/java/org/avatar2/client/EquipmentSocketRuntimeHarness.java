package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector;
import com.wurmonline.math.Vector3f;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Required native attachment API probe: production hooks, copy and real FK. */
public final class EquipmentSocketRuntimeHarness {
    private static final String[] SOCKETS={"NullLeftHand","NullRightHand","NullShield",
            "LeftHandMiddle1","RightHandMiddle1","Spine","SpineTop"};
    public static void run(String root) throws Exception {
        List<String> report=new ArrayList<>();
        report.add("version,body,poses,sockets,max_item_axis_length_error_m,validation");
        for(String body:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
            ColladaModel template=GeometryRuntimeHarness.load(new File(root,"assets/runtime/body/"+body+".dae"));
            String[] ids=template.getJointIds().clone();
            int joints=template.getJoints().size();
            AvatarEquipmentSockets.register(template);
            AvatarEquipmentSockets.register(template);
            require(joints==template.getJoints().size(),"Sockets changed skeleton size");
            require(Arrays.equals(ids,template.getJointIds()),"Sockets changed skin indices");
            AvatarHeightPrototype.register(template);
            AvatarWalkMotion.register(template);
            ColladaModel world=new ColladaModel(template), reference=new ColladaModel(template);
            ForwardKinematics fk=new ForwardKinematics(), rfk=new ForwardKinematics();
            float maxError=0;
            int poses=0;
            for(String clip:new String[]{"idle","walk","run"})for(int phase=0;phase<12;phase++){
                fk.calculateForwardKinematics(phase/12f,world.getColladaAnimation(clip),false,false,false);
                fk.updateBoneTransformations(world,world.getJoint("Root"),null,false,0,0);
                rfk.calculateForwardKinematics(phase/12f,reference.getColladaAnimation(clip),false,false,false);
                rfk.updateBoneTransformations(reference,reference.getJoint("Root"),null,false,0,0);
                Map<String,Matrix4f> unscaled=new LinkedHashMap<>();
                for(Joint joint:world.getJoints().values())unscaled.put(joint.getName(),new Matrix4f(joint.getBindMatrix()));
                for(int dirt:new int[]{13,17,20}){
                    for(Map.Entry<String,Matrix4f> entry:unscaled.entrySet())world.getJoint(entry.getKey()).getBindMatrix().set(entry.getValue());
                    AvatarHeightPrototype.prepare(world);
                    float scale=AvatarHeightPrototype.limits(world).fromDirts(dirt);
                    AvatarHeightPrototype.apply(world,scale);
                    for(String name:SOCKETS){
                        require(world.hasNull(name),"Missing socket "+name);
                        int id=world.getNullId(name);
                        require(id>=16384,"Virtual socket used a skin index");
                        require(world.getNullId(name.toLowerCase(Locale.ROOT))==id,"Case-insensitive ID");
                        Matrix4f actual=world.getNullTransformation(name), neutral=reference.getNullTransformation(name);
                        Matrix4f byIndex=world.getNullTransformation(id,new Matrix4f());
                        Matrix4f into=new Matrix4f();
                        require(world.getNullTransformation(name,into)==into,"Destination identity changed");
                        same(actual,byIndex);same(actual,into);
                        float floor=template.calculateBoundingBox().getH0();
                        near(actual.m03,neutral.m03*scale,"Socket X");
                        near(actual.m13,floor+(neutral.m13-floor)*scale,"Socket Y");
                        near(actual.m23,neutral.m23*scale,"Socket Z");
                        // Three independent metre-long item axes exercise orientation and size.
                        Vector3f origin=actual.mult(new Vector3f(0,0,0));
                        for(Vector3f axis:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}){
                            float length=actual.mult(axis).subtract(origin).length();
                            maxError=Math.max(maxError,Math.abs(length-1));near(length,1,"Rigid item changed size");
                        }
                        Vector offset=world.getNullAttachmentOffset(name,new Vector());
                        Vector indexedOffset=world.getNullAttachmentOffset(id,new Vector());
                        near(offset.vector[0],actual.m03,"Offset X");near(offset.vector[1],actual.m13,"Offset Y");
                        for(int axis=0;axis<3;axis++)near(offset.vector[axis],indexedOffset.vector[axis],"Indexed offset");
                        Vector itemScale=world.getNullScale(name);
                        for(float value:itemScale.vector)near(value,1,"Native item scale API");
                        Matrix4f original=world.getNullOriginalTransformation(name,new Matrix4f());
                        same(original,world.getNullOriginalTransformation(id,new Matrix4f()));
                        near(original.m00,1,"Vanilla reference X");near(original.m11,1,"Vanilla reference Y");
                        near(original.m22,1,"Vanilla reference Z");near(original.m01,0,"Vanilla reference off-axis");
                        Vector originalOffset=world.getOriginalNullAttachmentOffset(name,new Vector());
                        near(originalOffset.vector[1],original.m13,"Original offset double-scaled");
                        float[] degrees=world.getNullRotation(name).vector;
                        com.wurmonline.client.renderer.model.collada.math.Quaternion quaternion=new com.wurmonline.client.renderer.model.collada.math.Quaternion();
                        quaternion.fromAngles((float)Math.toRadians(degrees[0]),(float)Math.toRadians(degrees[1]),(float)Math.toRadians(degrees[2]));
                        Matrix4f rotated=new Matrix4f();rotated.setRotationQuaternion(quaternion);
                        near(rotated.m00,actual.m00,"Native Euler rotation X");
                        near(rotated.m12,actual.m12,"Native Euler rotation Y");
                        Matrix4f retained=new Matrix4f(actual);
                        actual.m03+=10;
                        same(retained,world.getNullTransformation(name));
                    }
                    // A copy made at temporary height must have independent unscaled frames.
                    ColladaModel copy=new ColladaModel(world), grandchild=new ColladaModel(copy);
                    for(String name:SOCKETS){same(reference.getNullTransformation(name),copy.getNullTransformation(name));
                        same(reference.getNullTransformation(name),grandchild.getNullTransformation(name));}
                    poses++;
                }
                for(Map.Entry<String,Matrix4f> entry:unscaled.entrySet())world.getJoint(entry.getKey()).getBindMatrix().set(entry.getValue());
                AvatarHeightPrototype.prepare(world);
                for(String name:SOCKETS)same(reference.getNullTransformation(name),world.getNullTransformation(name));
            }
            // Native missing-socket and camera behavior remains exact for an unregistered resource.
            ColladaModel unmanaged=GeometryRuntimeHarness.load(new File(root,"assets/runtime/body/"+body+".dae"));
            require(!unmanaged.hasNull("NullRightHand"),"Unregistered model acquired sockets");
            require(unmanaged.getNullId("NullRightHand")==-1,"Unregistered socket ID changed");
            same(new Matrix4f(),unmanaged.getNullTransformation("NullRightHand"));
            same(template.getNullTransformation("NullCamera0"),unmanaged.getNullTransformation("NullCamera0"));
            same(new Matrix4f(),world.getNullTransformation(99999,new Matrix4f()));
            require(!world.hasNull("NullHelmet"),"Unfitted helmet enabled");
            require(joints==world.getJoints().size(),"Copied skeleton changed");
            report.add(Avatar2Mod.VERSION+","+body+","+poses+","+SOCKETS.length+","+maxError+",native_fk_attachment_apis_height_copy_reset");
            System.out.println("AVATAR2_EQUIPMENT_SOCKETS_OK "+body+" poses="+poses+" sockets="+SOCKETS.length+" max_axis_error="+maxError);
        }
        File file=new File(root,"build/reports/avatar2/equipment-sockets-runtime.csv");
        Files.createDirectories(file.getParentFile().toPath());Files.write(file.toPath(),report,StandardCharsets.UTF_8);
    }
    private static void same(Matrix4f a,Matrix4f b)throws Exception{
        float[] expected=new float[16],actual=new float[16];a.get(expected,true);b.get(actual,true);
        for(int i=0;i<16;i++)near(actual[i],expected[i],"Matrix component "+i);
    }
    private static void near(float actual,float expected,String message){
        require(Float.isFinite(actual)&&Math.abs(actual-expected)<.00003f,message+": "+actual+" != "+expected);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
