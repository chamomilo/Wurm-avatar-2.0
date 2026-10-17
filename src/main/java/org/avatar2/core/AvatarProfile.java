package org.avatar2.core;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Serializable player choices constrained to Wurm-compatible proportions. */
public final class AvatarProfile {
    public enum Sex { MALE, FEMALE }
    // Legacy preset values remain readable; runtime geometry always uses the
    // canonical Bodybuilder binding, with temporary Shape commands kept separate.
    public enum Preset { ATHLETIC, BODYBUILDER, CUSTOM }

    public static final int SCHEMA_VERSION = 8;
    public static final float MIN_STATURE = 0.96f;
    public static final float MAX_STATURE = 1.04f;

    public Sex sex = Sex.MALE;
    public Preset preset = Preset.BODYBUILDER;
    public boolean enabled = true;
    public float muscle = 0.96f;
    public float mass = 0.72f;
    public float stature = 1.0f;
    public float shoulders = 0.86f;
    public float hips = 0.42f;
    public float head = 0.50f;

    public String shape = "none";
    public float shapeMix;
    public float blendAthletic, blendHeavy, blendOlder;
    public float buildMuscle = 1, buildFat;
    public float buildBreast = .5f, buildShoulders = .5f;
    public float headSize = .5f;
    public String agePreset = "young";
    // Zero preserves the approved native height when migrating schema 1.
    public float heightMetres;
    public String ageSkin = "original";

    public static float nativeHeightMetres(Sex sex) {
        return sex == Sex.FEMALE ? 1.5912297f : 1.729108f;
    }

    public float standingHeightMetres() {
        return heightMetres == 0 ? nativeHeightMetres(sex) : heightMetres;
    }

    public GeometryProbe geometry() {
        if ("proportions".equals(shape)) return new GeometryProbe(0, 0, shape, shapeMix, 0, 0, 0, buildMuscle, buildFat,
                sex == Sex.FEMALE ? buildBreast : .5f, buildShoulders, headSize, agePreset);
        return "blend".equals(shape) ? new GeometryProbe(0, 0, shape, shapeMix, blendAthletic, blendHeavy, blendOlder)
                .withHeadSize(headSize) : new GeometryProbe(0, 0, shape, shapeMix).withHeadSize(headSize);
    }

    /** Convert an endpoint to Blend without changing the currently visible body. */
    public void selectShape(String next) {
        if (!GeometryProbe.SHAPES.contains(next)) throw new IllegalArgumentException("Unknown body shape");
        if ("blend".equals(next)) { useGeometry(geometry().asBlend()); return; }
        if ("proportions".equals(next)) { shape = next; shapeMix = 1; return; }
        shapeMix = "none".equals(next) ? 0 : "none".equals(shape) ? 1 : shapeMix;
        shape = next;
    }

    public void setBlendContribution(int component, float value) { useGeometry(geometry().withContribution(component, value)); }

    private void useGeometry(GeometryProbe geometry) {
        shape = geometry.shape; shapeMix = geometry.shapeMix;
        blendAthletic = geometry.blendAthletic; blendHeavy = geometry.blendHeavy; blendOlder = geometry.blendOlder;
        if ("proportions".equals(geometry.shape)) { buildMuscle = geometry.buildMuscle; buildFat = geometry.buildFat; }
    }

    public boolean autoFitArmor = true;
    public boolean hideOccludedGeometry = true;
    public boolean poseCollisionGuard = true;
    public boolean beardChestGuard = true;
    public boolean shieldHandGuard = true;
    public boolean paperDollUsesAvatar = true;
    public boolean debugCoverage;

    private final EnumMap<ArmorSlot, Float> reflectionMultipliers =
            new EnumMap<ArmorSlot, Float>(ArmorSlot.class);

    public AvatarProfile() {
        for (ArmorSlot slot : ArmorSlot.values()) {
            reflectionMultipliers.put(slot, 1.0f);
        }
    }

    public static AvatarProfile athletic(Sex sex) {
        AvatarProfile profile = new AvatarProfile();
        profile.sex = sex == null ? Sex.MALE : sex;
        profile.preset = Preset.ATHLETIC;
        profile.muscle = profile.sex == Sex.MALE ? 0.82f : 0.72f;
        profile.mass = profile.sex == Sex.MALE ? 0.58f : 0.50f;
        profile.shoulders = profile.sex == Sex.MALE ? 0.70f : 0.54f;
        profile.hips = profile.sex == Sex.MALE ? 0.42f : 0.60f;
        return profile;
    }

    public static AvatarProfile bodybuilder(Sex sex) {
        AvatarProfile profile = athletic(sex);
        profile.preset = Preset.BODYBUILDER;
        profile.muscle = 0.96f;
        profile.mass = profile.sex == Sex.MALE ? 0.72f : 0.63f;
        profile.shoulders = profile.sex == Sex.MALE ? 0.86f : 0.70f;
        return profile;
    }

