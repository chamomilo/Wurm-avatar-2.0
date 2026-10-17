package org.avatar2.core;

import java.util.*;

/** Adult artistic presets; Young is the exact released Build. */
public final class BodyAge {
    public static final Set<String> PRESETS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("young", "middle", "old")));
    private BodyAge() {}
    public static String validate(String value) {
        if (!PRESETS.contains(value)) throw new IllegalArgumentException("Unknown body age: " + value);
        return value;
    }
    public static float weight(String value) {
        validate(value); return "old".equals(value) ? 1 : "middle".equals(value) ? .5f : 0;
    }
}
