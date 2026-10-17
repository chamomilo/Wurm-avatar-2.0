package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Vector;
import org.avatar2.client.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Pause the real animation worker between rollback and FK; read the renderer's APIs. */
public final class CameraRuntimeHarness {
    public static void run(String root, boolean expectJitter) throws Exception {
        int cases=0;
        try {
            AnimationLab.start();
            for(String name:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}) {
                File file=new File(root,"assets/runtime/body/"+name+".dae");
                ColladaModel model=GeometryRuntimeHarness.load(file);
                AvatarGeometryPrototype.register(model);
                ColladaModelData data=new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),model);
                AnimationLab.registerLocalModel(model);
                ForwardKinematics fk=new ForwardKinematics();
                for(float headSize:new float[]{0,.5f,1}) for(int dirt:new int[]{13,20}) for(String clip:new String[]{"game","idle","walk","run"}) {
                    AnimationLab.submit(LabJson.map("heightDirts",dirt,"geometry",LabJson.map("headSize",headSize),"preview",LabJson.map("clip",clip,"paused",true,"phase",.25)));
                    sample(model,data,fk);
                    float[] original=data.getOriginalNullOffset("NullCamera0",new Vector()).vector.clone();
                    float[] animated=data.getNullOffset("NullCamera0",new Vector()).vector.clone();
                    CountDownLatch prepared=new CountDownLatch(1),resume=new CountDownLatch(1);
                    AtomicReference<Throwable> failure=new AtomicReference<>();
                    Thread worker=new Thread(()-> {
                        try {
                            AnimationLab.beginFrame(model);prepared.countDown();
                            if(!resume.await(5,TimeUnit.SECONDS))throw new AssertionError("Camera reader did not resume worker");
                            finish(model,data,fk);
                        } catch(Throwable error) {failure.set(error);}
                    },"Avatar camera animation");
                    worker.setDaemon(true);worker.start();
                    boolean changed=false;
                    try {
                        if(!prepared.await(5,TimeUnit.SECONDS))throw new AssertionError("Animation worker stalled");
                        for(int read=0;read<100;read++) {
                            changed|=different(original,data.getOriginalNullOffset("NullCamera0",new Vector()).vector);
                            changed|=different(animated,data.getNullOffset("NullCamera0",new Vector()).vector);
                        }
                    } finally {resume.countDown();worker.join(5000);}
                    if(worker.isAlive()||failure.get()!=null)throw new AssertionError("Camera interleaving failed",failure.get());
                    if(changed!=expectJitter)throw new AssertionError(name+" "+dirt+" "+clip+": camera changed during unfinished pose="+changed);
                    if(!expectJitter) {
                        require(!different(original,data.getOriginalNullOffset("NullCamera0",new Vector()).vector),"Fixed-height original camera drifts");
                        require(!different(animated,data.getNullOffset("NullCamera0",new Vector()).vector),"Paused camera drifts");
                    }
                    cases++;
                }
                AnimationLab.submit(LabJson.map("reset",true));sample(model,data,fk);
                float[] baseline=data.getOriginalNullOffset("NullCamera0",new Vector()).vector.clone();
                AnimationLab.submit(LabJson.map("heightDirts",13));sample(model,data,fk);
                AnimationLab.stop();sample(model,data,fk);
                require(!different(baseline,data.getOriginalNullOffset("NullCamera0",new Vector()).vector),"Lab off failed to restore camera");
                AnimationLab.start();
            }
            System.out.println(expectJitter?"AVATAR2_061_CAMERA_JITTER_REPRODUCED "+cases+" paused worker interleavings":
                    "AVATAR2_CAMERA_STABILITY_OK "+cases+" paused worker interleavings, "+(cases*200)+" native camera reads, head size endpoints, game/idle/walk/run, four bodies, 13/20 dirt and Lab off");
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);}
    }
    private static void sample(ColladaModel model,ColladaModelData data,ForwardKinematics fk) {
        AnimationLab.beginFrame(model);finish(model,data,fk);
    }
    private static void finish(ColladaModel model,ColladaModelData data,ForwardKinematics fk) {
        try {
            fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            if(AnimationLab.beforeForwardKinematics(model))fk.updateBoneTransformations(model,model.getJoint("Root"),null,false,0,0);
            AnimationLab.afterForwardKinematics(model);data.calculateSkins(TriangleMesh.LODLevel.NONE);
            AnimationLab.afterSkinning(model);
        } finally {AnimationLab.endFrame();}
    }
    private static boolean different(float[] a,float[] b) {
        for(int i=0;i<3;i++)if(!Float.isFinite(b[i])||Math.abs(a[i]-b[i])>.00002f)return true;
        return false;
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
