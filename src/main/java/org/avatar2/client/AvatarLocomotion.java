package org.avatar2.client;

import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.renderer.model.collada.math.Quaternion;
import com.wurmonline.math.Vector3f;
import java.util.*;
import org.avatar2.lab.AnimationLab;

/** Local visual locomotion: bounded cadence, prompt starts and a turning body.
 * Never changes player position, speed, camera heading or server input.
 */
public final class AvatarLocomotion {
    private static final Map<ColladaModel,State> STATES=new WeakHashMap<>();
    private AvatarLocomotion() { }

    private static ColladaModel model(PlayerBodyRenderable body) {
        if(!Avatar2Mod.usesAvatar(body) || body.isSwimming()) return null;
        ColladaModel model=((ColladaModelData)body.getModelWrapper().getModelData()).getModel();
        return AnimationLab.gameMotion(model)?model:null;
    }
    private static State state(ColladaModel model) {
        State s=STATES.get(model);
        if(s==null){s=new State();STATES.put(model,s);}return s;
    }
    /** Called at the native local movement phase read, before modulo and sounds. */
    public static synchronized float phase(PlayerBodyRenderable body,float nativePhase) {
        ColladaModel model=model(body);if(model==null)return nativePhase;
        State s=state(model);float speed=body.getMovementData().getWalkSpeed();
        return s.phase(nativePhase,speed,AvatarHeightPrototype.scale(model),System.nanoTime());
    }
    /** Only the animation selector sees a small speed during a stationary turn. */
    public static synchronized float animationSpeed(PlayerBodyRenderable body,float speed) {
        ColladaModel model=model(body);if(model==null)return speed;
        State s=state(model);s.stationary=Math.abs(speed)<=.002f;
        if(Math.abs(speed)>.0001f && Math.abs(speed)<=.002f)return Math.copySign(.0021f,speed);
        return s.stationary && s.turning(System.nanoTime())?.04f:speed;
    }
    public static synchronized void begin(PlayerBodyRenderable body) {
        ColladaModel model=model(body);if(model==null)return;
        State s=state(model);
        if(Math.abs(body.getMovementData().getWalkSpeed())<=.0001f && !s.turning(System.nanoTime())) {
            s.moving=false;s.phaseAt=System.nanoTime();
        }
    }
    /** A native idle transition may keep reporting 'not walking'. Publish the
     * movement immediately and leave the visible crossfade to the worker.
     */
    public static synchronized boolean startsWithoutIdleWait(PlayerBodyRenderable body,
            com.wurmonline.client.renderer.model.AnimationData clip) {
        ColladaModel model=model(body);
        if(model==null || !(clip instanceof ColladaAnimation) || body.hasTarget())return false;
        if(!AvatarWalkMotion.isWalk(clip.getName()) && !AvatarWalkMotion.isRun(clip.getName()))return false;
        com.wurmonline.client.renderer.model.AnimationData action=body.getCurrentAnimation();
        if(action!=null && !AvatarWalkMotion.isIdle(action.getName())
                && !AvatarWalkMotion.isWalk(action.getName()) && !AvatarWalkMotion.isRun(action.getName()))return false;
        ColladaAnimation idle=body.getCurrentReadyOrIdleAnimation();
        if(idle!=null && AvatarWalkMotion.isIdle(idle.getName()))idle.resetBlending();
        return true;
    }
    public static synchronized float yaw(PlayerBodyRenderable body,float nativeYaw,boolean onFoot) {
        ColladaModel model=model(body);if(model==null)return nativeYaw;
        State s=state(model);
        if(!onFoot){s.resetHeading(nativeYaw,System.nanoTime());return nativeYaw;}
        return s.heading(nativeYaw,System.nanoTime());
    }
    /** Native FK uses a 200 ms crossfade. Local idle/stride transitions use 80 ms. */
    public static synchronized float transitionDelta(ColladaAnimation a,ColladaAnimation b,float delta) {
        if(a==null || b==null || a.getModel()!=b.getModel() || !STATES.containsKey(a.getModel())
                || !AnimationLab.gameMotion(a.getModel()))return delta;
        boolean idleA=AvatarWalkMotion.isIdle(a.getName()),idleB=AvatarWalkMotion.isIdle(b.getName());
        boolean strideA=AvatarWalkMotion.isWalk(a.getName())||AvatarWalkMotion.isRun(a.getName());
        boolean strideB=AvatarWalkMotion.isWalk(b.getName())||AvatarWalkMotion.isRun(b.getName());
        return idleA&&strideB || idleB&&strideA ? delta*2.5f:delta;
    }
    /** Runs on the worker after each native sample, before crossfade and skinning. */
    public static synchronized void sampled(ColladaAnimation clip,boolean upperOnly,boolean lowerOnly) {
        if(clip==null || !AnimationLab.gameMotion(clip.getModel()))return;
        State s=STATES.get(clip.getModel());if(s==null || !s.turning(System.nanoTime()))return;
        if(!AvatarWalkMotion.isWalk(clip.getName()) && !AvatarWalkMotion.isRun(clip.getName())
                && !AvatarWalkMotion.isIdle(clip.getName()))return;
        ColladaModel model=clip.getModel();
        // Small repositioning steps while stationary. A moving stride stays intact.
        if(s.stationary && AvatarWalkMotion.isWalk(clip.getName())) {
            for(String name:new String[]{"thigh_l","thigh_r","calf_l","calf_r","foot_l","foot_r","ball_l","ball_r",
                    "clavicle_l","clavicle_r","upperarm_l","upperarm_r","lowerarm_l","lowerarm_r","hand_l","hand_r"}) {
                Joint j=model.getJoint(name);if(!selected(j,upperOnly,lowerOnly))continue;
                Matrix4f local=j.getAnimationMatrix();
                Matrix4f standing=AvatarWalkMotion.idleLocal(model,name);if(standing==null)continue;
                Quaternion rest=new Quaternion().fromRotationMatrix(standing);
                Quaternion pose=new Quaternion().fromRotationMatrix(local);
                boolean arm=name.startsWith("clavicle_")||name.startsWith("upperarm_")||name.startsWith("lowerarm_")||name.startsWith("hand_");
                Quaternion mixed=new Quaternion().slerp(rest,pose,arm?.10f:.30f);
                local.setRotationQuaternion(mixed);clip.setAnimationMatrix(j.getBoneIndex(),new Matrix4f(local));
            }
        }
        // Upper torso leads the feet by at most ten degrees, in each bone's rest axes.
        float twist=Math.max(-10,Math.min(10,(float)Math.toDegrees(s.lag)*.18f));
        for(String name:new String[]{"spine_02","spine_03"}) {
            Joint j=model.getJoint(name);if(!selected(j,upperOnly,lowerOnly))continue;
            Matrix4f rest=restGlobal(j),local=j.getAnimationMatrix();
            Matrix4f rotation=new Matrix4f();
            rest.setTranslation(0,0,0);
            Vector3f axis=rest.invert().mult(new Vector3f(0,1,0)).normalize();
            rotation.fromAngleAxis((float)Math.toRadians(twist*.5f),axis);
            Matrix4f result=local.mult(rotation);result.setTranslation(local.m03,local.m13,local.m23);
            local.set(result);clip.setAnimationMatrix(j.getBoneIndex(),new Matrix4f(local));
        }
    }
    private static boolean selected(Joint j,boolean upper,boolean lower) {
        return j!=null && !(upper&&!j.isBlendOrChildOfBlend() || lower&&j.isBlendOrChildOfBlend());
    }
    private static Matrix4f restGlobal(Joint j) {
        return j.getParent()==null?new Matrix4f(j.getBindmatrixOffset()):restGlobal(j.getParent()).mult(j.getBindmatrixOffset());
    }
    /** Pure clock state; tests drive timestamps and unwrapped native distance. */
    static final class State {
        float phase,raw,heading,lag;long phaseAt,headingAt,turnAt;boolean moving,hasHeading,stationary;
        float phase(float nextRaw,float speed,float scale,long now) {
            boolean active=Math.abs(speed)>.0001f || turning(now);
            if(!active){raw=nextRaw;phaseAt=now;moving=false;return phase;}
            if(!moving){moving=true;raw=nextRaw;phaseAt=now;phase=(phase+(speed<0?-.08f:.08f)+1)%1;return phase;}
            double dt=Math.max(0,Math.min(.10,(now-phaseAt)*1e-9));phaseAt=now;
            float distance=Math.abs(nextRaw-raw);raw=nextRaw;
            scale=Math.max(.65f,Math.min(1.3f,scale));
            double maximum=1.8/Math.sqrt(scale),minimum=Math.abs(speed)>.16f?1.1:.65;
            double advance=Math.max(minimum*dt,Math.min(maximum*dt,distance/scale));
            phase=(float)((phase+(speed<0?-advance:advance)+1)%1);return phase;
        }
        void resetHeading(float yaw,long now){heading=yaw;lag=0;headingAt=now;turnAt=0;hasHeading=true;}
        float heading(float desired,long now) {
            if(!hasHeading || now-headingAt>1_000_000_000L){resetHeading(desired,now);return desired;}
            double dt=Math.max(0,Math.min(.10,(now-headingAt)*1e-9));headingAt=now;
            float error=wrap(desired-heading),limit=(float)(Math.toRadians(220)*dt);
            float step=Math.max(-limit,Math.min(limit,error));heading=wrap(heading+step);lag=wrap(desired-heading);
            if(Math.abs(error)>Math.toRadians(3))turnAt=now;
            return heading;
        }
        boolean turning(long now){return turnAt!=0 && now-turnAt<120_000_000L;}
        private static float wrap(float a){return (float)Math.atan2(Math.sin(a),Math.cos(a));}
    }
}
