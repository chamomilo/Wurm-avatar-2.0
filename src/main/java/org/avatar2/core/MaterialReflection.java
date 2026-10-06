package org.avatar2.core;

import java.util.Locale;

/** Quality-aware reflection policy expressed in percentage points (0..30). */
public final class MaterialReflection {
    public static final float START_QUALITY = 50f;
    public static final float MAX_QUALITY = 95f;
    public static final float PRECIOUS_MAX = 30f;
    public static final float COMMON_MAX = 16f;

    private MaterialReflection() { }

    public static float maximumFor(String material) {
        String normalized = material == null ? "" : material.trim().toLowerCase(Locale.ROOT);
        return "gold".equals(normalized) || "silver".equals(normalized)
                ? PRECIOUS_MAX : COMMON_MAX;
    }

    /** Ease-in parabola: zero at QL 50, full strength at QL 95. */
    public static float qualityFactor(float quality) {
        float q = finiteClamp(quality, 0f, 100f);
        if (q <= START_QUALITY) return 0f;
        if (q >= MAX_QUALITY) return 1f;
        float t = (q - START_QUALITY) / (MAX_QUALITY - START_QUALITY);
        return t * t;
    }

    public static float percent(String material, float quality,
                                float elementMultiplier) {
        return maximumFor(material) * qualityFactor(quality)
                * finiteClamp(elementMultiplier, 0f, 1f);
    }

    public static float shaderStrength(String material, float quality,
                                       float elementMultiplier) {
        return percent(material, quality, elementMultiplier) / 100f;
    }

    private static float finiteClamp(float value, float minimum, float maximum) {
        float finite = Float.isNaN(value) || Float.isInfinite(value) ? minimum : value;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}

