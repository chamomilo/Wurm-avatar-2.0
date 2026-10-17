package org.avatar2.client;

import com.wurmonline.client.renderer.model.ModelDataBounds;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector;
import org.avatar2.core.HeightLimits;
import java.util.*;

/** Local profile/Lab height. Rest/bind inputs remain immutable; scale follows FK. */
public final class AvatarHeightPrototype {
    private static final Map<ColladaModel, State> STATES = new WeakHashMap<>();
    private AvatarHeightPrototype() { }

    public static void register(ColladaModel model) {
        synchronized (AvatarHeightPrototype.class) { if (STATES.containsKey(model)) return; }
        // Native bounds enter the geometry hook. Never do that under the height monitor.
        State state=new State(model.calculateBoundingBox(),model.getJoint("NullCamera0"));
        synchronized (AvatarHeightPrototype.class) { if (!STATES.containsKey(model)) STATES.put(model,state); }
    }

    /** AnimationLab has just restored the previous unscaled engine matrices. */
    public static synchronized void prepare(ColladaModel model) {
        State state = STATES.get(model);
        if (state != null) state.applied = 1;
    }

    public static synchronized void apply(ColladaModel model, float scale) {
        State state = STATES.get(model);
        if (state == null) throw new IllegalStateException("Height model is not registered");
        state.limits.validateScale(scale);
        state.unscaled.clear();
        for (Joint joint : model.getJoints().values()) {
            Matrix4f bind = joint.getBindMatrix();
            state.unscaled.put(joint.getName(), new Matrix4f(bind));
            if (scale == 1) continue;
            bind.m00 *= scale; bind.m01 *= scale; bind.m02 *= scale; bind.m03 *= scale;
            bind.m10 *= scale; bind.m11 *= scale; bind.m12 *= scale;
            bind.m13 = state.floor + (bind.m13 - state.floor)*scale;
            bind.m20 *= scale; bind.m21 *= scale; bind.m22 *= scale; bind.m23 *= scale;
        }
        state.applied = scale;
    }

    /** Copies made during a preview start with the unscaled pose and independent state. */
    public static synchronized void copied(ColladaModel source, ColladaModel copy) {
        State old = STATES.get(source);
        if (old == null) return;
        if (old.applied != 1) for (Map.Entry<String, Matrix4f> entry : old.unscaled.entrySet()) {
            Joint joint = copy.getJoint(entry.getKey());
            if (joint != null) joint.getBindMatrix().set(entry.getValue());
        }
        STATES.put(copy, new State(old.floor, old.height,copy.getJoint("NullCamera0")));
    }

    /** Camera readers see only the last completed skinning pose, never rollback/FK work. */
    public static void publishCamera(ColladaModel model) {
        float[] delta=AvatarGeometryPrototype.headCameraDelta(model);
        synchronized(AvatarHeightPrototype.class) {
            State state=STATES.get(model);
            if(state==null || state.cameraRest==null) return;
            Joint camera=model.getJoint("NullCamera0"), head=model.getJoint("head");
            if(camera!=null) {
                Matrix4f skin=head==null?null:head.getBindMatrix().mult(head.getOriginalInvertBindMatrix());
                state.camera=new CameraFrame(state,camera.getBindMatrix(),state.applied,delta,skin);
            }
            state.completedScale=state.applied;
        }
    }

    /** Wurm's paper-doll camera uses fixed native translations, independent of model bounds.
     * Scale those translations with the completed Avatar pose so the head retains its
     * screen position at every height. Geometry is already scaled; leave rotation intact.
     */
    public static Matrix portraitMatrix(ModelResourceWrapper wrapper, Matrix nativeMatrix) {
        if(wrapper==null || nativeMatrix==null || !(wrapper.getModelData() instanceof ColladaModelData))return nativeMatrix;
        ColladaModel model=((ColladaModelData)wrapper.getModelData()).getModel();
        float scale,floor;
        synchronized(AvatarHeightPrototype.class) {
            State state=STATES.get(model);if(state==null || state.completedScale==1)return nativeMatrix;
            scale=state.completedScale;floor=state.floor;
        }
        // renderPaperDoll created this matrix exclusively for its current submission.
        nativeMatrix.m30(nativeMatrix._m30()*scale);
        nativeMatrix.m31(nativeMatrix._m31()*scale+floor*(scale-1));
        nativeMatrix.m32(nativeMatrix._m32()*scale);
        return nativeMatrix;
    }