    public AvatarProfile copy() {
        AvatarProfile result = new AvatarProfile();
        result.sex = sex;
        result.preset = preset;
        result.enabled = enabled;
        result.muscle = muscle;
        result.mass = mass;
        result.stature = stature;
        result.shoulders = shoulders;
        result.hips = hips;
        result.head = head;
        result.shape = shape;
        result.shapeMix = shapeMix;
        result.blendAthletic = blendAthletic; result.blendHeavy = blendHeavy; result.blendOlder = blendOlder;
        result.buildMuscle = buildMuscle; result.buildFat = buildFat;
        result.buildBreast = buildBreast;
        result.buildShoulders = buildShoulders;
        result.headSize = headSize;
        result.agePreset = agePreset;
        result.heightMetres = heightMetres;
        result.ageSkin = ageSkin;
        result.autoFitArmor = autoFitArmor;
        result.hideOccludedGeometry = hideOccludedGeometry;
        result.poseCollisionGuard = poseCollisionGuard;
        result.beardChestGuard = beardChestGuard;
        result.shieldHandGuard = shieldHandGuard;
        result.paperDollUsesAvatar = paperDollUsesAvatar;
        result.debugCoverage = debugCoverage;
        result.reflectionMultipliers.putAll(reflectionMultipliers);
        return result;
    }

    public void normalize() {
        if (sex == null) sex = Sex.MALE;
        if (preset == null) preset = Preset.CUSTOM;
        muscle = clampFinite(muscle, 0.96f, 0f, 1f);
        mass = clampFinite(mass, 0.72f, 0f, 1f);
        stature = clampFinite(stature, 1f, MIN_STATURE, MAX_STATURE);
        shoulders = clampFinite(shoulders, 0.86f, 0f, 1f);
        hips = clampFinite(hips, 0.42f, 0f, 1f);
        head = clampFinite(head, 0.50f, 0f, 1f);
        if (!GeometryProbe.SHAPES.contains(shape)) shape = "none";
        shapeMix = "none".equals(shape) ? 0 : clampFinite(shapeMix, 1, 0, 1);
        blendAthletic = clampFinite(blendAthletic, 0, 0, 1);
        blendHeavy = clampFinite(blendHeavy, 0, 0, 1);
        blendOlder = clampFinite(blendOlder, 0, 0, 1);
        buildMuscle = clampFinite(buildMuscle, 1, 0, 1);
        buildFat = clampFinite(buildFat, 0, 0, 1);
        buildBreast = clampFinite(buildBreast, .5f, 0, 1);
        buildShoulders = clampFinite(buildShoulders, .5f, 0, 1);
        headSize = clampFinite(headSize, .5f, 0, 1);
        if (!Float.isFinite(heightMetres)) heightMetres = 0;
        else if (heightMetres != 0) heightMetres = clampFinite(heightMetres, 0,
                (float)HeightLimits.MIN_METRES, (float)HeightLimits.MAX_METRES);
        if (!AgeSkin.PRESETS.contains(ageSkin)) ageSkin = "original";
        if (!BodyAge.PRESETS.contains(agePreset)) agePreset = "young";
        for (ArmorSlot slot : ArmorSlot.values()) {
            reflectionMultipliers.put(slot, clampFinite(reflectionMultiplier(slot), 1f, 0f, 1f));
        }
    }

    public float reflectionMultiplier(ArmorSlot slot) {
        Float value = reflectionMultipliers.get(slot);
        return value == null ? 1.0f : value;
    }

    public void setReflectionMultiplier(ArmorSlot slot, float value) {
        if (slot == null) return;
        reflectionMultipliers.put(slot, clampFinite(value, 1f, 0f, 1f));
        preset = Preset.CUSTOM;
    }

    public Map<ArmorSlot, Float> reflectionMultipliers() {
        return new EnumMap<ArmorSlot, Float>(reflectionMultipliers);
    }

