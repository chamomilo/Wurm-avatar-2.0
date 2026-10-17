package org.avatar2.core;

import java.io.*;
import java.util.*;

/** Immutable stage-0 endpoints bound to Wurm's imported binding fingerprint. */
public final class MorphBank {
    public static final int MAGIC = 0x41564d31; // AVM1
    public final String body, sourceDaeSha256;
    private final Map<String, Mesh> meshes;

    private MorphBank(String body, String source, Map<String, Mesh> meshes) {
        this.body = body; this.sourceDaeSha256 = source;
        this.meshes = Collections.unmodifiableMap(meshes);
    }

    public Mesh matching(String fingerprint) { return meshes.get(fingerprint); }

    public static MorphBank read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream));
        if (in.readInt() != MAGIC) throw new IOException("Unsupported Avatar morph format");
        String body = in.readUTF(), source = hash(in.readUTF());
        int count = bounded(in.readInt(), 1, 2);
        Map<String, Mesh> meshes = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String fingerprint = hash(in.readUTF()), name = in.readUTF();
            int size = bounded(in.readInt(), 3, 150000);
            if (size % 3 != 0) throw new IOException("Invalid morph vertex layout");
            Map<String, float[][]> targets = new LinkedHashMap<>();
            int targetCount = bounded(in.readInt(), 1, GeometryProbe.TARGETS.size());
            for (int t = 0; t < targetCount; t++) {
                String target = in.readUTF();
                if (!GeometryProbe.TARGETS.contains(target))
                    throw new IOException("Unknown Avatar morph target: " + target);
                float[][] delta = {floats(in, size), floats(in, size)};
                if (targets.put(target, delta) != null) throw new IOException("Duplicate morph target");
            }
            if (meshes.put(fingerprint, new Mesh(name, size, targets)) != null)
                throw new IOException("Duplicate morph fingerprint");
        }
        if (in.read() != -1) throw new IOException("Trailing morph data");
        return new MorphBank(body, source, meshes);
    }

    private static String hash(String text) throws IOException {
        if (!text.matches("[0-9a-f]{64}")) throw new IOException("Invalid morph fingerprint");
        return text;
    }
    private static int bounded(int value, int low, int high) throws IOException {
        if (value < low || value > high) throw new IOException("Invalid morph data size");
        return value;
    }
    private static float[] floats(DataInputStream in, int count) throws IOException {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readFloat();
            if (!Float.isFinite(values[i]) || Math.abs(values[i]) > 2)
                throw new IOException("Invalid morph delta");
        }
        return values;
    }

    public static final class Mesh {
        public final String name;
        public final int components;
        private final Map<String, float[][]> targets;
        private Mesh(String name, int components, Map<String, float[][]> targets) {
            this.name = name; this.components = components;
            this.targets = Collections.unmodifiableMap(targets);
        }
        public Set<String> shapes() { return targets.keySet(); }
        public void deform(GeometryProbe probe, float[] base, float[] baseNormals,
                           float[] vertices, float[] normals) {
            if (!"blend".equals(probe.shape) && !"proportions".equals(probe.shape)) {
                deform(probe.shape, probe.shapeMix, base, baseNormals, vertices, normals);
                return;
            }
            if (base.length != components || baseNormals.length != components
                    || vertices.length != components || normals.length != components
                    || base == vertices || baseNormals == normals)
                throw new IllegalArgumentException("Morph does not match imported geometry");
            float[] weights = "proportions".equals(probe.shape) ? probe.buildGridWeights() : probe.weights();
            if ("proportions".equals(probe.shape)) {
                // Evaluate the released middle first, then local cup residuals.
                // Equivalent to the convex grid, with exact old float arithmetic
                // wherever the authored cup displacement is zero.
                float[] middle = probe.buildWeights();
                System.arraycopy(middle, 0, weights, 0, 3);
            }
            float[] ageWeights = probe.ageWeights();
            boolean empty = true; for (float weight : weights) if (weight != 0) empty = false;
            for (float weight : ageWeights) if (weight != 0) empty = false;
            if (empty) {
                System.arraycopy(base, 0, vertices, 0, components);
                System.arraycopy(baseNormals, 0, normals, 0, components);
                return;
            }
            String[] names = "proportions".equals(probe.shape)
                    ? new String[]{"build-low-muscle", "build-full", "build-low-muscle-full",
                        "build-breast-small-original", "build-breast-small-low-muscle", "build-breast-small-full", "build-breast-small-low-muscle-full",
                        "build-breast-large-original", "build-breast-large-low-muscle", "build-breast-large-full", "build-breast-large-low-muscle-full",
                        "build-shoulders-narrow", "build-shoulders-wide"}
                    : new String[]{"mpfb-athletic", "mpfb-heavy", "mpfb-older"};
            float[][][] deltas = new float[names.length][][];
            for (int t = 0; t < names.length; t++) {
                deltas[t] = targets.get(names[t]);
                if (weights[t] != 0 && deltas[t] == null)
                    throw new IllegalArgumentException("Missing morph contribution: " + names[t]);
            }
            float[][][] aged = new float[12][][], young = new float[12][][];
            for (int t=0;t<12;t++) if(ageWeights[t]!=0) {
                aged[t]=targets.get(GeometryProbe.AGE_TARGETS.get(t));
                young[t]=t==0?null:deltas[t-1];
                if(aged[t]==null)throw new IllegalArgumentException("Missing age contribution: "+GeometryProbe.AGE_TARGETS.get(t));
            }
            for (int i = 0; i < components; i++) {
                float vertex = base[i], normal = baseNormals[i];
                for (int t = 0; t < names.length; t++) if (weights[t] != 0) {
                    float positionDelta=deltas[t][0][i],normalDelta=deltas[t][1][i];
                    if(t>=3 && t<11) {
                        int corner=(t-3)%4;
                        if(corner!=0) { positionDelta-=deltas[corner-1][0][i]; normalDelta-=deltas[corner-1][1][i]; }
                    }
                    vertex += positionDelta * weights[t];
                    normal += normalDelta * weights[t];
                }
                for(int t=0;t<12;t++) if(ageWeights[t]!=0) {
                    vertex += (aged[t][0][i] - (young[t]==null?0:young[t][0][i])) * ageWeights[t];
                    normal += (aged[t][1][i] - (young[t]==null?0:young[t][1][i])) * ageWeights[t];
                }
                vertices[i] = vertex; normals[i] = normal;
            }
            for (int i = 0; i < components; i += 3) {
                double x = normals[i], y = normals[i + 1], z = normals[i + 2];
                double length = Math.sqrt(x*x + y*y + z*z);
                if (length < 1e-8) throw new IllegalArgumentException("Degenerate morph normal");
                normals[i] /= length; normals[i + 1] /= length; normals[i + 2] /= length;
            }
        }
        /** Blend a single authored endpoint, then normalize its bind normals. */
        public void deform(String shape, float mix, float[] base, float[] baseNormals,
                           float[] vertices, float[] normals) {
            float[][] delta = targets.get(shape);
            if (delta == null || !Float.isFinite(mix) || mix < 0 || mix > 1
                    || base.length != components || baseNormals.length != components
                    || vertices.length != components || normals.length != components
                    || base == vertices || baseNormals == normals)
                throw new IllegalArgumentException("Morph does not match imported geometry");
            if (mix == 0) {
                System.arraycopy(base, 0, vertices, 0, components);
                System.arraycopy(baseNormals, 0, normals, 0, components);
                return;
            }
            for (int i = 0; i < components; i++) {
                vertices[i] = base[i] + delta[0][i] * mix;
                normals[i] = baseNormals[i] + delta[1][i] * mix;
            }
            for (int i = 0; i < components; i += 3) {
                double x = normals[i], y = normals[i + 1], z = normals[i + 2];
                double length = Math.sqrt(x*x + y*y + z*z);
                if (length < 1e-8) throw new IllegalArgumentException("Degenerate morph normal");
                normals[i] /= length; normals[i + 1] /= length; normals[i + 2] /= length;
            }
        }
    }
}
