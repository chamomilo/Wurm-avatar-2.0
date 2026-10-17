package org.avatar2.client;

import com.wurmonline.client.game.*;
import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.cell.*;
import com.wurmonline.client.renderer.model.*;
import com.wurmonline.client.renderer.model.collada.*;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.FakeResourceUrl;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import sun.misc.Unsafe;

public final class GameplayStartRuntimeHarness {
    public static long now=1_000_000_000L;
    public static float dt=1f/60;
    public static PlayerObj player;
    @SuppressWarnings("unchecked") public static void run(String root)throws Exception {
        Field access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);Unsafe unsafe=(Unsafe)access.get(null);
        player=(PlayerObj)unsafe.allocateInstance(PlayerObj.class);
        World world=(World)unsafe.allocateInstance(World.class);
        Method setWorld=CellRenderable.class.getDeclaredMethod("setWorld",World.class);setWorld.setAccessible(true);setWorld.invoke(null,world);
        Method worker=ColladaAnimationJob.class.getDeclaredMethod("applyAnimations",ColladaModelData.class,ColladaModel.class,
                boolean.class,boolean.class,boolean.class,TriangleMesh.LODLevel.class);worker.setAccessible(true);
        int frames=0;
        for(String sex:new String[]{"male","female"})for(int fps:new int[]{23,60}) {
            dt=1f/fps;File file=new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae");ColladaModel model;
            try(InputStream in=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),in);}
            model.setRootJointName("Root");AvatarAnimationBridge.normalizeSkinWeights(model);
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);AvatarWalkMotion.register(model);
            FakeResourceUrl url=new FakeResourceUrl(new URL("file:avatar2/body/"+sex+"-bodybuilder.dae"));
            ColladaModelData data=new ColladaModelData(url,model);
            Constructor<ModelResourceWrapper> wc=ModelResourceWrapper.class.getDeclaredConstructor(com.wurmonline.client.resources.ResourceUrl.class,AbstractModelData.class);
            wc.setAccessible(true);ModelResourceWrapper wrapper=wc.newInstance(url,data);
            PlayerBodyRenderable body=(PlayerBodyRenderable)unsafe.allocateInstance(PlayerBodyRenderable.class);
            set(body,"playerModel",wrapper);set(body,"player",player);set(body,"blendToStart",true);
            set(body,"hPos",2f);set(body,"equipment",new AttachedCellRenderable[30]);
            ColladaAnimation idle=model.getColladaAnimation("idle"),walk=model.getColladaAnimation("walk");
            MovementData movement=new MovementData(walk,0,com.wurmonline.client.sound.MovementSound.MovementType.WALK);
            movement.setRunAnim(model.getColladaAnimation("run"));set(body,"movementData",movement);
            body.setIdleAnimation(idle);body.setCurrentReadyOrIdleAnimation(idle);
            set(body,"activeAnimations",new ConcurrentHashMap<String,AnimationTask>());
            set(body,"animationToRemove",new ConcurrentLinkedQueue<AnimationTask>());set(body,"random",new Random(1));
            set(body,"initializeAnimationOffset",false);set(body,"currentLod",TriangleMesh.LODLevel.NONE);
            idle.addToModel();walk.addToModel();model.getColladaAnimation("run").addToModel();
            if(!Avatar2Mod.usesAvatar(body)||body.isSwimming())throw new AssertionError("Invalid grounded Avatar fixture");
            ColladaAnimationJob job=new ColladaAnimationJob();idle.apply(0);
            worker.invoke(job,data,model,false,false,true,TriangleMesh.LODLevel.NONE);
            float initial=model.getJoint("foot_l").getBindMatrix().m23;
            float initialRight=model.getJoint("foot_r").getBindMatrix().m23;
            // Pretend Idle's own transition has not completed. The production
            // selector must still publish the first tiny, actual displacement.
            idle.setBlending("walk");
            long start=now,nextInput=now+41_666_667L;
            body.setSpeed(.00011f,false,false,false);
            for(int frame=0;frame<(int)Math.ceil(fps*.5);frame++) {
                now+=(long)(1e9/fps);
                // PlayerObj publishes displacement at 24 Hz, independently of rendering.
                while(now>=nextInput) {
                    float speed=.00011f+(nextInput-start)*1e-9f*.046f;
                    body.setSpeed(speed,false,false,false);nextInput+=41_666_667L;
                }
                movement.setNumsteps(1);
                body.applyAnim();
                if(!body.getIsWalking())throw new AssertionError("Native dispatcher kept Idle during acceleration at frame "+frame);
                worker.invoke(job,data,model,body.getIsWalking(),false,true,TriangleMesh.LODLevel.NONE);
                if(frame==0) {
                    boolean queued=false;for(ColladaAnimation c:model.getLegsAnimation())if(c==walk)queued=true;
                    if(!queued)throw new AssertionError("First displacement did not reach native gait queue");
                    float firstStep=Math.abs(model.getJoint("foot_l").getBindMatrix().m23-initial)
                            +Math.abs(model.getJoint("foot_r").getBindMatrix().m23-initialRight);
                    if(firstStep<.001f)throw new AssertionError("First native worker frame still has frozen feet: "+firstStep);
                }
                frames++;
            }
            float moved=Math.abs(model.getJoint("foot_l").getBindMatrix().m23-initial);
            if(moved<.015f)throw new AssertionError("First half-second still has no foot movement: "+moved);
            body.setSpeed(0,false,false,false);movement.setNumsteps(1);
            if(body.applyMoveAnimation())throw new AssertionError("Stopping did not enter the native Idle transition");
            FakeResourceUrl vanilla=new FakeResourceUrl(new URL("file:creature/humanoid/human/player/male.dae"));
            set(body,"playerModel",wc.newInstance(vanilla,new ColladaModelData(vanilla,model)));
            idle.setBlending("walk");set(body,"blendToStart",true);
            Method gate=PlayerBodyRenderable.class.getDeclaredMethod("blendBetweenIdleToWalk",AnimationData.class,boolean.class);
            gate.setAccessible(true);
            if((Boolean)gate.invoke(body,walk,false))throw new AssertionError("Vanilla's native Idle gate was bypassed");
        }
        System.out.println("AVATAR2_GAMEPLAY_START_OK native selector + Cell dispatcher + queues + worker, both sexes, 23/60 fps, frames="+frames+", first displacement queued, first half-second animated");
    }
    private static void set(Object object,String name,Object value)throws Exception {
        Class<?> c=object.getClass();
        while(c!=null) {
            try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}
            catch(NoSuchFieldException e){c=c.getSuperclass();}
        }
        throw new NoSuchFieldException(name);
    }
}