    public Properties toProperties() {
        normalize();
        Properties output = new Properties();
        output.setProperty("schema", Integer.toString(SCHEMA_VERSION));
        output.setProperty("sex", sex.name());
        output.setProperty("preset", preset.name());
        output.setProperty("enabled", Boolean.toString(enabled));
        put(output, "muscle", muscle);
        put(output, "mass", mass);
        put(output, "stature", stature);
        put(output, "shoulders", shoulders);
        put(output, "hips", hips);
        put(output, "head", head);
        output.setProperty("shape", shape);
        put(output, "shapeMix", shapeMix);
        put(output, "blendAthletic", blendAthletic); put(output, "blendHeavy", blendHeavy); put(output, "blendOlder", blendOlder);
        put(output, "buildMuscle", buildMuscle); put(output, "buildFat", buildFat);
        put(output, "buildBreast", buildBreast);
        put(output, "buildShoulders", buildShoulders);
        put(output, "headSize", headSize);
        output.setProperty("agePreset", agePreset);
        put(output, "heightMetres", heightMetres);
        output.setProperty("ageSkin", ageSkin);
        output.setProperty("autoFitArmor", Boolean.toString(autoFitArmor));
        output.setProperty("hideOccludedGeometry", Boolean.toString(hideOccludedGeometry));
        output.setProperty("poseCollisionGuard", Boolean.toString(poseCollisionGuard));
        output.setProperty("beardChestGuard", Boolean.toString(beardChestGuard));
        output.setProperty("shieldHandGuard", Boolean.toString(shieldHandGuard));
        output.setProperty("paperDollUsesAvatar", Boolean.toString(paperDollUsesAvatar));
        output.setProperty("debugCoverage", Boolean.toString(debugCoverage));
        for (ArmorSlot slot : ArmorSlot.values()) {
            put(output, "reflection." + slot.name(), reflectionMultiplier(slot));
        }
        return output;
    }

    public static AvatarProfile fromProperties(Properties properties) {
        AvatarProfile result = new AvatarProfile();
        if (properties == null) return result;
        result.sex = enumValue(Sex.class, properties.getProperty("sex"), result.sex);
        result.preset = enumValue(Preset.class, properties.getProperty("preset"), result.preset);
        result.enabled = bool(properties, "enabled", result.enabled);
        result.muscle = number(properties, "muscle", result.muscle);
        result.mass = number(properties, "mass", result.mass);
        result.stature = number(properties, "stature", result.stature);
        result.shoulders = number(properties, "shoulders", result.shoulders);
        result.hips = number(properties, "hips", result.hips);
        result.head = number(properties, "head", result.head);
        // Schema 1 did not apply stature or temporary Lab settings to geometry.
        // Preserve its visible native body rather than inventing a saved trial.
        if (number(properties, "schema", 1) >= 2) {
            result.shape = properties.getProperty("shape", result.shape);
            result.shapeMix = number(properties, "shapeMix", result.shapeMix);
            result.heightMetres = number(properties, "heightMetres", result.heightMetres);
            result.ageSkin = properties.getProperty("ageSkin", result.ageSkin);
        }
        if (number(properties, "schema", 1) >= 3) {
            result.blendAthletic = number(properties, "blendAthletic", 0);
            result.blendHeavy = number(properties, "blendHeavy", 0);
            result.blendOlder = number(properties, "blendOlder", 0);
        } else if ("blend".equals(result.shape)) { result.shape = "none"; result.shapeMix = 0; }
        if (number(properties, "schema", 1) >= 4) {
            result.buildMuscle = number(properties, "buildMuscle", 1);
            result.buildFat = number(properties, "buildFat", 0);
        } else if ("proportions".equals(result.shape)) { result.shape = "none"; result.shapeMix = 0; }
        if (number(properties, "schema", 1) >= 5) result.buildBreast = number(properties, "buildBreast", .5f);
        if (number(properties, "schema", 1) >= 6) result.buildShoulders = number(properties, "buildShoulders", .5f);
        if (number(properties, "schema", 1) >= 7) result.headSize = number(properties, "headSize", .5f);
        if (number(properties, "schema", 1) >= 8) result.agePreset = properties.getProperty("agePreset", "young");
        result.autoFitArmor = bool(properties, "autoFitArmor", result.autoFitArmor);
        result.hideOccludedGeometry = bool(properties, "hideOccludedGeometry", result.hideOccludedGeometry);
        result.poseCollisionGuard = bool(properties, "poseCollisionGuard", result.poseCollisionGuard);
        result.beardChestGuard = bool(properties, "beardChestGuard", result.beardChestGuard);
        result.shieldHandGuard = bool(properties, "shieldHandGuard", result.shieldHandGuard);
        result.paperDollUsesAvatar = bool(properties, "paperDollUsesAvatar", result.paperDollUsesAvatar);
        result.debugCoverage = bool(properties, "debugCoverage", result.debugCoverage);
        for (ArmorSlot slot : ArmorSlot.values()) {
            result.reflectionMultipliers.put(slot, number(properties,
                    "reflection." + slot.name(), 1f));
        }
        result.preset = enumValue(Preset.class,
                properties.getProperty("preset"), result.preset);
        result.normalize();
        return result;
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static float number(Properties p, String key, float fallback) {
        try {
            String value = p.getProperty(key);
            return value == null ? fallback : Float.parseFloat(value.trim());
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static void put(Properties p, String key, float value) {
        p.setProperty(key, Float.toString(value));
    }

    private static float clampFinite(float value, float fallback,
                                     float minimum, float maximum) {
        float finite = Float.isNaN(value) || Float.isInfinite(value) ? fallback : value;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