    public static synchronized Vector cameraOffset(ColladaModel model,Vector result,Object name) {
        State state=STATES.get(model);
        if(result!=null && state!=null && state.camera!=null && isCamera(model,name))
            state.camera.offset(result,false);
        return result;
    }

    private static boolean isCamera(ColladaModel model,Object name) {
        if(name instanceof Integer) {
            int index=(Integer)name;String[] ids=model.getJointIds();
            return ids!=null && index>=0 && index<ids.length && "NullCamera0".equalsIgnoreCase(ids[index]);
        }
        return name instanceof String && "NullCamera0".equalsIgnoreCase((String)name);
    }

    public static synchronized boolean boundsChanged(ColladaModel model) {
        State state = STATES.get(model);
        if (state == null || state.boundsScale == state.applied) return false;
        state.boundsScale = state.applied;
        return true;
    }

    public static synchronized float scale(ColladaModel model) {
        State state = STATES.get(model);
        return state == null ? 1 : state.applied;
    }

    public static synchronized HeightLimits limits(ColladaModel model) {
        State state = STATES.get(model);
        if (state == null) throw new IllegalStateException("Height model is not registered");
        return state.limits;
    }

    /** DQ skinning represents rigid transforms. Never feed its quaternion a scale. */
    public static Matrix4f gpuBind(com.wurmonline.client.renderer.model.collada.importer.TriangleMesh mesh, Matrix4f bind, boolean inverse) {
        float scale = AvatarGeometryPrototype.heightScale(mesh);
        if (scale == 1) return bind;
        Matrix4f result = new Matrix4f(bind);
        if (inverse) {
            // The GPU vertex buffer is scaled about the origin. The animated
            // bind translation already includes the floor pivot correction.
            result.m03 *= scale; result.m13 *= scale; result.m23 *= scale;
        } else {
            result.m00 /= scale; result.m01 /= scale; result.m02 /= scale;
            result.m10 /= scale; result.m11 /= scale; result.m12 /= scale;
            result.m20 /= scale; result.m21 /= scale; result.m22 /= scale;
        }
        return result;
    }

    public static synchronized ModelDataBounds bounds(ColladaModel model, ModelDataBounds base) {
        State state = STATES.get(model);
        if (base == null || state == null || state.applied == 1) return base;
        float s = state.applied, floor = state.floor;
        return new ModelDataBounds(base.getX0()*s, floor+(base.getH0()-floor)*s, base.getY0()*s,
                base.getX1()*s, floor+(base.getH1()-floor)*s, base.getY1()*s);
    }

    // Wurm returns copies for these getters. Strip only the added uniform scale;
    // preserve the socket's original rotation/scale and its scaled position.
    public static synchronized Matrix4f attachment(ColladaModel model, Matrix4f result, Object name, boolean original) {
        State state = STATES.get(model);
        if (result == null || state == null || state.applied == 1 || !hasJoint(model, name, original)) return result;
        float scale = state.applied;
        if (original) {
            result.m03 *= scale; result.m13 = state.floor+(result.m13-state.floor)*scale; result.m23 *= scale;
        } else {
            result.m00 /= scale; result.m01 /= scale; result.m02 /= scale;
            result.m10 /= scale; result.m11 /= scale; result.m12 /= scale;
            result.m20 /= scale; result.m21 /= scale; result.m22 /= scale;
        }
        return result;
    }

    public static synchronized Vector originalOffset(ColladaModel model, Vector result, String name) {
        State state = STATES.get(model);
        if(result!=null && state!=null && state.camera!=null && isCamera(model,name)) {
            state.camera.offset(result,true);
            return result;
        }
        if (result != null && state != null && state.applied != 1 && hasJoint(model, name, true)) {
            result.vector[0] *= state.applied;
            result.vector[1] = state.floor+(result.vector[1]-state.floor)*state.applied;
            result.vector[2] *= state.applied;
        }
        return result;
    }

