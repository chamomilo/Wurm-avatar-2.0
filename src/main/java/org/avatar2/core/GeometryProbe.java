package org.avatar2.core;

/** Temporary stage-0 geometry command; authored shapes require a matching bank. */
public final class GeometryProbe {
    public static final java.util.Set<String> SHAPES = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList("none", "mpfb-athletic", "mpfb-heavy", "mpfb-older", "blend", "proportions")));
    public static final java.util.Set<String> TARGETS = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList("mpfb-athletic", "mpfb-heavy", "mpfb-older",
                    "build-low-muscle", "build-full", "build-low-muscle-full",
                    "build-breast-small-original", "build-breast-small-low-muscle", "build-breast-small-full", "build-breast-small-low-muscle-full",
                    "build-breast-large-original", "build-breast-large-low-muscle", "build-breast-large-full", "build-breast-large-low-muscle-full",
                    "build-shoulders-narrow", "build-shoulders-wide",
                    "build-age-old-middle-original", "build-age-old-middle-low-muscle", "build-age-old-middle-full", "build-age-old-middle-low-muscle-full",
                    "build-age-old-small-original", "build-age-old-small-low-muscle", "build-age-old-small-full", "build-age-old-small-low-muscle-full",
                    "build-age-old-large-original", "build-age-old-large-low-muscle", "build-age-old-large-full", "build-age-old-large-low-muscle-full")));
    public static final java.util.List<String> AGE_TARGETS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "build-age-old-middle-original", "build-age-old-middle-low-muscle", "build-age-old-middle-full", "build-age-old-middle-low-muscle-full",
            "build-age-old-small-original", "build-age-old-small-low-muscle", "build-age-old-small-full", "build-age-old-small-low-muscle-full",
            "build-age-old-large-original", "build-age-old-large-low-muscle", "build-age-old-large-full", "build-age-old-large-low-muscle-full"));
    public static final java.util.List<String> BREAST_TARGETS = java.util.Collections.unmodifiableList(java.util.Arrays.asList("build-breast-small-original", "build-breast-small-low-muscle",
            "build-breast-small-full", "build-breast-small-low-muscle-full", "build-breast-large-original",
            "build-breast-large-low-muscle", "build-breast-large-full", "build-breast-large-low-muscle-full"));
    public static final GeometryProbe NEUTRAL = new GeometryProbe(0, 0);
    public final float torsoWidth, torsoDepth;
    public final String shape;
    public final float shapeMix;
    public final float blendAthletic, blendHeavy, blendOlder;
    public final float buildMuscle, buildFat;
    public final float buildBreast;
    public final float buildShoulders;
    public final float headSize;
    public final String agePreset;

    public GeometryProbe(float torsoWidth, float torsoDepth) {
        this(torsoWidth, torsoDepth, "none", 0);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix) {
        this(torsoWidth, torsoDepth, shape, mix, 0, 0, 0);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older) {
        this(torsoWidth, torsoDepth, shape, mix, athletic, heavy, older, 1, 0);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older, float muscle, float fat) {
        this(torsoWidth, torsoDepth, shape, mix, athletic, heavy, older, muscle, fat, .5f);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older, float muscle, float fat, float breast) {
        this(torsoWidth, torsoDepth, shape, mix, athletic, heavy, older, muscle, fat, breast, .5f);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older, float muscle, float fat, float breast, float shoulders) {
        this(torsoWidth, torsoDepth, shape, mix, athletic, heavy, older, muscle, fat, breast, shoulders, .5f);
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older, float muscle, float fat, float breast, float shoulders, float headSize) {
        this(torsoWidth, torsoDepth, shape, mix, athletic, heavy, older, muscle, fat, breast, shoulders, headSize, "young");
    }

    public GeometryProbe(float torsoWidth, float torsoDepth, String shape, float mix,
                         float athletic, float heavy, float older, float muscle, float fat, float breast, float shoulders, float headSize, String agePreset) {
        this.torsoWidth = checked(torsoWidth);
        this.torsoDepth = checked(torsoDepth);
        if (!SHAPES.contains(shape) || !Float.isFinite(mix) || mix < 0 || mix > 1 || shape.equals("none") && mix != 0)
            throw new IllegalArgumentException("Invalid authored shape/mix");
        // Retain the selected endpoint at 0% so a live amount slider can rise again.
        this.shape = shape;
        this.shapeMix = mix == 0 ? 0 : mix;
        blendAthletic = contribution(athletic);
        blendHeavy = contribution(heavy);
        blendOlder = contribution(older);
        buildMuscle = contribution(muscle); buildFat = contribution(fat);
        buildBreast = contribution(breast);
        buildShoulders = contribution(shoulders);
        this.headSize = contribution(headSize);
        this.agePreset = BodyAge.validate(agePreset);
        if (!"blend".equals(shape) && (athletic != 0 || heavy != 0 || older != 0))
            throw new IllegalArgumentException("Shape contributions require blend mode");
        if (!"proportions".equals(shape) && (muscle != 1 || fat != 0 || breast != .5f || shoulders != .5f || !"young".equals(agePreset)))
            throw new IllegalArgumentException("Build controls require proportions mode");
    }

    private static float contribution(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Shape contributions must be finite and in 0..1");
        return value == 0 ? 0 : value;
    }

    /** Bounded convex blend: excess contributions share the available amount. */
    public float[] weights() {
        if ("blend".equals(shape)) {
            float scale = shapeMix / Math.max(1, blendAthletic + blendHeavy + blendOlder);
            return new float[]{blendAthletic * scale, blendHeavy * scale, blendOlder * scale};
        }
        return new float[]{"mpfb-athletic".equals(shape) ? shapeMix : 0,
                "mpfb-heavy".equals(shape) ? shapeMix : 0,
                "mpfb-older".equals(shape) ? shapeMix : 0};
    }

    /** Expose effective shares at full amount, preserving the visible silhouette. */
    public GeometryProbe asBlend() {
        // The independent grid cannot be represented by the historical silhouettes.
        // Switching to Blend explicitly starts an Original mixture in that case.
        float[] weights = weights();
        return new GeometryProbe(torsoWidth, torsoDepth, "blend", 1, weights[0], weights[1], weights[2], 1, 0, .5f, .5f, headSize);
    }

    /** Bilinear control grid: Original plus three separately authored corners. */
    public float[] buildWeights() {
        if (!"proportions".equals(shape)) return new float[]{0, 0, 0};
        float low = 1 - buildMuscle;
        return new float[]{shapeMix * low * (1 - buildFat), shapeMix * buildMuscle * buildFat,
                shapeMix * low * buildFat};
    }

    /** Three breast layers: released middle grid plus four corners on either side. */
    public float[] buildGridWeights() {
        float[] middle = buildWeights(), weights = new float[13];
        if (!"proportions".equals(shape)) return weights;
        float side = Math.abs(2 * buildBreast - 1), center = 1 - side;
        for (int i = 0; i < 3; i++) weights[i] = middle[i] * center;
        int offset = buildBreast < .5f ? 3 : 7;
        side *= shapeMix;
        weights[offset] = side * buildMuscle * (1 - buildFat);
        weights[offset + 1] = side * (1 - buildMuscle) * (1 - buildFat);
        weights[offset + 2] = side * buildMuscle * buildFat;
        weights[offset + 3] = side * (1 - buildMuscle) * buildFat;
        weights[buildShoulders < .5f ? 11 : 12] = Math.abs(2 * buildShoulders - 1) * shapeMix;
        return weights;
    }

    /** Old minus Young at each authored body/breast corner; Middle is halfway. */
    public float[] ageWeights() {
        float[] weights = new float[12];
        if (!"proportions".equals(shape)) return weights;
        float amount = BodyAge.weight(agePreset) * shapeMix;
        float side = Math.abs(2 * buildBreast - 1);
        float[] corners = {buildMuscle*(1-buildFat), (1-buildMuscle)*(1-buildFat), buildMuscle*buildFat, (1-buildMuscle)*buildFat};
        for (int i=0;i<4;i++) {
            weights[i] = amount*(1-side)*corners[i];
            weights[(buildBreast < .5f ? 4 : 8)+i] = amount*side*corners[i];
        }
        return weights;
    }

    /** A slider owns its selected percentage; other shares fit the remaining space. */
    public GeometryProbe withContribution(int component, float value) {
        if (!"blend".equals(shape) || component < 0 || component > 2)
            throw new IllegalArgumentException("Select Blend and a valid contribution");
        value = contribution(value);
        float[] shares = {blendAthletic, blendHeavy, blendOlder};
        float total = Math.max(1, shares[0] + shares[1] + shares[2]);
        for (int i = 0; i < 3; i++) shares[i] /= total;
        shares[component] = value;
        float others = 0;
        for (int i = 0; i < 3; i++) if (i != component) others += shares[i];
        if (others > 1 - value) {
            float scale = (1 - value) / others;
            for (int i = 0; i < 3; i++) if (i != component) shares[i] *= scale;
        }
        return new GeometryProbe(torsoWidth, torsoDepth, shape, shapeMix, shares[0], shares[1], shares[2], 1, 0, .5f, .5f, headSize);
    }

    /** Independent of body shape and its amount. */
    public GeometryProbe withHeadSize(float value) {
        return new GeometryProbe(torsoWidth, torsoDepth, shape, shapeMix, blendAthletic, blendHeavy, blendOlder,
                buildMuscle, buildFat, buildBreast, buildShoulders, value, agePreset);
    }

    public GeometryProbe withAgePreset(String value) {
        return new GeometryProbe(torsoWidth, torsoDepth, shape, shapeMix, blendAthletic, blendHeavy, blendOlder,
                buildMuscle, buildFat, buildBreast, buildShoulders, headSize, value);
    }

    private static float checked(float value) {
        if (!Float.isFinite(value) || value < -1 || value > 1)
            throw new IllegalArgumentException("Geometry probe values must be finite and in -1..1");
        return value == 0 ? 0 : value;
    }

    public boolean hasAuthoredShape() {
        if ("proportions".equals(shape)) return shapeMix != 0 && (buildMuscle != 1 || buildFat != 0 || buildBreast != .5f || buildShoulders != .5f || !"young".equals(agePreset));
        return shapeMix != 0 && (!"blend".equals(shape) || blendAthletic + blendHeavy + blendOlder != 0);
    }
    public boolean isNeutral() { return torsoWidth == 0 && torsoDepth == 0 && headSize == .5f && !hasAuthoredShape(); }

    /** Always starts from immutable imported data, including when returning to neutral. */
    public void deform(float[] baseVertices, float[] baseNormals,
                       float[] vertices, float[] normals, float floor, float height) {
        if (baseVertices.length % 3 != 0 || baseVertices.length != baseNormals.length
                || vertices.length != baseVertices.length || normals.length != baseNormals.length
                || baseVertices == vertices || baseNormals == normals
                || !Float.isFinite(height) || height <= 0)
            throw new IllegalArgumentException("Invalid bind geometry");
        System.arraycopy(baseVertices, 0, vertices, 0, vertices.length);
        System.arraycopy(baseNormals, 0, normals, 0, normals.length);
        if (torsoWidth == 0 && torsoDepth == 0) return;
        double width = torsoWidth * .10, depth = torsoDepth * .10;
        double radius = height * .16, lower = floor + height * .48, range = height * .34;
        for (int i = 0; i < vertices.length; i += 3) {
            double x = baseVertices[i], y = baseVertices[i + 1], z = baseVertices[i + 2];
            double t = (y - lower) / range;
            if (t <= 0 || t >= 1) continue;
            double q = x / radius, radial = Math.exp(-q*q*q*q);
            double sine = Math.sin(Math.PI*t), mask = radial*sine*sine;
            double dx = -4*q*q*q/radius*mask;
            double dy = radial*Math.PI*Math.sin(2*Math.PI*t)/range;
            vertices[i] = (float)(x*(1 + width*mask));
            vertices[i + 2] = (float)(z*(1 + depth*mask));
            // Inverse-transpose of the analytic deformation Jacobian. UV seams
            // keep their original shading; there is no triangle/vertex reorder.
            double j00 = 1 + width*(mask + x*dx), j01 = width*x*dy;
            double j20 = depth*z*dx, j21 = depth*z*dy, j22 = 1 + depth*mask;
            double nz = baseNormals[i + 2]/j22;
            double nx = (baseNormals[i] - j20*nz)/j00;
            double ny = baseNormals[i + 1] - j01*nx - j21*nz;
            double length = Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (length > 1e-12) {
                normals[i] = (float)(nx/length);
                normals[i + 1] = (float)(ny/length);
                normals[i + 2] = (float)(nz/length);
            }
        }
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof GeometryProbe)) return false;
        GeometryProbe probe = (GeometryProbe)other;
        return torsoWidth == probe.torsoWidth && torsoDepth == probe.torsoDepth
                && shape.equals(probe.shape) && shapeMix == probe.shapeMix
                && blendAthletic == probe.blendAthletic && blendHeavy == probe.blendHeavy && blendOlder == probe.blendOlder
                && buildMuscle == probe.buildMuscle && buildFat == probe.buildFat && buildBreast == probe.buildBreast && buildShoulders == probe.buildShoulders && headSize == probe.headSize && agePreset.equals(probe.agePreset);
    }
    @Override public int hashCode() {
        int hash = 31*(31*(31*Float.floatToIntBits(torsoWidth) + Float.floatToIntBits(torsoDepth)) + shape.hashCode()) + Float.floatToIntBits(shapeMix);
        hash = 31*(31*(31*hash + Float.floatToIntBits(blendAthletic)) + Float.floatToIntBits(blendHeavy)) + Float.floatToIntBits(blendOlder);
        return 31*(31*(31*(31*(31*(31*hash + Float.floatToIntBits(buildMuscle)) + Float.floatToIntBits(buildFat)) + Float.floatToIntBits(buildBreast)) + Float.floatToIntBits(buildShoulders)) + Float.floatToIntBits(headSize)) + agePreset.hashCode();
    }
}
