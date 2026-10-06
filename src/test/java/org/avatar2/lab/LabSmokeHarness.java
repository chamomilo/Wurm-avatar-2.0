package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.FakeResourceUrl;
import org.avatar2.client.AvatarAnimationBridge;
import java.io.*;

/** Offline FK transport smoke test. No game, GL context or real skinning frame. */
public final class LabSmokeHarness {
    public static void main(String[] args) throws Exception {
        File file=new File("assets/runtime/body/male-athletic.dae").getAbsoluteFile();ColladaModel model;
        try(InputStream input=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),input);}
        AvatarAnimationBridge.ensureRoot(model);ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
        AnimationLab.registerLocalModel(model);ForwardKinematics fk=new ForwardKinematics();
        System.out.println("OFFLINE_FK_SMOKE "+AnimationLab.start());
        try{
            long end=System.nanoTime()+(args.length==0?30:Long.parseLong(args[0]))*1_000_000_000L;
            while(System.nanoTime()<end){
                AnimationLab.beginFrame(model);fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                if(AnimationLab.beforeForwardKinematics(model))fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
                AnimationLab.afterForwardKinematics(model);AnimationLab.afterSkinning(model);AnimationLab.endFrame();Thread.sleep(50);
            }
        }finally{AnimationLab.stop();}
    }
}
