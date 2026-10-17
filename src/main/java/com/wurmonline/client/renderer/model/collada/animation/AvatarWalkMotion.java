package com.wurmonline.client.renderer.model.collada.animation;

import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Restores native Avatar weight transfer after Wurm clamps bone translations.
 * Lives beside KeyFrame to read the imported samples without reflection.
 * Root/world placement, all rotations and the previous jog are untouched.
 */
public final class AvatarWalkMotion {
    private static final Map<ColladaModel, Boolean> MODELS =
            Collections.synchronizedMap(new WeakHashMap<ColladaModel, Boolean>());
    private static final Map<ColladaAnimation, Centre> CENTRES =
            Collections.synchronizedMap(new WeakHashMap<ColladaAnimation, Centre>());

    private AvatarWalkMotion() { }

    public static void register(ColladaModel model) {
        if (model != null && model.getJoint("pelvis") != null
                && model.getJoint("foot_l") != null && model.getJoint("foot_r") != null)
            MODELS.put(model, Boolean.TRUE);
    }

    /** Cached resource instances must inherit the imported body's registration. */
    public static void copied(ColladaModel source, ColladaModel copy) {
        if (MODELS.containsKey(source)) register(copy);
    }

    public static boolean isWalk(String name) {
        return "walk".equals(name) || "walk.p1".equals(name)
                || "gait.walk.relaxed".equals(name);
    }

    public static boolean isRun(String name) {
        return "run".equals(name) || "run.p1".equals(name) || "gait.sprint".equals(name)
                || "gait.jog.clean".equals(name) || "gait.sprint.clean".equals(name);
    }

    public static boolean isIdle(String name) {
        return "idle".equals(name) || "idle.p1".equals(name);
    }

    /** Immutable authored standing pose, rather than the arms-out skeletal bind. */
    public static Matrix4f idleLocal(ColladaModel model, String jointName) {
        ColladaAnimation idle=model.getColladaAnimation("idle");
        if(idle==null || idle.getKeyFrames()==null || idle.getKeyFrames().length==0)return null;
        KeyFrame first=idle.getKeyFrames()[0];String[] names=first.getJointIds();
        for(int i=0;i<names.length;i++)if(jointName.equals(names[i]))
            return new Matrix4f(first.getTransformation(i));
        return null;
    }

    /** Invoked after the original sampler, before Wurm blends the cached poses. */
    public static void sampled(float phase, ColladaAnimation clip,
                               boolean upperOnly, boolean lowerOnly) {
        if (clip == null || !(isWalk(clip.getName()) || isRun(clip.getName()) || isIdle(clip.getName()))) return;
        ColladaModel model = clip.getModel();
        if (!MODELS.containsKey(model)) return;
        Joint pelvis = model.getJoint("pelvis");
        if ((upperOnly && !pelvis.isBlendOrChildOfBlend())
                || (lowerOnly && pelvis.isBlendOrChildOfBlend())) return;
        Matrix4f nativeOffset = pelvis.getBindmatrixOffset();
        if (nativeOffset == null) return;
        Centre centre = CENTRES.get(clip);
        if (centre == null) {
            centre = new Centre(clip);
            if (!centre.valid) return;
            CENTRES.put(clip, centre);
        }
        float time = Math.max(0, Math.min(1, phase)) * clip.getLength();
        KeyFrame a = clip.getCurrentKeyFrame(time), b = clip.getNextKeyFrame(time);
        if (a == null || b == null) return;
        Matrix4f ma = pelvis(a), mb = pelvis(b);
        if (ma == null || mb == null) return;
        float t = b.getTime() == a.getTime() ? 0 :
                Math.max(0, Math.min(1, (time-a.getTime())/(b.getTime()-a.getTime())));
        float scale = centre.height > .1f ? nativeOffset.m13 / centre.height : 1;
        Matrix4f local = pelvis.getAnimationMatrix();
        if (isRun(clip.getName())) {
            // Preserve imported runner bob as well as sway. Grounding every
            // running frame would erase the source's airborne interval.
            local.setTranslation(nativeOffset.m03 + (lerp(ma.m03, mb.m03, t)-centre.x)*scale,
                    nativeOffset.m13 + (lerp(ma.m13, mb.m13, t)-centre.height)*scale,
                    nativeOffset.m23 + (lerp(ma.m23, mb.m23, t)-centre.z)*scale);
            clip.setAnimationMatrix(pelvis.getBoneIndex(), local);
            return;
        }
        // Idle also loses its authored pelvis translation in Wurm's sampler.
        // Restore centred horizontal transfer, then solve support height from
        // the actual leg pose for both grounded clips. This recovers breathing
        // motion without adding a synthetic sway or lifting planted feet.
        local.setTranslation(nativeOffset.m03 + (lerp(ma.m03, mb.m03, t)-centre.x)*scale,
                nativeOffset.m13,
                nativeOffset.m23 + (lerp(ma.m23, mb.m23, t)-centre.z)*scale);
        // Solve the native support height from the actual accepted leg pose.
        // No artificial pelvis sine, leg stretch, knee-axis or torso correction.
        Map<Joint, Matrix4f> globals = new IdentityHashMap<Joint, Matrix4f>();
        if (isIdle(clip.getName())) {
            // Idle plants both feet. Donor translation alone is insufficient
            // after retargeting onto different leg lengths and bind angles.
            // Recover the pelvis offset from the accepted native leg pose so
            // the mean ankle position remains at its original model-space anchor.
            float dx=0, dz=0;
            for (String side : new String[]{"l","r"}) {
                Joint foot=model.getJoint("foot_"+side);
                Matrix4f current=global(foot,globals), rest=foot.getOriginalBindMatrix();
                dx+=(rest.m03-current.m03)*.5f;
                dz+=(rest.m23-current.m23)*.5f;
            }
            Matrix4f parent=global(pelvis.getParent(),globals).invert();
            local.m03+=parent.m00*dx+parent.m02*dz;
            local.m13+=parent.m10*dx+parent.m12*dz;
            local.m23+=parent.m20*dx+parent.m22*dz;
            globals.clear();
        }
        boolean idle = isIdle(clip.getName());
        float left = soleHeight(model, "l", globals, idle), right = soleHeight(model, "r", globals, idle);
        float ground = idle ? Math.min(left, right) : smoothMin(left, right, .002f);
        if (Float.isNaN(ground) || Float.isInfinite(ground) || Math.abs(ground) > .15f) {
            local.setTranslation(nativeOffset.m03, nativeOffset.m13, nativeOffset.m23);
            return; // malformed grounded poses fail closed, not a large teleport
        }
        Matrix4f parent = global(pelvis.getParent(), globals).invert();
        // Convert a model-Y displacement to the pelvis parent's axes (direction).
        local.m03 -= parent.m01 * ground;
        local.m13 -= parent.m11 * ground;
        local.m23 -= parent.m21 * ground;
        clip.setAnimationMatrix(pelvis.getBoneIndex(), local);
    }

