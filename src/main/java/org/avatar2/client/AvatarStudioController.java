package org.avatar2.client;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import org.avatar2.core.ArmorSlot;
import org.avatar2.core.AvatarProfile;
import org.avatar2.core.AvatarProfileRepository;
import org.avatar2.core.MaterialQualitySurface;
import org.avatar2.core.MaterialReflection;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** UI-facing editor. No GL calls are made here, so every transition is testable. */
public final class AvatarStudioController {
    private static final Logger LOG = Logger.getLogger("Avatar2");

    private final HeadsUpDisplay hud;
    private final AvatarProfileRepository repository;
    private AvatarProfile profile;
    private AvatarProfile saved;
    private ArmorSlot selectedSlot = ArmorSlot.TORSO;
    private float previewQuality = 75f;
    private String previewMaterial = "steel";
    private boolean dirty;
    private String status = "Avatar profile loaded";

    public AvatarStudioController(HeadsUpDisplay hud,
                                  AvatarProfileRepository repository) {
        this.hud = hud;
        this.repository = repository;
    }

    public synchronized void initialize() {
        try {
            profile = repository.load();
            saved = profile.copy();
            AvatarRuntimeState.apply(profile);
            status = repository.file().isFile()
                    ? "Loaded " + repository.file().getName()
                    : "New muscular bodybuilder profile";
        } catch (IOException error) {
            profile = AvatarProfile.bodybuilder(AvatarProfile.Sex.MALE);
            saved = profile.copy();
            status = "Profile read failed; using safe defaults";
            LOG.log(Level.WARNING, "Could not load avatar profile", error);
        }
    }

    public synchronized AvatarProfile profile() {
        ensureProfile();
        return profile;
    }

    public synchronized ArmorSlot selectedSlot() { return selectedSlot; }
    public synchronized float previewQuality() { return previewQuality; }
    public synchronized String previewMaterial() { return previewMaterial; }
    public synchronized boolean dirty() { return dirty; }
    public synchronized String status() { return status; }
    public synchronized String activeModel() {
        ensureProfile();
        return AvatarModelRuntime.modelFor(profile);
    }

    public synchronized float value(String control) {
        ensureProfile();
        if ("muscle".equals(control)) return profile.muscle;
        if ("mass".equals(control)) return profile.mass;
        if ("stature".equals(control)) return profile.stature;
        if ("shoulders".equals(control)) return profile.shoulders;
        if ("hips".equals(control)) return profile.hips;
        if ("head".equals(control)) return profile.head;
        if ("slotReflection".equals(control))
            return profile.reflectionMultiplier(selectedSlot);
        if ("previewQuality".equals(control)) return previewQuality;
        throw new IllegalArgumentException("Unknown avatar control: " + control);
    }

    public synchronized void setValue(String control, float value) {
        ensureProfile();
        if ("muscle".equals(control)) profile.muscle = value;
        else if ("mass".equals(control)) profile.mass = value;
        else if ("stature".equals(control)) profile.stature = value;
        else if ("shoulders".equals(control)) profile.shoulders = value;
        else if ("hips".equals(control)) profile.hips = value;
        else if ("head".equals(control)) profile.head = value;
        else if ("slotReflection".equals(control)) {
            profile.setReflectionMultiplier(selectedSlot, value);
        } else if ("previewQuality".equals(control)) {
            previewQuality = Math.max(0f, Math.min(100f, value));
            status = qualitySummary();
            return;
        } else throw new IllegalArgumentException("Unknown avatar control: " + control);
        profile.preset = AvatarProfile.Preset.CUSTOM;
        profile.normalize();
        changed("Unsaved profile changes");
    }

