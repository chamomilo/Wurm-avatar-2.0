package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector;
import java.util.*;

/** Rigid Wurm attachment frames, outside the deforming skeleton and morph binding. */
public final class AvatarEquipmentSockets {
    // Opaque attachment IDs. Never add these to Wurm's skinning joint arrays.
    private static final int FIRST_ID = 16384;
    private static final String[] NAMES = {"NullLeftHand", "NullRightHand", "NullShield",
            "LeftHandMiddle1", "RightHandMiddle1", "Spine", "SpineTop"};
    private static final Map<ColladaModel, Map<String, Socket>> MODELS = new WeakHashMap<>();
    private AvatarEquipmentSockets() { }

    public static void register(ColladaModel model) {
        synchronized (AvatarEquipmentSockets.class) { if (MODELS.containsKey(model)) return; }
        Map<String, Socket> sockets = new LinkedHashMap<>();
        for (String side : new String[]{"l", "r"}) {
            float sign = side.equals("l") ? 1 : -1;
            String hand = "hand_" + side;
            // Pinned vanilla Male_1_04.wom landmarks, metres, Wurm Y up.
            float[] wrist = {sign * .646039f, 1.342788f, -.038631f};
            float[] middle = {sign * .753960f, 1.338579f, -.017285f};
            float[] index = {sign * .747297f, 1.335141f, .003970f};
            float[] pinky = {sign * .747542f, 1.336244f, -.061100f};
            Frame source = new Frame(wrist, middle, subtract(index, pinky));
            Frame target = new Frame(point(model, hand), point(model, "middle_01_" + side),
                    subtract(point(model, "index_01_" + side), point(model, "pinky_01_" + side)));
            String prefix = side.equals("l") ? "Left" : "Right";
            float[] grip = side.equals("l") ? new float[]{.724879f, 1.316171f, -.028025f}
                    : new float[]{-.725501f, 1.315549f, -.028025f};
            add(model, sockets, "Null" + prefix + "Hand", hand, source.fit(target, grip));
            add(model, sockets, prefix + "HandMiddle1", "middle_01_" + side, source.fit(target, middle));
            if (side.equals("l")) {
                Frame forearm = new Frame(new float[]{.408950f, 1.346720f, -.038631f}, wrist,
                        subtract(index, pinky));
                Frame nativeForearm = new Frame(point(model, "lowerarm_l"), point(model, hand),
                        subtract(point(model, "index_01_l"), point(model, "pinky_01_l")));
                add(model, sockets, "NullShield", "lowerarm_l", forearm.fit(nativeForearm,
                        new float[]{.585357f, 1.379371f, -.038631f}));
            }
        }
        // Match vanilla torso landmarks between the pelvis and base of the neck.
        for (String name : new String[]{"Spine", "SpineTop"}) {
            float fraction = ((name.equals("Spine") ? 1.116967f : 1.234433f) - .963799f)
                    / (1.411342f - .963799f);
            float[] pelvis = point(model, "pelvis"), neck = point(model, "neck_01");
            Matrix4f rest = new Matrix4f();
            rest.m03 = pelvis[0] + (neck[0] - pelvis[0]) * fraction;
            rest.m13 = pelvis[1] + (neck[1] - pelvis[1]) * fraction;
            rest.m23 = pelvis[2] + (neck[2] - pelvis[2]) * fraction;
            add(model, sockets, name, name.equals("Spine") ? "spine_02" : "spine_03", rest);
        }
        synchronized (AvatarEquipmentSockets.class) {
            if (!MODELS.containsKey(model)) MODELS.put(model, Collections.unmodifiableMap(sockets));
        }
    }

    public static synchronized void copied(ColladaModel source, ColladaModel copy) {
        Map<String, Socket> sockets = MODELS.get(source);
        // Socket values contain no models/joints and are immutable after creation.
        if (sockets != null) MODELS.put(copy, sockets);
    }

