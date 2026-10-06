package org.avatar2.tools;

import com.wurmonline.client.renderer.model.ModelDataBounds;
import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.renderer.model.wom.WOMLoader;
import com.wurmonline.client.resources.FakeResourceUrl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Extracts only measurements/rig metadata from the locally installed proprietary WOM. */
public final class WurmRigProbe {
    private WurmRigProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: WurmRigProbe graphics.jar output.json player/model.wom");
        }
        File graphics = new File(args[0]).getAbsoluteFile();
        File output = new File(args[1]).getAbsoluteFile();
        String entry = args[2];
        if (!graphics.isFile()) throw new IllegalArgumentException("Missing " + graphics);
        File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();

        URL file = graphics.toURI().toURL();
        FakeResourceUrl resource = new FakeResourceUrl(
                new URL("jar:" + file.toExternalForm() + "!/" + entry));
        ColladaModel model = WOMLoader.loadeNewModel(resource);
        ColladaPropertiesXml.load(model, resource, true);
        List<Joint> joints = new ArrayList<Joint>(model.getJoints().values());
        Collections.sort(joints, new Comparator<Joint>() {
            @Override public int compare(Joint left, Joint right) {
                int byIndex = Short.compare(left.getBoneIndex(), right.getBoneIndex());
                return byIndex != 0 ? byIndex : left.getName().compareTo(right.getName());
            }
        });
        ModelDataBounds bounds = model.calculateBoundingBox();

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8))) {
            writer.write("{\n  \"schema\": 1,\n");
            writer.write("  \"source\": \"" + json(entry) + "\",\n");
            writer.write("  \"root\": \"" + json(model.getRootJointName()) + "\",\n");
            writer.write(String.format(Locale.ROOT,
                    "  \"bounds\": {\"x0\": %.9f, \"h0\": %.9f, \"y0\": %.9f, \"x1\": %.9f, \"h1\": %.9f, \"y1\": %.9f},\n",
                    bounds.getX0(), bounds.getH0(), bounds.getY0(),
                    bounds.getX1(), bounds.getH1(), bounds.getY1()));
            writer.write("  \"joints\": [\n");
            for (int index = 0; index < joints.size(); index++) {
                Joint joint = joints.get(index);
                writer.write("    {\"name\": \"" + json(joint.getName()) + "\", ");
                writer.write("\"parent\": ");
                if (joint.getParent() == null) writer.write("null");
                else writer.write("\"" + json(joint.getParent().getName()) + "\"");
                writer.write(", \"index\": " + joint.getBoneIndex());
                writer.write(", \"bind\": ");
                matrix(writer, joint.getOriginalBindMatrix());
                writer.write(", \"inverseBind\": ");
                matrix(writer, joint.getOriginalInvertBindMatrix());
                writer.write("}" + (index + 1 == joints.size() ? "\n" : ",\n"));
            }
            writer.write("  ],\n  \"attachments\": {\n");
            List<String> attachments = new ArrayList<String>(
                    model.getModelsOffsetToNullList().keySet());
            Collections.sort(attachments);
            for (int index = 0; index < attachments.size(); index++) {
                String name = attachments.get(index);
                writer.write("    \"" + json(name) + "\": ");
                matrix(writer, model.getNullOriginalTransformation(name, new Matrix4f()));
                writer.write(index + 1 == attachments.size() ? "\n" : ",\n");
            }
            writer.write("  },\n  \"animations\": [\n");
            List<String> animations = new ArrayList<String>(
                    model.getColladaAnimationList().keySet());
            Collections.sort(animations);
            for (int index = 0; index < animations.size(); index++) {
                writer.write("    \"" + json(animations.get(index)) + "\"");
                writer.write(index + 1 == animations.size() ? "\n" : ",\n");
            }
            writer.write("  ],\n  \"meshes\": [\n");
            List<TriangleMesh> meshes = meshes(model);
            for (int index = 0; index < meshes.size(); index++) {
                TriangleMesh mesh = meshes.get(index);
                writer.write(String.format(Locale.ROOT,
                        "    {\"name\": \"%s\", \"vertices\": %d, \"triangles\": %d}%s\n",
                        json(mesh.getName()), mesh.getVertexCount(),
                        mesh.getTriangleCount(), index + 1 == meshes.size() ? "" : ","));
            }
            writer.write("  ]\n}\n");
        }
        System.out.println("AVATAR2_RIG_PROBED source=" + entry
                + " joints=" + joints.size() + " meshes=" + meshes(model).size()
                + " output=" + output);
    }

    private static List<TriangleMesh> meshes(ColladaModel model) {
        List<TriangleMesh> result = new ArrayList<TriangleMesh>();
        if (model.getGeometryArray() == null) return result;
        for (ColladaGeometry geometry : model.getGeometryArray()) {
            if (geometry == null || geometry.getTriangleMeshArray() == null) continue;
            Collections.addAll(result, geometry.getTriangleMeshArray());
        }
        return result;
    }

    private static void matrix(Writer writer, Matrix4f matrix) throws Exception {
        if (matrix == null) {
            writer.write("null");
            return;
        }
        float[] values = new float[16];
        matrix.get(values, false);
        writer.write("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) writer.write(", ");
            writer.write(String.format(Locale.ROOT, "%.9f", values[index]));
        }
        writer.write("]");
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