    public synchronized void perform(String action) {
        ensureProfile();
        if ("sex".equals(action)) {
            profile.sex = profile.sex == AvatarProfile.Sex.MALE
                    ? AvatarProfile.Sex.FEMALE : AvatarProfile.Sex.MALE;
            profile.preset = AvatarProfile.Preset.CUSTOM;
            changed("Body sex changed; press Apply to rebuild the live avatar");
        } else if ("enabled".equals(action)) {
            profile.enabled = !profile.enabled;
            changed(profile.enabled ? "Avatar 2.0 enabled" : "Vanilla body selected");
        } else if ("athletic".equals(action)) {
            replace(AvatarProfile.athletic(profile.sex), "Athletic preset selected");
        } else if ("bodybuilder".equals(action)) {
            replace(AvatarProfile.bodybuilder(profile.sex), "Bodybuilder preset selected");
        } else if (action.startsWith("toggle:")) {
            toggle(action.substring("toggle:".length()));
        } else if (action.startsWith("slot:")) {
            selectedSlot = ArmorSlot.valueOf(action.substring(5));
            status = qualitySummary();
        } else if ("material".equals(action)) {
            previewMaterial = nextMaterial(previewMaterial);
            status = qualitySummary();
        } else if ("apply".equals(action)) {
            apply();
        } else if ("save".equals(action)) {
            save();
        } else if ("revert".equals(action)) {
            profile = saved.copy();
            AvatarRuntimeState.apply(profile);
            dirty = false;
            status = "Reverted to last saved profile";
        } else if ("reset".equals(action)) {
            replace(AvatarProfile.bodybuilder(profile.sex), "Muscular defaults restored; not saved");
        }
    }

    public synchronized MaterialQualitySurface.Sample qualitySample() {
        return MaterialQualitySurface.evaluate(previewMaterial,
                previewQuality, profile.reflectionMultiplier(selectedSlot));
    }

    private void toggle(String name) {
        if ("autoFitArmor".equals(name)) profile.autoFitArmor = !profile.autoFitArmor;
        else if ("hideOccludedGeometry".equals(name)) profile.hideOccludedGeometry = !profile.hideOccludedGeometry;
        else if ("poseCollisionGuard".equals(name)) profile.poseCollisionGuard = !profile.poseCollisionGuard;
        else if ("beardChestGuard".equals(name)) profile.beardChestGuard = !profile.beardChestGuard;
        else if ("shieldHandGuard".equals(name)) profile.shieldHandGuard = !profile.shieldHandGuard;
        else if ("paperDollUsesAvatar".equals(name)) profile.paperDollUsesAvatar = !profile.paperDollUsesAvatar;
        else if ("debugCoverage".equals(name)) profile.debugCoverage = !profile.debugCoverage;
        else throw new IllegalArgumentException("Unknown toggle: " + name);
        changed("Unsaved fit/render changes");
    }

    private void apply() {
        profile.normalize();
        AvatarRuntimeState.apply(profile);
        status = "Applied to Avatar 2.0 runtime (revision "
                + AvatarRuntimeState.revision() + ")";
    }

    private void save() {
        try {
            profile.normalize();
            repository.save(profile);
            saved = profile.copy();
            AvatarRuntimeState.apply(profile);
            dirty = false;
            status = "Saved and applied " + repository.file().getName();
        } catch (IOException error) {
            status = "Save failed; see client log";
            LOG.log(Level.WARNING, "Could not save avatar profile", error);
        }
    }

    private String qualitySummary() {
        float percent = MaterialReflection.percent(previewMaterial,
                previewQuality, profile.reflectionMultiplier(selectedSlot));
        return String.format(Locale.ROOT, "%s %s at QL %.1f: reflection %.2f%%",
                selectedSlot.displayName(), previewMaterial, previewQuality, percent);
    }

    private void replace(AvatarProfile replacement, String message) {
        profile = replacement;
        changed(message);
    }

    private void changed(String message) {
        profile.normalize();
        dirty = true;
        status = message;
    }

    private void ensureProfile() {
        if (profile == null) initialize();
    }

    private static String nextMaterial(String material) {
        if ("steel".equals(material)) return "gold";
        if ("gold".equals(material)) return "silver";
        if ("silver".equals(material)) return "copper";
        if ("copper".equals(material)) return "adamantine";
        return "steel";
    }
}
