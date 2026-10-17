package org.avatar2.client;

import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.cell.AttachedCellRenderable;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.animation.AvatarWalkMotion;
import com.wurmonline.client.renderer.model.collada.animation.ColladaAnimation;
import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.renderer.model.collada.math.Quaternion;
import com.wurmonline.math.Vector3f;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

/** An asymmetric walk/idle carry: elbow beside the torso, shield along the flank. */
public final class AvatarShieldWalk {
    // Pinned Large/MediumMetalShield native offset rotation applied to their +Y face axis.
    private static final Vector3f METAL_FACE = new Vector3f(.059390984f, .9396926f, -.33682415f);
    // Native front strap contact points, transformed by each pinned WOM item offset.
    private static final Fit LARGE_METAL = new Fit(METAL_FACE, new Vector3f(.14120884f,-.05400376f,.04793226f), 0);
    private static final Fit MEDIUM_METAL = new Fit(METAL_FACE, new Vector3f(.14377315f,-.05310410f,.03786677f), 0);
    private static final Fit SMALL_METAL = new Fit(new Vector3f(.09472816f,.9834581f,-.15439157f),
            new Vector3f(.13257278f,-.0730362f,.02979788f), -5);
    private static final Map<ColladaModel, Carry> EQUIPPED =
            Collections.synchronizedMap(new WeakHashMap<ColladaModel, Carry>());
    private AvatarShieldWalk() { }

    public static void reconcile(PlayerBodyRenderable body, ColladaModel model) {
        AttachedCellRenderable shield = body.getEquipment(11);
        ModelResourceWrapper item = shield == null ? null : shield.getModelWrapper();
        String path = item == null || !item.isLoaded() || item.getResourceUrl() == null
                ? null : item.getResourceUrl().getFilePath();
        equipped(model, path, body.isShowEquipmentInHands()
                && body.getEquippedTool() == null && !body.isSwimming());
    }

    /** Slot 11 supplies the shield type; the base arm pose applies to every shield. */
    public static void equipped(ColladaModel model, String path, boolean visible) {
        if (model == null) return;
        if (!visible || path == null || model.getJoint("upperarm_l") == null
                || model.getJoint("lowerarm_l") == null || model.getJoint("hand_l") == null) {
            EQUIPPED.remove(model); return;
        }
        Carry carry = EQUIPPED.get(model);
        if (carry == null) { carry = new Carry(model); EQUIPPED.put(model, carry); }
        path = resourcePath(path);
        carry.fit = "weapon/Shields/LargeMetalShield.wom".equalsIgnoreCase(path) ? LARGE_METAL
                : "weapon/Shields/MediumMetalShield.wom".equalsIgnoreCase(path) ? MEDIUM_METAL
                : "weapon/Shields/SmallMetalShield.wom".equalsIgnoreCase(path) ? SMALL_METAL : null;
    }

    public static boolean isLargeMetalShield(String path) {
        return "weapon/Shields/LargeMetalShield.wom".equalsIgnoreCase(resourcePath(path));
    }

    private static String resourcePath(String path) {
        if (path == null) return null;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        return path.replace('\\', '/');
    }

    public static void sampled(ColladaAnimation clip, boolean upperOnly, boolean lowerOnly) {
        if (clip == null) return;
        Carry carry = EQUIPPED.get(clip.getModel());
        if (carry == null) return;
        boolean activeCarry = isCarry(clip);
        String[] names = {"upperarm_l", "lowerarm_l", "hand_l"};
        for (int i = 0; i < names.length; i++) {
            Joint joint = clip.getModel().getJoint(names[i]);
            if (!selected(joint, upperOnly, lowerOnly)) continue;
            if (i == 1) {
                carry.carryWeight=activeCarry?1:0;
                Matrix4f reference=new Matrix4f(joint.getAnimationMatrix());
                if(activeCarry)poseRotation(reference,carry.unrolledForearm);
                carry.referenceForearm.put(clip.getName(),reference);
                carry.socketCounter=orientation(joint.getAnimationMatrix()).invert().mult(orientation(reference));
            }
            if (!activeCarry) continue;
            Matrix4f sampled = joint.getAnimationMatrix();
            Matrix4f rotation = i == 0 ? carry.upper : i == 1 ? carry.aimForearm(clip.getModel()) : carry.hand;
            // Bend the wrist towards the loop without reversing the accepted fist roll.
            poseRotation(sampled,rotation);
            if(i==1)carry.socketCounter=orientation(sampled).invert()
                    .mult(orientation(carry.referenceForearm.get(clip.getName())));
            clip.setAnimationMatrix(joint.getBoneIndex(), new Matrix4f(sampled));
        }
    }