    private static float lerp(float a, float b, float t) { return a+(b-a)*t; }

    private static Matrix4f pelvis(KeyFrame frame) {
        String[] names = frame.getJointIds();
        for (int i=0; i<names.length; i++)
            if ("pelvis".equals(names[i])) return frame.getTransformation(i);
        return null;
    }

    private static Matrix4f global(Joint joint, Map<Joint, Matrix4f> cache) {
        if (joint == null) return new Matrix4f();
        Matrix4f result = cache.get(joint);
        if (result != null) return result;
        Matrix4f local = joint.getAnimationMatrix();
        if (local.isIdentity()) local = joint.getParent() == null ?
                joint.getOriginalBindMatrix() : joint.getBindmatrixOffset();
        if (local == null) local = new Matrix4f();
        result = global(joint.getParent(), cache).mult(local);
        cache.put(joint, result);
        return result;
    }

    private static float soleHeight(ColladaModel model, String side, Map<Joint, Matrix4f> globals, boolean planted) {
        Joint foot = model.getJoint("foot_"+side), ball = model.getJoint("ball_"+side);
        Matrix4f footRest = foot.getOriginalBindMatrix(), ballRest = ball.getOriginalBindMatrix();
        Matrix4f heelSkin = global(foot, globals).mult(foot.getOriginalInvertBindMatrix());
        Matrix4f toeSkin = global(ball, globals).mult(ball.getOriginalInvertBindMatrix());
        float heel = pointY(heelSkin, footRest.m03, footRest.m23-.025f);
        float toe = pointY(toeSkin, ballRest.m03, ballRest.m23);
        return planted ? Math.min(heel, toe) : smoothMin(heel, toe, .001f);
    }

    /** Rest-space sole landmarks, Y=0. These approximate contact, not terrain IK. */
    private static float pointY(Matrix4f skin, float x, float z) {
        return skin.m10*x + skin.m12*z + skin.m13;
    }

    private static float smoothMin(float a, float b, float epsilon) {
        float d=a-b;
        return .5f*(a+b-(float)Math.sqrt(d*d+epsilon*epsilon));
    }

    private static final class Centre {
        final float x, z, height;
        final boolean valid;
        Centre(ColladaAnimation clip) {
            float sx=0, sz=0, sy=0; int count=0;
            KeyFrame[] frames=clip.getKeyFrames();
            // Closing duplicate must not bias the centre toward phase zero.
            for (int i=0; i<frames.length-1; i++) {
                Matrix4f m=pelvis(frames[i]);
                if (m!=null) { sx+=m.m03; sz+=m.m23; sy+=m.m13; count++; }
            }
            x=count==0?0:sx/count; z=count==0?0:sz/count;
            height=count==0?0:sy/count; valid=count>0;
        }
    }
}
