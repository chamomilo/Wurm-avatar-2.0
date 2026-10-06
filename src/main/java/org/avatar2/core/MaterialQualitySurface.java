package org.avatar2.core;

/** Pure shader inputs derived from item QL; all outputs are normalized 0..1. */
public final class MaterialQualitySurface {
    public static final float IMPROVEMENT_QUALITY = 50f;
    public static final float EMISSION_QUALITY = 90f;

    private MaterialQualitySurface() { }

    public static Sample evaluate(String material, float quality,
                                  float elementMultiplier) {
        float q = clamp(quality, 0f, 100f);
        float low = q >= IMPROVEMENT_QUALITY
                ? 0f : square((IMPROVEMENT_QUALITY - q) / IMPROVEMENT_QUALITY);
        float polish = q <= IMPROVEMENT_QUALITY
                ? 0f : smoothstep((q - IMPROVEMENT_QUALITY) / 50f);
        float emission = q <= EMISSION_QUALITY
                ? 0f : 0.08f * square((q - EMISSION_QUALITY) / 10f);
        return new Sample(
                q / 100f,
                0.30f * low,
                0.22f * low,
                0.18f * low,
                polish,
                MaterialReflection.shaderStrength(material, q, elementMultiplier),
                emission);
    }

    private static float smoothstep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float square(float value) {
        return value * value;
    }

    private static float clamp(float value, float minimum, float maximum) {
        float finite = Float.isNaN(value) || Float.isInfinite(value) ? minimum : value;
        return Math.max(minimum, Math.min(maximum, finite));
    }

    public static final class Sample {
        public final float quality;
        public final float normalWarp;
        public final float roughnessNoise;
        public final float tintVariation;
        public final float polish;
        public final float reflection;
        public final float emission;

        Sample(float quality, float normalWarp, float roughnessNoise,
               float tintVariation, float polish, float reflection,
               float emission) {
            this.quality = quality;
            this.normalWarp = normalWarp;
            this.roughnessNoise = roughnessNoise;
            this.tintVariation = tintVariation;
            this.polish = polish;
            this.reflection = reflection;
            this.emission = emission;
        }
    }
}

