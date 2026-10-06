package org.avatar2.core;

/** Numeric gate used by Blender and Java exporters before a runtime asset ships. */
public final class VanillaScaleContract {
    public static final float MAX_HEIGHT_RELATIVE_DEVIATION = 0.015f;
    public static final float MAX_SHOULDER_RELATIVE_DEVIATION = 0.025f;
    public static final float MAX_PELVIS_RELATIVE_DEVIATION = 0.020f;
    public static final float MAX_HAND_ANCHOR_DEVIATION = 0.025f;
    public static final float MAX_FOOT_ANCHOR_DEVIATION = 0.015f;

    private VanillaScaleContract() { }

    public static Result validate(Metrics reference, Metrics candidate) {
        if (reference == null || candidate == null) {
            return new Result(false, "missing metrics");
        }
        if (relative(reference.height, candidate.height) > MAX_HEIGHT_RELATIVE_DEVIATION)
            return new Result(false, "height");
        if (relative(reference.shoulderWidth, candidate.shoulderWidth) > MAX_SHOULDER_RELATIVE_DEVIATION)
            return new Result(false, "shoulders");
        if (relative(reference.pelvisHeight, candidate.pelvisHeight) > MAX_PELVIS_RELATIVE_DEVIATION)
            return new Result(false, "pelvis");
        if (Math.abs(reference.handAnchorHeight - candidate.handAnchorHeight) > MAX_HAND_ANCHOR_DEVIATION)
            return new Result(false, "hand anchor");
        if (Math.abs(reference.footAnchorHeight - candidate.footAnchorHeight) > MAX_FOOT_ANCHOR_DEVIATION)
            return new Result(false, "foot anchor");
        return new Result(true, "compatible");
    }

    private static float relative(float expected, float actual) {
        if (Math.abs(expected) < 0.000001f) return Math.abs(actual - expected);
        return Math.abs(actual - expected) / Math.abs(expected);
    }

    public static final class Metrics {
        public final float height;
        public final float shoulderWidth;
        public final float pelvisHeight;
        public final float handAnchorHeight;
        public final float footAnchorHeight;

        public Metrics(float height, float shoulderWidth, float pelvisHeight,
                       float handAnchorHeight, float footAnchorHeight) {
            this.height = height;
            this.shoulderWidth = shoulderWidth;
            this.pelvisHeight = pelvisHeight;
            this.handAnchorHeight = handAnchorHeight;
            this.footAnchorHeight = footAnchorHeight;
        }
    }

    public static final class Result {
        public final boolean compatible;
        public final String reason;
        Result(boolean compatible, String reason) {
            this.compatible = compatible;
            this.reason = reason;
        }
    }
}
