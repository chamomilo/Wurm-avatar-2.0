package org.avatar2.tools;

import com.wurmonline.client.renderer.model.ModelDataBounds;
import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.ColladaAnimation;
import com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics;
import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.ColladaImporter;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.renderer.model.collada.material.ColladaMaterial;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.resources.FakeResourceUrl;
import com.wurmonline.math.Vector;
import com.wurmonline.math.Vector3f;
import org.avatar2.client.AvatarAnimationBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

/** Verifies generated Collada bodies with Wurm's actual model importer. */
public final class DaeRigProbe {
    private static final List<String> REQUIRED = Arrays.asList(
            "Root", "pelvis", "spine_01", "spine_02", "spine_03",
            "neck_01", "head",
            "clavicle_r", "upperarm_r", "lowerarm_r", "hand_r",
            "clavicle_l", "upperarm_l", "lowerarm_l", "hand_l",
            "thigh_r", "calf_r", "foot_r", "ball_r",
            "thigh_l", "calf_l", "foot_l", "ball_l",
            "thumb_01_r", "index_01_r", "middle_01_r", "pinky_01_r",
            "thumb_01_l", "index_01_l", "middle_01_l", "pinky_01_l");
    private static final List<String> NEUTRAL_REFERENCE_JOINTS = Arrays.asList(
            "Root", "pelvis", "spine_01", "spine_02", "spine_03",
            "neck_01", "head", "clavicle_r", "clavicle_l");