    public static synchronized Vector rotation(ColladaModel model, Vector result, String name) {
        State state = STATES.get(model);
        if (state != null && state.applied != 1 && result != null) {
            Matrix4f bind = state.unscaled.get(name);
            if (bind != null) {
                com.wurmonline.client.renderer.model.collada.math.Quaternion quaternion = new com.wurmonline.client.renderer.model.collada.math.Quaternion();
                bind.toRotationQuat(quaternion);
                float[] angles = quaternion.toAngles(null);
                for (int axis = 0; axis < 3; axis++) result.vector[axis] = (float)Math.toDegrees(angles[axis]);
            }
        }
        return result;
    }

    private static boolean hasJoint(ColladaModel model, Object name, boolean ignoreCase) {
        if (name instanceof Integer) {
            int index = (Integer)name;
            return model.getJointIds() != null && index >= 0 && index < model.getJointIds().length;
        }
        for (String key : model.getJoints().keySet())
            if (ignoreCase ? key.equalsIgnoreCase(String.valueOf(name)) : key.equals(String.valueOf(name))) return true;
        return false;
    }

    public static Map<String, Object> describe(ColladaModel model) {
        Map<String, Object> result = new LinkedHashMap<>();
        synchronized (AvatarHeightPrototype.class) {
            State state=STATES.get(model);if (state == null) return result;
            result.put("scale", state.applied); result.put("baselineHeightMetres", state.height);
            result.put("heightMetres", state.height*state.applied); result.put("floor", state.floor);
            result.put("heightDirts", state.height*state.applied*HeightLimits.DIRTS_PER_METRE);
            result.put("heightMetresRange", new double[]{HeightLimits.MIN_METRES,HeightLimits.MAX_METRES});
            result.put("reference", "current approved control DAE; common release reference pending calibration");
        }
        // This getter enters virtual-socket hooks, even for the native camera.
        Vector camera = model.getNullAttachmentOffset("NullCamera0", new Vector());
        result.put("cameraSocket", camera.vector.clone());
        return result;
    }

    private static final class State {
        final float floor, height;
        final HeightLimits limits;
        float applied = 1, boundsScale = 1, completedScale = 1;
        final float[] cameraRest;
        CameraFrame camera;
        // Matrices only: values must never retain the weak model key or its meshes/joints.
        final Map<String, Matrix4f> unscaled = new HashMap<>();
        State(ModelDataBounds bounds,Joint camera) { this(bounds.getH0(), bounds.getH1()-bounds.getH0(),camera); }
        State(float floor, float height,Joint camera) {
            this.floor = floor; this.height = height; this.limits = new HeightLimits(height);
            Matrix4f rest=camera==null?null:camera.getOriginalBindMatrix();
            cameraRest=rest==null?null:new float[]{rest.m03,rest.m13,rest.m23};
            if(camera!=null) this.camera=new CameraFrame(this,camera.getBindMatrix(),1);
        }
    }

    private static final class CameraFrame {
        final float x,y,z,restX,restY,restZ;
        CameraFrame(State state,Matrix4f bind,float scale) {
            this(state,bind,scale,new float[3],null);
        }
        CameraFrame(State state,Matrix4f bind,float scale,float[] delta,Matrix4f skin) {
            x=bind.m03+(skin==null?0:skin.m00*delta[0]+skin.m01*delta[1]+skin.m02*delta[2]);
            y=bind.m13+(skin==null?0:skin.m10*delta[0]+skin.m11*delta[1]+skin.m12*delta[2]);
            z=bind.m23+(skin==null?0:skin.m20*delta[0]+skin.m21*delta[1]+skin.m22*delta[2]);
            restX=(state.cameraRest[0]+delta[0])*scale;
            restY=state.floor+(state.cameraRest[1]+delta[1]-state.floor)*scale;
            restZ=(state.cameraRest[2]+delta[2])*scale;
        }
        void offset(Vector result,boolean original) {
            result.vector[0]=original?restX:x;
            result.vector[1]=original?restY:y;
            result.vector[2]=original?restZ:z;
        }
    }
}