    public static synchronized int id(ColladaModel model, String name) {
        Map<String, Socket> sockets = MODELS.get(model);
        if (sockets == null || name == null) return -1;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)
                && sockets.containsKey(NAMES[i])) return FIRST_ID + i;
        return -1;
    }

    private static synchronized Socket socket(ColladaModel model, Object key) {
        Map<String, Socket> sockets = MODELS.get(model);
        if (sockets == null) return null;
        int id = key instanceof Integer ? (Integer) key : id(model, String.valueOf(key));
        int index = id - FIRST_ID;
        return index >= 0 && index < NAMES.length ? sockets.get(NAMES[index]) : null;
    }

    /** Null means the caller must preserve the native attachment implementation. */
    public static Matrix4f transform(ColladaModel model, Object key,
                                                  Matrix4f destination, boolean original) {
        return transform(model, key, destination, original, true);
    }

    static Matrix4f unfittedShieldFrame(ColladaModel model) {
        return transform(model, "NullShield", null, false, false);
    }

    private static Matrix4f transform(ColladaModel model, Object key,
                                     Matrix4f destination, boolean original, boolean fitShield) {
        Socket socket = socket(model, key);
        if (socket == null) return null;
        Matrix4f result = destination == null ? new Matrix4f() : destination;
        float scale = AvatarHeightPrototype.scale(model);
        if (original) {
            // AttachedCellRenderable removes this reference orientation. Vanilla
            // reference axes are identity: retain our anatomical alignment once.
            result.setIdentity();
            result.m03 = socket.rest.m03 * scale;
            result.m13 = socket.floor + (socket.rest.m13 - socket.floor) * scale;
            result.m23 = socket.rest.m23 * scale;
        } else {
            Joint parent = model.getJoint(socket.parent);
            if (parent == null) return null;
            result.set(parent.getBindMatrix().mult(socket.local));
            // Scale the attachment position with the body, never the item axes.
            result.m00 /= scale; result.m01 /= scale; result.m02 /= scale;
            result.m10 /= scale; result.m11 /= scale; result.m12 /= scale;
            result.m20 /= scale; result.m21 /= scale; result.m22 /= scale;
            if (fitShield && socket.parent.equals("lowerarm_l")) AvatarShieldWalk.fitShield(model, result);
        }
        return result;
    }

    public static Vector offset(ColladaModel model, Object key, Vector result, boolean original) {
        Matrix4f matrix = transform(model, key, null, original);
        if (matrix == null) return null;
        result.vector[0] = matrix.m03; result.vector[1] = matrix.m13; result.vector[2] = matrix.m23;
        return result;
    }

    public static Vector rotation(ColladaModel model, String key) {
        Matrix4f matrix = transform(model, key, null, false);
        if (matrix == null) return null;
        com.wurmonline.client.renderer.model.collada.math.Quaternion q =
                new com.wurmonline.client.renderer.model.collada.math.Quaternion();
        matrix.toRotationQuat(q);
        float[] angles = q.toAngles(null);
        Vector result = new Vector();
        for (int i = 0; i < 3; i++) result.vector[i] = (float) Math.toDegrees(angles[i]);
        return result;
    }

    public static synchronized Vector scale(ColladaModel model, String key) {
        if (socket(model, key) == null) return null;
        Vector result = new Vector();
        Arrays.fill(result.vector, 1f);
        return result;
    }

    private static synchronized Map<String, Socket> sockets(ColladaModel model) { return MODELS.get(model); }

    static Matrix4f referenceShieldFrame(ColladaModel model) {
        register(model);
        return new Matrix4f(sockets(model).get("NullShield").rest);
    }

    public static List<Map<String, Object>> describe(ColladaModel model) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Socket> sockets = sockets(model);
        if (sockets == null) return result;
        for (Map.Entry<String, Socket> entry : sockets.entrySet()) {
            Matrix4f matrix = transform(model, entry.getKey(), null, false);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getKey()); row.put("parent", entry.getValue().parent);
            row.put("position", new float[]{matrix.m03, matrix.m13, matrix.m23});
            row.put("itemScale", 1); result.add(row);
        }
        return result;
    }

    private static void add(ColladaModel model, Map<String, Socket> sockets, String name,
                            String parent, Matrix4f rest) {
        Joint joint = model.getJoint(parent);
        if (joint == null) throw new IllegalArgumentException("Missing Avatar socket parent: " + parent);
        sockets.put(name, new Socket(parent, rest,
                joint.getOriginalBindMatrix().invert().mult(rest), model.calculateBoundingBox().getH0()));
    }

    private static float[] point(ColladaModel model, String name) {
        Joint joint = model.getJoint(name);
        if (joint == null) throw new IllegalArgumentException("Missing Avatar landmark: " + name);
        Matrix4f m = joint.getOriginalBindMatrix();
        return new float[]{m.m03, m.m13, m.m23};
    }

    private static float[] subtract(float[] a, float[] b) { return new float[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; }
    private static float dot(float[] a, float[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]};
    }
    private static float length(float[] a) { return (float)Math.sqrt(dot(a,a)); }
    private static float[] unit(float[] a) {
        float length = length(a);
        if (!Float.isFinite(length) || length < .000001f) throw new IllegalArgumentException("Degenerate socket landmarks");
        return new float[]{a[0]/length, a[1]/length, a[2]/length};
    }

    private static final class Socket {
        final String parent;
        final Matrix4f rest, local;
        final float floor;
        Socket(String parent, Matrix4f rest, Matrix4f local, float floor) {
            this.parent=parent; this.rest=rest; this.local=local; this.floor=floor;
        }
    }

    private static final class Frame {
        final float[] origin;
        final float[][] axes;
        final float along, across;
        Frame(float[] origin, float[] end, float[] width) {
            this.origin=origin;
            float[] direction=subtract(end,origin);
            along=length(direction);
            float[] x=unit(direction);
            float parallel=dot(x,width);
            float[] planar={width[0]-x[0]*parallel,width[1]-x[1]*parallel,width[2]-x[2]*parallel};
            across=length(planar);
            float[] y=unit(planar);
            axes=new float[][]{x,y,unit(cross(x,y))};
        }
        Matrix4f fit(Frame target, float[] point) {
            float[] offset=subtract(point,origin);
            float[] coordinates={dot(offset,axes[0])*target.along/along,
                    dot(offset,axes[1])*target.across/across, dot(offset,axes[2])*target.along/along};
            float[] position=target.origin.clone();
            float[][] rotation=new float[3][3];
            for (int r=0;r<3;r++) for (int k=0;k<3;k++) {
                position[r]+=target.axes[k][r]*coordinates[k];
                for (int c=0;c<3;c++) rotation[r][c]+=target.axes[k][r]*axes[k][c];
            }
            Matrix4f m=new Matrix4f();
            m.m00=rotation[0][0];m.m01=rotation[0][1];m.m02=rotation[0][2];m.m03=position[0];
            m.m10=rotation[1][0];m.m11=rotation[1][1];m.m12=rotation[1][2];m.m13=position[1];
            m.m20=rotation[2][0];m.m21=rotation[2][1];m.m22=rotation[2][2];m.m23=position[2];
            return m;
        }
    }
}
