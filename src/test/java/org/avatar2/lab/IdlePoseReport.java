package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Patched native sampler: planted Idle feet, moving pelvis and Lab-ready poses. */
public final class IdlePoseReport {
    public static void verifyAndExport(String root,String body,ColladaModel model,ForwardKinematics fk) throws Exception {
        File source=new File(root,"assets/runtime/body/"+body+".dae");ColladaModel vanilla;
        try(InputStream input=new FileInputStream(source)){vanilla=ColladaImporter.loadModel(source.toURI().toString(),input);}
        vanilla.setRootJointName("Root");ColladaPropertiesXml.load(vanilla,new FakeResourceUrl(source.toURI().toURL()),false);
        ForwardKinematics rawFk=new ForwardKinematics();
        List<Object> samples=new ArrayList<>();float loY=Float.POSITIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY;
        float loZ=loY,hiZ=hiY,footLo=loY,footHi=hiY,maximumSole=0;
        float rawLo=loY,rawHi=hiY;
        float headLo=loY,headHi=hiY;
        float[] feetLo={loY,loY},feetHi={hiY,hiY};
        for(int phase=0;phase<120;phase++){
            fk.calculateForwardKinematics(phase/120f,model.getColladaAnimation("idle"),false,phase%2==1,false);
            rawFk.calculateForwardKinematics(phase/120f,vanilla.getColladaAnimation("idle"),false,phase%2==1,false);
            for(Joint joint:model.getJoints().values()){
                float[] actual=LabPose.rows(joint.getAnimationMatrix()),original=LabPose.rows(vanilla.getJoint(joint.getName()).getAnimationMatrix());
                for(int i:new int[]{0,1,2,4,5,6,8,9,10})
                    if(Math.abs(actual[i]-original[i])>.000001f)throw new AssertionError("Idle changed rotation at "+joint.getName());
                if(joint.getName().equals("Root")&&!Arrays.equals(actual,original))throw new AssertionError("Idle changed Root/world placement");
            }
            Map<String,Object> pose=new LinkedHashMap<>();
            for(Joint j:model.getJoints().values()){
                if(j.getName().equals("NullCamera0"))continue;
                Matrix4f local=j.getAnimationMatrix();
                if(local.isIdentity())local=j.getParent()==null?j.getOriginalBindMatrix():j.getBindmatrixOffset();
                pose.put(j.getName(),LabPose.rows(local));
            }
            fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            rawFk.updateBoneTransformations(vanilla,vanilla.getJoint("Root"),null,false,0,0);
            float rawY=vanilla.getJoint("pelvis").getBindMatrix().m13;
            rawLo=Math.min(rawLo,rawY);rawHi=Math.max(rawHi,rawY);
            Matrix4f pelvis=model.getJoint("pelvis").getBindMatrix();
            float headZ=model.getJoint("head").getBindMatrix().m23;
            headLo=Math.min(headLo,headZ);headHi=Math.max(headHi,headZ);
            loY=Math.min(loY,pelvis.m13);hiY=Math.max(hiY,pelvis.m13);
            loZ=Math.min(loZ,pelvis.m23);hiZ=Math.max(hiZ,pelvis.m23);
            Matrix4f foot=model.getJoint("foot_l").getBindMatrix();
            footLo=Math.min(footLo,foot.m23);footHi=Math.max(footHi,foot.m23);
            float support=Float.POSITIVE_INFINITY;
            for(String side:new String[]{"l","r"}){
                Joint heel=model.getJoint("foot_"+side),toe=model.getJoint("ball_"+side);
                int index=side.equals("l")?0:1;
                feetLo[index]=Math.min(feetLo[index],heel.getBindMatrix().m13);
                feetHi[index]=Math.max(feetHi[index],heel.getBindMatrix().m13);
                support=Math.min(support,sole(heel,heel.getOriginalBindMatrix().m23-.025f));
                support=Math.min(support,sole(toe,toe.getOriginalBindMatrix().m23));
            }
            maximumSole=Math.max(maximumSole,Math.abs(support));
            if(phase%5==0)samples.add(LabJson.map("phase",phase/120f,"pose",pose));
        }
        if(hiY-loY<.0001f)throw new AssertionError(body+" Idle pelvis remains fixed vertically");
        if(hiZ-loZ<.00005f)throw new AssertionError(body+" Idle lost fore/aft pelvis compensation");
        if(headHi-headLo>.015f)throw new AssertionError(body+" Idle still sways excessively: "+(headHi-headLo));
        if(footHi-footLo>.0003f)throw new AssertionError(body+" Idle planted foot drifts fore/aft: "+(footHi-footLo));
        if(maximumSole>.00003f)throw new AssertionError(body+" Idle support leaves bind floor: "+maximumSole);
        for(int side=0;side<2;side++)if(feetHi[side]-feetLo[side]>.00003f)throw new AssertionError(body+" Idle foot still bobs vertically");
        if(rawHi-rawLo>.000001f)throw new AssertionError("Unregistered native fixture gained an Idle correction");
        Path output=Paths.get(root,"build/reports/avatar2");Files.createDirectories(output);
        Files.write(output.resolve(body+"-idle-poses.json"),LabJson.write(LabJson.map("body",body,"samples",samples)).getBytes("UTF-8"));
        String result="body,clamped_pelvis_y_range_m,pelvis_y_range_m,pelvis_z_range_m,left_foot_y_range_m,right_foot_y_range_m,foot_z_drift_m,max_sole_error_m,head_z_range_m,phases\n"+body+","+(rawHi-rawLo)+","+(hiY-loY)+","+(hiZ-loZ)+","+(feetHi[0]-feetLo[0])+","+(feetHi[1]-feetLo[1])+","+(footHi-footLo)+","+maximumSole+","+(headHi-headLo)+",120\n";
        Files.write(output.resolve(body+"-idle-contact.csv"),result.getBytes("UTF-8"));
        System.out.println("AVATAR2_IDLE_CONTACT_OK "+result.trim().replace('\n',' '));
    }
    private static float sole(Joint joint,float z){
        Matrix4f rest=joint.getOriginalBindMatrix(),skin=joint.getBindMatrix().mult(joint.getOriginalInvertBindMatrix());
        return skin.m10*rest.m03+skin.m12*z+skin.m13;
    }
}
