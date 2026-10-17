package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.importer.*;
import org.avatar2.core.MorphBank;
import org.avatar2.lab.LabJson;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Explicit offline conversion: original DAE vertex IDs -> optimized Wurm IDs. */
public final class MorphBankBaker {
    public static void run(String root) throws Exception {
        List<Object> provenance = new ArrayList<>();
        for (String body : new String[]{"male-bodybuilder", "female-bodybuilder"}) {
            File dae = new File(root, "assets/runtime/body/" + body + ".dae");
            ColladaModel model = GeometryRuntimeHarness.load(dae);
            // Mirror loadIfAvatar's post-import pass as well as the patched
            // importer. Float normalization affects the exact binding hash.
            AvatarAnimationBridge.normalizeSkinWeights(model);
            AvatarGeometryPrototype.register(model);
            Map<String, String> fingerprints = new HashMap<>();
            for (Map<String,Object> row : AvatarGeometryPrototype.describe(model))
                fingerprints.put((String)row.get("name"), (String)row.get("basisSha256"));
            File output = new File(root, "assets/runtime/morph/" + body + ".avm");
            output.getParentFile().mkdirs();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(
                    new File(root, "assets/working/morphs/" + body + ".avsrc"))));
                 DataOutputStream out = new DataOutputStream(bytes)) {
                if (in.readInt() != 0x41565331) throw new IOException("Unsupported authoring format");
                String sourceSha = in.readUTF(), sourceBody = in.readUTF();
                if (!body.equals(sourceBody) || !sourceSha.equals(sha(dae))) throw new IOException("Authoring DAE mismatch");
                int meshCount = count(in.readInt(), 1, 2);
                out.writeInt(MorphBank.MAGIC); out.writeUTF(body); out.writeUTF(sourceSha); out.writeInt(meshCount);
                for (int m = 0; m < meshCount; m++) {
                    String name = in.readUTF(); int length = count(in.readInt(), 3, 150000);
                    float[] base = floats(in, length), normals = floats(in, length);
                    int targets = count(in.readInt(), 1, org.avatar2.core.GeometryProbe.TARGETS.size());
                    Map<String,float[][]> shapes = new LinkedHashMap<>();
                    for (int t = 0; t < targets; t++) {
                        String target = in.readUTF();
                        if (shapes.put(target, new float[][]{floats(in, length), floats(in, length)}) != null)
                            throw new IOException("Duplicate authoring target");
                    }
                    TriangleMesh mesh = mesh(model, name);
                    float[] imported = mesh.getOriginalVertexBuffer(), importedNormals = mesh.getOriginalNormalBuffer();
                    Map<Key,Integer> sourceIds = new HashMap<>();
                    for (int i = 0; i < length; i += 3) {
                        Key key = new Key(base, normals, i);
                        Integer old = sourceIds.putIfAbsent(key, i);
                        if (old != null) for (float[][] shape : shapes.values()) for (float[] values : shape)
                            for (int axis = 0; axis < 3; axis++)
                                if (Math.abs(values[i + axis] - values[old + axis]) > 2e-6)
                                    throw new IOException("Ambiguous original vertex IDs: " + name);
                    }
                    int[] mapping = new int[imported.length / 3];
                    for (int i = 0; i < imported.length; i += 3) {
                        Integer original = sourceIds.get(new Key(imported, importedNormals, i));
                        if (original == null) throw new IOException("Unmapped imported vertex: " + name + " #" + i/3);
                        mapping[i / 3] = original;
                    }
                    out.writeUTF(fingerprints.get(mesh.getName())); out.writeUTF(mesh.getName());
                    out.writeInt(imported.length); out.writeInt(shapes.size());
                    for (Map.Entry<String,float[][]> target : shapes.entrySet()) {
                        out.writeUTF(target.getKey());
                        for (int channel = 0; channel < 2; channel++) {
                            float[] neutral = channel == 0 ? base : normals;
                            for (int id : mapping) for (int axis = 0; axis < 3; axis++)
                                out.writeFloat(target.getValue()[channel][id + axis] - neutral[id + axis]);
                        }
                    }
                    System.out.println("AVATAR2_MORPH_MAPPED " + body + " " + mesh.getName() + " source=" + length/3
                            + " imported=" + imported.length/3 + " targets=" + shapes.size() + " missing=0");
                }
                if (in.read() != -1) throw new IOException("Trailing authoring data");
            }
            byte[] result = bytes.toByteArray();
            MorphBank.read(new ByteArrayInputStream(result));
            Files.write(output.toPath(), result);
            Map<String,Object> metadata = LabJson.object(LabJson.parse(new String(Files.readAllBytes(
                    new File(root, "assets/working/morphs/" + body + ".json").toPath()), java.nio.charset.StandardCharsets.UTF_8)));
            metadata.put("bankSha256", sha(output)); provenance.add(metadata);
            System.out.println("AVATAR2_MORPH_BANK " + output + " bytes=" + result.length);
        }
        Files.write(new File(root, "assets/metadata/morph-prototype.json").toPath(),
                (LabJson.write(LabJson.map("schema",1,"stage",0,"authoring","Blender 4.5.13; MPFB 2.0.17",
                        "coordinates","Wurm Y-up; frozen approved bind rig","bodies",provenance)) + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static TriangleMesh mesh(ColladaModel model, String name) throws IOException {
        TriangleMesh result = null;
        for (ColladaGeometry g : model.getGeometryArray()) for (TriangleMesh m : g.getTriangleMeshArray())
            if (m.getName().startsWith(name + "-")) {
                if (result != null) throw new IOException("Ambiguous imported mesh: " + name);
                result = m;
            }
        if (result == null) throw new IOException("Missing imported mesh: " + name);
        return result;
    }
    private static int count(int value, int min, int max) throws IOException {
        if (value < min || value > max) throw new IOException("Invalid authoring size"); return value;
    }
    private static float[] floats(DataInputStream in, int count) throws IOException {
        if (count % 3 != 0) throw new IOException("Invalid authoring vertex layout");
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readFloat(); if (!Float.isFinite(values[i])) throw new IOException("Nonfinite authored geometry");
        }
        return values;
    }
    private static String sha(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder out = new StringBuilder();
        for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static final class Key {
        final int[] bits = new int[6];
        Key(float[] vertices, float[] normals, int i) {
            for (int axis = 0; axis < 3; axis++) {
                bits[axis] = Float.floatToIntBits(vertices[i + axis] == 0 ? 0 : vertices[i + axis]);
                bits[axis + 3] = Float.floatToIntBits(normals[i + axis] == 0 ? 0 : normals[i + axis]);
            }
        }
        @Override public boolean equals(Object other) { return other instanceof Key && Arrays.equals(bits, ((Key)other).bits); }
        @Override public int hashCode() { return Arrays.hashCode(bits); }
    }
}
