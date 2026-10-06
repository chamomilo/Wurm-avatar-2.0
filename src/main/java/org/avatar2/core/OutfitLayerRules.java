package org.avatar2.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Deterministic top-down coverage; dynamic contact is handled separately. */
public final class OutfitLayerRules {
    public enum Layer {
        BODY(0), UNDERGARMENT(10), SLEEVE(20), CHEST_ARMOR(30), PAULDRON(40), SHIELD(50);
        public final int priority;
        Layer(int priority) { this.priority = priority; }
    }

    public enum Region {
        CHEST, UPPER_BACK, LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_UPPER_ARM, RIGHT_UPPER_ARM, LEFT_FOREARM, RIGHT_FOREARM
    }

    private OutfitLayerRules() { }

    public static boolean hides(Layer upper, Layer lower, Region region) {
        if (upper == null || lower == null || region == null
                || upper.priority <= lower.priority) return false;
        if (upper == Layer.PAULDRON) {
            return region == Region.LEFT_SHOULDER
                    || region == Region.RIGHT_SHOULDER
                    || region == Region.LEFT_UPPER_ARM
                    || region == Region.RIGHT_UPPER_ARM
                    || region == Region.CHEST
                    || region == Region.UPPER_BACK;
        }
        if (upper == Layer.CHEST_ARMOR) {
            return lower == Layer.BODY || lower == Layer.UNDERGARMENT
                    || lower == Layer.SLEEVE && (region == Region.CHEST
                    || region == Region.UPPER_BACK);
        }
        if (upper == Layer.SLEEVE) return lower == Layer.BODY;
        return false;
    }

    public static Set<Region> hiddenRegions(Layer upper, Layer lower) {
        EnumSet<Region> result = EnumSet.noneOf(Region.class);
        for (Region region : Region.values()) {
            if (hides(upper, lower, region)) result.add(region);
        }
        return Collections.unmodifiableSet(result);
    }
}