    private DaeRigProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Pass one or more DAE files");
        for (String path : args) verify(new File(path).getAbsoluteFile());
    }

    private static void verify(File file) throws Exception {
        if (!file.isFile()) throw new IllegalArgumentException("Missing " + file);
        ColladaModel model;
        try (InputStream stream = new FileInputStream(file)) {
            model = ColladaImporter.loadModel(file.toURI().toString(), stream);
        }
        if (model == null) throw new IllegalStateException("Wurm importer returned null for " + file);
        AvatarAnimationBridge.ensureRoot(model);
        int repairedWeights = AvatarAnimationBridge.normalizeSkinWeights(model);
        System.out.printf("AVATAR2_WEIGHT_REPAIR file=%s vertices=%d%n",
                file.getName(), repairedWeights);
        if (!"Root".equals(model.getRootJointName())) {
            throw new IllegalStateException(file.getName()
                    + " has unsafe animation root " + model.getRootJointName());
        }
        for (String name : REQUIRED) {
            if (model.getJoint(name) == null) {
                throw new IllegalStateException(file.getName() + " is missing joint " + name);
            }
        }
        if (model.getJoint("Blend") != null || model.getJoint("RightArm") != null) {
            throw new IllegalStateException(file.getName()
                    + " unexpectedly contains the legacy Wurm skeleton");
        }
        if (!model.hasNull("NullCamera0")) {
            throw new IllegalStateException(file.getName()
                    + " does not expose NullCamera0 as a Wurm null");
        }
        Joint cameraJoint = model.getJoint("NullCamera0");
        if (cameraJoint.getBoneInfluenceList() != null
                && !cameraJoint.getBoneInfluenceList().isEmpty()) {
            throw new IllegalStateException(file.getName()
                    + " camera socket must not deform geometry");
        }
        Vector camera = model.getOriginalNullAttachmentOffset("NullCamera0", new Vector());
        if (camera == null || camera.vector[1] < 1.35f || camera.vector[1] > 2.05f) {
            throw new IllegalStateException(file.getName()
                    + " has invalid camera attachment height "
                    + (camera == null ? "null" : Arrays.toString(camera.vector)));
        }
        FakeResourceUrl avatar = new FakeResourceUrl(file.toURI().toURL());
        ColladaPropertiesXml.load(model, avatar, false);
        for (String animation : Arrays.asList(
                "idle", "walk", "run", "idle.p1", "walk.p1", "run.p1",
                "gait.walk.relaxed", "gait.jog")) {
            if (model.getColladaAnimation(animation) == null) {
                throw new IllegalStateException(file.getName()
                        + " is missing bind-safe animation " + animation);
            }
        }
        for (String rejected : Arrays.asList(
                "gait.walk.formal", "gait.sprint", "gait.crouch")) {
            if (model.getColladaAnimation(rejected) != null) {
                throw new IllegalStateException(file.getName()
                        + " exposes rejected animation " + rejected);
            }
        }
        requireSameLength(file, model, "walk", "gait.walk.relaxed");
        requireSameLength(file, model, "run", "gait.jog");
        requireSameLength(file, model, "run", "run.p1");
        verifyNeutralReferencePose(file, model);
        verifyPoseEnvelopes(file, model);
        System.out.printf(java.util.Locale.ROOT,
                "AVATAR2_LOCOMOTION_OK file=%s idle=%.3fs "
                        + "walk-relaxed=%.3fs run-relaxed=%.3fs jog=%.3fs%n",
                file.getName(), model.getColladaAnimation("idle").getLength(),
                model.getColladaAnimation("walk").getLength(),
                model.getColladaAnimation("run").getLength(),
                model.getColladaAnimation("gait.jog").getLength());
        int meshes = 0;
        int vertices = 0;
        int triangles = 0;
        if (model.getGeometryArray() != null) {
            for (ColladaGeometry geometry : model.getGeometryArray()) {
                if (geometry == null || geometry.getTriangleMeshArray() == null) continue;
                for (TriangleMesh mesh : geometry.getTriangleMeshArray()) {
                    verifyMeshBuffers(file, mesh);
                    if (!mesh.hasMaterial()) {
                        throw new IllegalStateException(file.getName()
                                + " mesh " + mesh.getName() + " has no material");
                    }
                    ColladaMaterial material = mesh.getColladaMaterial();
                    if (material == null || material.getTextureName() == null
                            || material.getTextureName().isEmpty()) {
                        throw new IllegalStateException(file.getName()
                                + " mesh " + mesh.getName() + " has no diffuse texture");
                    }
                    File texture = new File(file.getParentFile(), material.getTextureName());
                    if (!texture.isFile()) {
                        throw new IllegalStateException(file.getName()
                                + " references missing texture " + texture);
                    }
                    meshes++;
                    vertices += mesh.getVertexCount();
                    int indices = mesh.getIndexCount();
                    triangles += indices > 0 ? indices / 3 : mesh.getVertexCount() / 3;
                }
            }
        }
        ModelDataBounds bounds = model.calculateBoundingBox();
        System.out.printf(java.util.Locale.ROOT,
                "AVATAR2_DAE_OK file=%s joints=%d meshes=%d vertices=%d triangles=%d camera=[%.5f,%.5f,%.5f] bounds=[%.5f,%.5f,%.5f]-[%.5f,%.5f,%.5f]%n",
                file.getName(), model.getJoints().size(), meshes, vertices, triangles,
                camera.vector[0], camera.vector[1], camera.vector[2],
                bounds.getX0(), bounds.getH0(), bounds.getY0(),
                bounds.getX1(), bounds.getH1(), bounds.getY1());
    }

    private static void verifyPoseEnvelopes(File file, ColladaModel model) {
        verifyMotionPose(file, model, "idle",
                new float[] {0.0f, 0.25f, 0.5f, 0.75f}, 0.35f, 0.45f);
        verifyMotionPose(file, model, "walk",
                new float[] {0.0f, 0.25f, 0.5f, 0.75f}, 0.35f, 0.65f);
        verifyMotionPose(file, model, "run",
                new float[] {0.0f, 0.25f, 0.5f, 0.75f}, 0.35f, 0.80f);
        verifyMotionPose(file, model, "gait.jog",
                new float[] {0.0f, 0.25f, 0.5f, 0.75f}, 0.35f, 0.80f);
    }

    private static void verifyMotionPose(File file, ColladaModel model,
                                         String animationName,
                                         float[] fractions,
                                         float minimumRise,
                                         float maximumLeanRatio) {
        ColladaAnimation animation = model.getColladaAnimation(animationName);
        ForwardKinematics kinematics = new ForwardKinematics();
        for (float fraction : fractions) {
            kinematics.calculateForwardKinematics(fraction, animation,
                    false, false, false);
            kinematics.updateBoneTransformations(model, model.getJoint("Root"),
                    null, false);
            Vector3f pelvis = position(model, "pelvis");
            Vector3f head = position(model, "head");
            float rise = head.y - pelvis.y;
            float forward = head.z - pelvis.z;
            float sideways = head.x - pelvis.x;
            requireRange(file, animationName + " head above pelvis", rise,
                    minimumRise, 0.80f);
            requireRange(file, animationName + " sideways torso offset",
                    Math.abs(sideways), 0.0f, 0.22f);
            float leanRatio = Math.abs(forward) / Math.max(rise, 0.001f);
            requireRange(file, animationName + " torso lean ratio", leanRatio,
                    0.0f, maximumLeanRatio);
            if ("walk".equals(animationName) || "run".equals(animationName)) {
                printLimbEnvelope(file, model, animationName, fraction, pelvis);
            }
            System.out.printf(java.util.Locale.ROOT,
                    "AVATAR2_POSE_OK file=%s animation=%s fraction=%.2f "
                            + "rise=%.4f forward=%.4f lean=%.3f%n",
                    file.getName(), animationName, fraction, rise, forward,
                    leanRatio);
        }
        if (!"idle".equals(animationName)) return;
        Vector3f pelvis = position(model, "pelvis");
        Vector3f rightHand = position(model, "hand_r");
        Vector3f leftHand = position(model, "hand_l");
        Vector3f rightFoot = position(model, "foot_r");
        Vector3f leftFoot = position(model, "foot_l");
        requireRange(file, "pelvis height", pelvis.y, 0.70f, 1.10f);
        requireRange(file, "right foot height", rightFoot.y, 0.0f, 0.25f);
        requireRange(file, "left foot height", leftFoot.y, 0.0f, 0.25f);
        if (!(rightHand.x < -0.10f && leftHand.x > 0.10f)) {
            throw new IllegalStateException(file.getName()
                    + " has crossed or collapsed idle hands: right="
                    + rightHand.x + ", left=" + leftHand.x);
        }
        if (!(rightHand.y > rightFoot.y + 0.55f
                && leftHand.y > leftFoot.y + 0.55f)) {
            throw new IllegalStateException(file.getName()
                    + " has implausible arm deformation in idle");
        }
        float idleHandSpread = Math.max(Math.abs(rightHand.x - pelvis.x),
                Math.abs(leftHand.x - pelvis.x));
        requireRange(file, "idle hand lateral spread", idleHandSpread,
                0.10f, 0.42f);
    }

    private static void printLimbEnvelope(File file, ColladaModel model,
                                          String animationName,
                                          float fraction,
                                          Vector3f pelvis) {
        Vector3f rightHand = position(model, "hand_r");
        Vector3f leftHand = position(model, "hand_l");
        Vector3f rightElbow = position(model, "lowerarm_r");
        Vector3f leftElbow = position(model, "lowerarm_l");
        Vector3f rightShoulder = position(model, "upperarm_r");
        Vector3f leftShoulder = position(model, "upperarm_l");
        Vector3f rightKnee = position(model, "calf_r");
        Vector3f leftKnee = position(model, "calf_l");
        float handSpread = Math.max(Math.abs(rightHand.x - pelvis.x),
                Math.abs(leftHand.x - pelvis.x));
        float elbowSpread = Math.max(Math.abs(rightElbow.x - pelvis.x),
                Math.abs(leftElbow.x - pelvis.x));
        float kneeSpread = Math.max(Math.abs(rightKnee.x - pelvis.x),
                Math.abs(leftKnee.x - pelvis.x));
        float maximumHandSpread = "run".equals(animationName)
                ? 0.48f : 0.42f;
        requireRange(file, animationName + " hand lateral spread",
                handSpread, 0.10f, maximumHandSpread);
        requireRange(file, animationName + " elbow lateral spread",
                elbowSpread, 0.10f, 0.42f);
        requireRange(file, animationName + " knee lateral spread",
                kneeSpread, 0.04f, 0.16f);
        if (!(rightKnee.x < pelvis.x - 0.04f
                && leftKnee.x > pelvis.x + 0.04f)) {
            throw new IllegalStateException(file.getName() + " has crossed "
                    + animationName + " knees at fraction " + fraction
                    + ": right=" + rightKnee.x + ", left=" + leftKnee.x);
        }
        float handClearance = Math.min(rightShoulder.y - rightHand.y,
                leftShoulder.y - leftHand.y);
        requireRange(file, animationName + " hands below shoulders",
                handClearance, 0.10f, 0.80f);
        float shoulderHeightDelta = Math.abs(
                leftShoulder.y - rightShoulder.y);
        float shoulderForwardDelta = Math.abs(
                leftShoulder.z - rightShoulder.z);
        requireRange(file, animationName + " shoulder height delta",
                shoulderHeightDelta, 0.0f, 0.035f);
        float maximumShoulderForwardDelta = "run".equals(animationName)
                ? 0.28f : 0.20f;
        requireRange(file, animationName + " shoulder forward delta",
                shoulderForwardDelta, 0.025f,
                maximumShoulderForwardDelta);
        System.out.printf(java.util.Locale.ROOT,
                "AVATAR2_LIMB_POSE file=%s animation=%s fraction=%.2f "
                        + "hands=[%.4f,%.4f] handSpread=%.4f "
                        + "elbowSpread=%.4f handClearance=%.4f "
                        + "shoulderHeightDelta=%.4f "
                        + "shoulderForwardDelta=%.4f "
                        + "knees=[%.4f,%.4f] kneeSpread=%.4f%n",
                file.getName(), animationName, fraction,
                rightHand.x, leftHand.x, handSpread,
                elbowSpread, handClearance,
                shoulderHeightDelta, shoulderForwardDelta,
                rightKnee.x, leftKnee.x, kneeSpread);
    }

    private static void verifyNeutralReferencePose(File file,
                                                    ColladaModel model) {
        ColladaAnimation idle = model.getColladaAnimation("idle");
        ForwardKinematics kinematics = new ForwardKinematics();
        kinematics.calculateForwardKinematics(0.0f, idle,
                false, false, false);
        float maximumDifference = 0.0f;
        String maximumBone = "";
        for (String name : NEUTRAL_REFERENCE_JOINTS) {
            Joint joint = model.getJoint(name);
            Matrix4f nativeLocal = nativeLocalBind(joint);
            float difference = matrixDifference(joint.getAnimationMatrix(),
                    nativeLocal);
            if (difference > maximumDifference) {
                maximumDifference = difference;
                maximumBone = name;
            }
        }
        // Athletic is the canonical animation rig. Bodybuilder presets are
        // generated from the same native hierarchy but differ by at most a
        // few thousandths in their local bind matrices as body proportions
        // are applied. A 0.01 matrix-element envelope keeps that harmless
        // sub-degree drift while rejecting the old absolute-pose retarget,
        // whose shoulder/pelvis corrections differed by tenths.
        if (maximumDifference > 0.01f) {
            throw new IllegalStateException(file.getName()
                    + " idle reference pose differs from the native bind at "
                    + maximumBone + " by " + maximumDifference
                    + "; animation="
                    + model.getJoint(maximumBone).getAnimationMatrix()
                    + "; native="
                    + nativeLocalBind(model.getJoint(maximumBone)));
        }
        System.out.printf(java.util.Locale.ROOT,
                "AVATAR2_NEUTRAL_REFERENCE_OK file=%s maxDifference=%.7f "
                        + "bone=%s%n",
                file.getName(), maximumDifference, maximumBone);
    }

    private static Matrix4f nativeLocalBind(Joint joint) {
        Matrix4f global = new Matrix4f(joint.getOriginalBindMatrix());
        Joint parent = joint.getParent();
        if (parent == null) return global;
        Matrix4f inverseParent = new Matrix4f(
                parent.getOriginalBindMatrix()).invertLocal();
        return inverseParent.mult(global);
    }

    private static float matrixDifference(Matrix4f left, Matrix4f right) {
        float[] leftValues = {
                left.m00, left.m01, left.m02, left.m03,
                left.m10, left.m11, left.m12, left.m13,
                left.m20, left.m21, left.m22, left.m23,
                left.m30, left.m31, left.m32, left.m33
        };
        float[] rightValues = {
                right.m00, right.m01, right.m02, right.m03,
                right.m10, right.m11, right.m12, right.m13,
                right.m20, right.m21, right.m22, right.m23,
                right.m30, right.m31, right.m32, right.m33
        };
        float maximum = 0.0f;
        for (int index = 0; index < leftValues.length; index++) {
            maximum = Math.max(maximum,
                    Math.abs(leftValues[index] - rightValues[index]));
        }
        return maximum;
    }

    private static Vector3f position(ColladaModel model, String jointName) {
        return model.getJoint(jointName).getForwardKinematicsMatrixTotal()
                .toTranslationVector();
    }

    private static void verifyMeshBuffers(File file, TriangleMesh mesh) {
        int vertices = mesh.getVertexCount();
        float[] positions = mesh.getOriginalVertexBuffer();
        float[] normals = mesh.getOriginalNormalBuffer();
        if (positions == null || positions.length != vertices * 3) {
            throw new IllegalStateException(file.getName() + " mesh "
                    + mesh.getName() + " has an incomplete position buffer");
        }
        if (normals == null || normals.length != vertices * 3) {
            throw new IllegalStateException(file.getName() + " mesh "
                    + mesh.getName() + " has an incomplete normal buffer");
        }
        for (int vertex = 0; vertex < vertices; vertex++) {
            int base = vertex * 3;
            float normalLengthSquared = normals[base] * normals[base]
                    + normals[base + 1] * normals[base + 1]
                    + normals[base + 2] * normals[base + 2];
            if (!Float.isFinite(normalLengthSquared)
                    || normalLengthSquared < 0.25f) {
                throw new IllegalStateException(file.getName() + " mesh "
                        + mesh.getName() + " has invalid normal at vertex "
                        + vertex);
            }
        }
        FloatBuffer weights = mesh.getWeightBuffer();
        ShortBuffer bones = mesh.getBoneBuffer();
        Joint[] meshBones = mesh.getBones();
        if (weights == null || bones == null || meshBones == null
                || weights.capacity() < vertices * 4
                || bones.capacity() < vertices * 4) {
            throw new IllegalStateException(file.getName() + " mesh "
                    + mesh.getName() + " has incomplete skinning buffers");
        }
        float minimumWeightTotal = Float.POSITIVE_INFINITY;
        float maximumWeightTotal = Float.NEGATIVE_INFINITY;
        int imperfectWeightVertices = 0;
        for (int vertex = 0; vertex < vertices; vertex++) {
            float total = 0.0f;
            for (int influence = 0; influence < 4; influence++) {
                int offset = vertex * 4 + influence;
                float weight = weights.get(offset);
                int bone = bones.get(offset);
                if (!Float.isFinite(weight) || weight < 0.0f
                        || bone < 0 || bone >= meshBones.length) {
                    throw new IllegalStateException(file.getName() + " mesh "
                            + mesh.getName() + " has invalid skinning at vertex "
                            + vertex);
                }
                total += weight;
            }
            minimumWeightTotal = Math.min(minimumWeightTotal, total);
            maximumWeightTotal = Math.max(maximumWeightTotal, total);
            if (Math.abs(total - 1.0f) > 0.001f) imperfectWeightVertices++;
            if (total < 0.999f || total > 1.001f) {
                throw new IllegalStateException(file.getName() + " mesh "
                        + mesh.getName() + " has unnormalized weights at vertex "
                        + vertex + ": " + total);
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "AVATAR2_SKIN_WEIGHTS file=%s mesh=%s min=%.6f max=%.6f "
                        + "imperfect=%d/%d%n",
                file.getName(), mesh.getName(), minimumWeightTotal,
                maximumWeightTotal, imperfectWeightVertices, vertices);
    }

    private static void requireRange(File file, String label, float value,
                                     float minimum, float maximum) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < minimum || value > maximum) {
            throw new IllegalStateException(file.getName() + " has invalid "
                    + label + " " + value + " outside [" + minimum
                    + ", " + maximum + "]");
        }
    }

    private static void requireSameLength(File file, ColladaModel model,
                                          String first, String second) {
        float firstLength = model.getColladaAnimation(first).getLength();
        float secondLength = model.getColladaAnimation(second).getLength();
        if (Math.abs(firstLength - secondLength) > 0.000001f) {
            throw new IllegalStateException(file.getName() + " maps " + first
                    + " and " + second + " to different clips: "
                    + firstLength + " vs " + secondLength);
        }
    }
}
