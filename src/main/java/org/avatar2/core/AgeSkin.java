package org.avatar2.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Adult diffuse-only Lab presets; deliberately independent of body morphology. */
public final class AgeSkin {
    public static final Set<String> PRESETS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("original", "young", "middle", "old")));

    private AgeSkin() { }

    public static String validate(String preset) {
        if (!PRESETS.contains(preset)) throw new IllegalArgumentException("Unknown age skin: " + preset);
        return preset;
    }

    public static String file(String sex, String preset) {
        validate(preset);
        if (!"male".equals(sex) && !"female".equals(sex))
            throw new IllegalArgumentException("Unknown skin sex: " + sex);
        String age = "middle".equals(preset) ? "middleage" : "old".equals(preset) ? "old" : "young";
        return age + "_lightskinned_" + sex + "_diffuse.png";
    }
}