    /** Match the socket fit to Wurm's final arm crossfade, independent of sampler order. */
    public static void blended(ColladaAnimation a, ColladaAnimation b, ColladaModel model,
                               float fraction, boolean upperOnly, boolean lowerOnly) {
        Carry carry = EQUIPPED.get(model);
        Joint joint = model.getJoint("lowerarm_l");
        // Native blendAnimation applies both filters, unlike the sampler's first-group priority.
        if (carry == null || joint == null || upperOnly && !joint.isBlendOrChildOfBlend()
                || lowerOnly && joint.isBlendOrChildOfBlend()) return;
        float t = Math.max(0, Math.min(1, fraction));
        carry.carryWeight=(isCarry(a)?1-t:0)+(isCarry(b)?t:0);
        if(a!=null&&b!=null) {
            Matrix4f ar=isCarry(a)?carry.referenceForearm.get(a.getName()):a.getAnimationMatrix(joint.getBoneIndex());
            Matrix4f br=isCarry(b)?carry.referenceForearm.get(b.getName()):b.getAnimationMatrix(joint.getBoneIndex());
            if(ar!=null&&br!=null) {
                Quaternion qa=new Quaternion(),qb=new Quaternion(),q=new Quaternion();
                ar.toRotationQuat(qa);br.toRotationQuat(qb);q.slerp(qa,qb,t);
                Matrix4f reference=new Matrix4f();reference.setRotationQuaternion(q);
                carry.socketCounter=orientation(joint.getAnimationMatrix()).invert().mult(reference);
            }
        }
    }

    /** Publish after successful skinning; renderer never consumes an intermediate sample. */
    public static void publishFrame(ColladaModel model) {
        Carry carry = EQUIPPED.get(model);
        if (carry == null) return;
        Fit fit = carry.fit;
        Matrix4f ordinary = AvatarEquipmentSockets.unfittedShieldFrame(model);
        if (ordinary == null) return;
        Matrix4f fitted = new Matrix4f(ordinary);
        applyFit(model,carry,fit,carry.carryWeight,carry.socketCounter,fitted);
        carry.completed = new Completed(fit, ordinary.invert().mult(fitted));
    }

    static void fitShield(ColladaModel model, Matrix4f frame) {
        Carry carry = EQUIPPED.get(model);
        Completed completed = carry == null ? null : carry.completed;
        if (completed != null && completed.fit == carry.fit) frame.set(frame.mult(completed.local));
    }

