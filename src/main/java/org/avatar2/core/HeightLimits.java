package org.avatar2.core;

/** Wurm vertical unit: ten dirt/slope units per metre. Standing body limits. */
public final class HeightLimits {
    public static final double MIN_METRES = 1.3, MAX_METRES = 2.0;
    public static final double DIRTS_PER_METRE = 10;
    public final float baselineMetres;

    public HeightLimits(float baselineMetres) {
        if (!Float.isFinite(baselineMetres) || baselineMetres <= 0)
            throw new IllegalArgumentException("Invalid standing body height");
        this.baselineMetres = baselineMetres;
    }

    public float[] scaleRange() { return new float[]{(float)(MIN_METRES/baselineMetres), (float)(MAX_METRES/baselineMetres)}; }

    public float fromMetres(double metres) {
        if (!Double.isFinite(metres) || metres < MIN_METRES || metres > MAX_METRES)
            throw new IllegalArgumentException("Height must be in 1.3..2.0 metres (13..20 dirt)");
        return (float)(metres/baselineMetres);
    }

    public float fromDirts(double dirts) { return fromMetres(dirts/DIRTS_PER_METRE); }

    public void validateScale(float scale) {
        double metres = (double)baselineMetres*scale;
        // Float division at an inclusive endpoint can differ by a fraction of a micrometre.
        if (!Float.isFinite(scale) || metres < MIN_METRES-0.000002 || metres > MAX_METRES+0.000002)
            throw new IllegalArgumentException("Height must be in 1.3..2.0 metres (13..20 dirt)");
    }
}