    /** Worker-only calculation. Render readers consume one immutable completed local fit. */
    private static void applyFit(ColladaModel model,Carry carry,Fit fit,float weight,Matrix4f counter,Matrix4f frame) {
        if (weight == 0) return;
        Matrix4f elbow=model.getJoint("lowerarm_l").getBindMatrix();
        Matrix4f reference=orientation(elbow).mult(counter);
        Vector3f forward=reference.mult(carry.foreAxis).normalize();
        // Counter only the forearm's new aim; retain the accepted shield placement.
        // Use the actual native blended local rotations, not a linear angle estimate.
        // This also carries Lab parent/local corrections into the attachment.
        Matrix4f turn=orientation(elbow).mult(counter).mult(orientation(elbow).invert());
        Vector3f pivot=new Vector3f(elbow.m03,elbow.m13,elbow.m23);
        Vector3f moved=turn.mult(new Vector3f(frame.m03-pivot.x,frame.m13-pivot.y,frame.m23-pivot.z));
        frame.set(turn.mult(frame));frame.setTranslation(pivot.x+moved.x,pivot.y+moved.y,pivot.z+moved.z);
        if(fit==null)return;
        if(fit.tilt!=0) {
            Vector3f contact=frame.mult(fit.contact);
            turn.fromAngleAxis((float)Math.toRadians(fit.tilt*weight),forward);
            moved=turn.mult(new Vector3f(frame.m03-contact.x,frame.m13-contact.y,frame.m23-contact.z));
            frame.set(turn.mult(frame));frame.setTranslation(contact.x+moved.x,contact.y+moved.y,contact.z+moved.z);
        }
        Vector3f face=fit.face;
        Vector3f normal=orientation(frame).mult(face).normalize();
        // Bring the rear loop towards the forearm with a small turn about the
        // front grip. The arm and the native panel dimensions stay intact.
        Vector3f contact=frame.mult(fit.contact);
        turn.fromAngleAxis((float)Math.toRadians(3*weight),forward.cross(normal).normalize());
        moved=turn.mult(new Vector3f(frame.m03-contact.x,frame.m13-contact.y,frame.m23-contact.z));
        frame.set(turn.mult(frame));frame.setTranslation(contact.x+moved.x,contact.y+moved.y,contact.z+moved.z);
        normal=orientation(frame).mult(face).normalize();
        // Fit the current natural fist, rather than the old half-turned hand's
        // landmark. A small clearance places the strap along the curled fingers.
        Matrix4f index=model.getJoint("index_02_l").getBindMatrix();
        Matrix4f middle=model.getJoint("middle_02_l").getBindMatrix();
        Vector3f fingers=new Vector3f((index.m03+middle.m03)*.5f,
                (index.m13+middle.m13)*.5f,(index.m23+middle.m23)*.5f);
        // Medium's rear rim needs 3 mm more room on the tall Bodybuilder arm.
        float allowance=fit==MEDIUM_METAL?.017f:.014f;
        Vector3f target=fingers.add(normal.mult(allowance)).add(forward.mult(.012f));
        Vector3f shift=target.subtract(frame.mult(fit.contact)).mult(weight);
        frame.m03+=shift.x;frame.m13+=shift.y;frame.m23+=shift.z;
        if(fit==LARGE_METAL) {
            // The marked front/rear axes need a 2 cm translation towards the
            // elbow, not another panel rotation or a change of the arm pose.
            Vector3f wrist=point(model.getJoint("hand_l").getBindMatrix());
            Vector3f towardsElbow=pivot.subtract(wrist).normalize().mult(.020f*weight);
            frame.m03+=towardsElbow.x;frame.m13+=towardsElbow.y;frame.m23+=towardsElbow.z;
        }
    }

    private static boolean selected(Joint joint, boolean upper, boolean lower) {
        return joint != null && !(upper ? !joint.isBlendOrChildOfBlend() : lower && joint.isBlendOrChildOfBlend());
    }
    private static boolean isCarry(ColladaAnimation clip) {
        return clip!=null&&(AvatarWalkMotion.isWalk(clip.getName())||AvatarWalkMotion.isIdle(clip.getName()));
    }
    private static void poseRotation(Matrix4f sampled,Matrix4f rotation) {
        float sx=length(sampled.m00,sampled.m10,sampled.m20),sy=length(sampled.m01,sampled.m11,sampled.m21),sz=length(sampled.m02,sampled.m12,sampled.m22);
        sampled.m00=rotation.m00*sx;sampled.m10=rotation.m10*sx;sampled.m20=rotation.m20*sx;
        sampled.m01=rotation.m01*sy;sampled.m11=rotation.m11*sy;sampled.m21=rotation.m21*sy;
        sampled.m02=rotation.m02*sz;sampled.m12=rotation.m12*sz;sampled.m22=rotation.m22*sz;
    }
    private static float length(float x, float y, float z) { return (float)Math.sqrt(x*x+y*y+z*z); }
    private static Vector3f point(Joint joint) {
        Matrix4f m=joint.getOriginalBindMatrix(); return new Vector3f(m.m03,m.m13,m.m23);
    }
    private static Vector3f point(Matrix4f m) { return new Vector3f(m.m03,m.m13,m.m23); }
    private static Matrix4f orientation(Matrix4f source) {
        Matrix4f result=new Matrix4f(source); result.setTranslation(0,0,0); return result;
    }
    private static Matrix4f basis(Vector3f direction, Vector3f normal) {
        Vector3f x=direction.normalize(), y=normal.subtract(x.mult(x.dot(normal))).normalize(), z=x.cross(y);
        Matrix4f m=new Matrix4f();
        m.m00=x.x;m.m10=x.y;m.m20=x.z;m.m01=y.x;m.m11=y.y;m.m21=y.z;m.m02=z.x;m.m12=z.y;m.m22=z.z;
        return m;
    }
    private static Vector3f gripLocal(ColladaModel model,Matrix4f handRotation) {
        Matrix4f hand=new Matrix4f(model.getJoint("hand_l").getAnimationMatrix());
        poseRotation(hand,handRotation);
        return hand.mult(fingerLocal(model,"index_02_l").add(fingerLocal(model,"middle_02_l")).mult(.5f));
    }
    private static Vector3f fingerLocal(ColladaModel model,String name) {
        Joint joint=model.getJoint(name);Matrix4f local=new Matrix4f(joint.getAnimationMatrix());
        while((joint=joint.getParent())!=null&&!"hand_l".equals(joint.getName()))
            local=joint.getAnimationMatrix().mult(local);
        return new Vector3f(local.m03,local.m13,local.m23);
    }
    private static Matrix4f aim(Vector3f from,Vector3f to) {
        from=from.normalize();to=to.normalize();Vector3f axis=from.cross(to);
        Matrix4f result=new Matrix4f();float cosine=Math.max(-1,Math.min(1,from.dot(to)));
        if(axis.length()>.000001f)result.fromAngleAxis((float)Math.acos(cosine),axis.normalize());
        return result;
    }
    private static final class Carry {
        final Matrix4f upper,hand,neutralHand,unrolledForearm,attachmentReference;
        final Vector3f foreAxis,faceUpper;
        final Map<String,Matrix4f> referenceForearm=new HashMap<>();
        Matrix4f socketCounter=new Matrix4f();
        volatile Fit fit;
        volatile float carryWeight;
        volatile Completed completed;
        Carry(ColladaModel model) {
            Joint shoulder=model.getJoint("upperarm_l"), elbow=model.getJoint("lowerarm_l"), hand=model.getJoint("hand_l");
            Matrix4f reference=AvatarEquipmentSockets.referenceShieldFrame(model);
            Vector3f normal=new Vector3f(reference.m00*METAL_FACE.x+reference.m01*METAL_FACE.y+reference.m02*METAL_FACE.z,
                    reference.m10*METAL_FACE.x+reference.m11*METAL_FACE.y+reference.m12*METAL_FACE.z,
                    reference.m20*METAL_FACE.x+reference.m21*METAL_FACE.y+reference.m22*METAL_FACE.z);
            Vector3f forward=new Vector3f(0,0,1);
            Matrix4f u=basis(new Vector3f(.10f,-.96f,-.28f),forward)
                    .mult(basis(point(elbow).subtract(point(shoulder)),forward).invert())
                    .mult(orientation(shoulder.getOriginalBindMatrix()));
            Matrix4f l=basis(new Vector3f(0,-.40f,.9165f),new Vector3f(1,0,0))
                    .mult(basis(point(hand).subtract(point(elbow)),normal).invert())
                    .mult(orientation(elbow.getOriginalBindMatrix()));
            upper=orientation(shoulder.getParent().getOriginalBindMatrix()).invert().mult(u);
            unrolledForearm=u.invert().mult(l);
            neutralHand=orientation(elbow.getOriginalBindMatrix()).invert().mult(orientation(hand.getOriginalBindMatrix()));
            foreAxis=orientation(elbow.getOriginalBindMatrix()).invert().mult(point(hand).subtract(point(elbow))).normalize();
            Vector3f faceLocal=orientation(elbow.getOriginalBindMatrix()).invert().mult(normal);
            faceUpper=unrolledForearm.mult(faceLocal).normalize();
            attachmentReference=new Matrix4f();attachmentReference.fromAngleAxis((float)Math.PI,foreAxis);
            // The reference retains the accepted ITEM placement; it never rotates a body joint.
            Matrix4f bend=new Matrix4f();bend.fromAngleAxis((float)Math.toRadians(30),
                    unrolledForearm.mult(foreAxis).cross(faceUpper).normalize());
            this.hand=unrolledForearm.invert().mult(bend).mult(unrolledForearm).mult(neutralHand);
        }
        Matrix4f aimForearm(ColladaModel model) {
            Vector3f source=unrolledForearm.mult(gripLocal(model,hand));
            Vector3f target=unrolledForearm.mult(attachmentReference.mult(gripLocal(model,neutralHand)))
                    .subtract(faceUpper.mult(.015f));
            return aim(source,target).mult(unrolledForearm);
        }
    }

    private static final class Completed {
        final Fit fit;
        final Matrix4f local;
        Completed(Fit fit, Matrix4f local) { this.fit = fit; this.local = local; }
    }

    private static final class Fit {
        final Vector3f face, contact;
        final float tilt;
        Fit(Vector3f face,Vector3f contact,float tilt) {
            this.face=face;this.contact=contact;this.tilt=tilt;
        }
    }
}
