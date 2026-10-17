package org.avatar2.client;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import org.avatar2.core.ArmorSlot;
import org.avatar2.core.AvatarProfile;
import org.avatar2.core.AvatarProfileRepository;
import org.avatar2.core.MaterialQualitySurface;
import org.avatar2.core.MaterialReflection;
import org.avatar2.lab.AnimationLab;

import java.io.IOException;
import java.util.Locale;
import java.util.Arrays;
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
            AvatarRuntimeState.apply(profile);
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
    public boolean labEnabled() { return AnimationLab.isEnabled(); }
    public AnimationLab.StudioControls testControls(boolean visible) { return AnimationLab.studioControls(visible); }
    public synchronized String activeModel() {
        ensureProfile();
        return AvatarModelRuntime.modelFor(profile);
    }

    public synchronized float value(String control) {
        if ("testHeight".equals(control)) return testControls(false).heightDirts;
        if ("testMix".equals(control)) return testControls(false).shapeMix;
        if ("testAthletic".equals(control)) return testControls(false).blendAthletic;
        if ("testHeavy".equals(control)) return testControls(false).blendHeavy;
        if ("testOlder".equals(control)) return testControls(false).blendOlder;
        if ("testMuscle".equals(control)) return testControls(false).buildMuscle;
        if ("testFat".equals(control)) return testControls(false).buildFat;
        if ("testBreast".equals(control)) return testControls(false).buildBreast;
        if ("testShoulders".equals(control)) return testControls(false).buildShoulders;
        if ("testHeadSize".equals(control)) return testControls(false).headSize;
        ensureProfile();
        if ("muscle".equals(control)) return profile.muscle;
        if ("mass".equals(control)) return profile.mass;
        if ("stature".equals(control)) return profile.stature;
        if ("shoulders".equals(control)) return profile.shoulders;
        if ("hips".equals(control)) return profile.hips;
        if ("head".equals(control)) return profile.head;
        if ("heightDirts".equals(control)) return profile.standingHeightMetres()*10;
        if ("shapeMix".equals(control)) return profile.shapeMix;
        if ("blendAthletic".equals(control)) return profile.blendAthletic;
        if ("blendHeavy".equals(control)) return profile.blendHeavy;
        if ("blendOlder".equals(control)) return profile.blendOlder;
        if ("buildMuscle".equals(control)) return profile.buildMuscle;
        if ("buildFat".equals(control)) return profile.buildFat;
        if ("buildBreast".equals(control)) return profile.sex == AvatarProfile.Sex.FEMALE ? profile.buildBreast : .5f;
        if ("buildShoulders".equals(control)) return profile.buildShoulders;
        if ("headSize".equals(control)) return profile.headSize;
        if ("slotReflection".equals(control))
            return profile.reflectionMultiplier(selectedSlot);
        if ("previewQuality".equals(control)) return previewQuality;
        throw new IllegalArgumentException("Unknown avatar control: " + control);
    }

    public synchronized void setValue(String control, float value) {
        if (control.startsWith("test")) {
            try {
                if ("testHeight".equals(control)) AnimationLab.studioHeight(value);
                else if ("testHeadSize".equals(control)) AnimationLab.studioHeadSize(value);
                else if ("testMix".equals(control)) AnimationLab.studioMix(value);
                else if (Arrays.asList("testAthletic","testHeavy","testOlder").contains(control)) AnimationLab.studioContribution(control.substring(4),value);
                else if (Arrays.asList("testMuscle","testFat","testBreast","testShoulders").contains(control)) AnimationLab.studioBuild(control.substring(4),value);
                else throw new IllegalArgumentException("Unknown test control");
                status = "Temporary body test applied; Reset test restores the body";
            } catch (RuntimeException error) { status = error.getMessage(); }
            return;
        }
        ensureProfile();
        if ("muscle".equals(control)) profile.muscle = value;
        else if ("mass".equals(control)) profile.mass = value;
        else if ("stature".equals(control)) profile.stature = value;
        else if ("shoulders".equals(control)) profile.shoulders = value;
        else if ("hips".equals(control)) profile.hips = value;
        else if ("head".equals(control)) profile.head = value;
        else if ("heightDirts".equals(control)) profile.heightMetres = value/10;
        else if ("shapeMix".equals(control)) profile.shapeMix = value;
        else if ("blendAthletic".equals(control)) profile.setBlendContribution(0, value);
        else if ("blendHeavy".equals(control)) profile.setBlendContribution(1, value);
        else if ("blendOlder".equals(control)) profile.setBlendContribution(2, value);
        else if ("buildMuscle".equals(control)) profile.buildMuscle = value;
        else if ("buildFat".equals(control)) profile.buildFat = value;
        else if ("buildShoulders".equals(control)) profile.buildShoulders = value;
        else if ("headSize".equals(control)) profile.headSize = value;
        else if ("buildBreast".equals(control)) {
            if (profile.sex != AvatarProfile.Sex.FEMALE || !"proportions".equals(profile.shape))
                throw new IllegalArgumentException("Breast volume requires female Build");
            profile.buildBreast = value;
        }
        else if ("slotReflection".equals(control)) {
            profile.setReflectionMultiplier(selectedSlot, value);
        } else if ("previewQuality".equals(control)) {
            previewQuality = Math.max(0f, Math.min(100f, value));
            status = qualitySummary();
            return;
        } else throw new IllegalArgumentException("Unknown avatar control: " + control);
        profile.preset = AvatarProfile.Preset.CUSTOM;
        profile.normalize();
        changed("Previewing body changes; Save keeps them");
    }

    public synchronized void perform(String action) {
        if (action.startsWith("shape:") || action.startsWith("motion:") || action.startsWith("skin:") || action.startsWith("age:") || "testReset".equals(action)) {
            try {
                if (action.startsWith("shape:")) AnimationLab.studioShape(action.substring(6));
                else if (action.startsWith("motion:")) AnimationLab.studioMotion(action.substring(7));
                else if (action.startsWith("skin:")) AnimationLab.studioSkin(action.substring(5));
                else if (action.startsWith("age:")) AnimationLab.studioAge(action.substring(4));
                else AnimationLab.studioReset();
                status="Temporary test updated; profile is unchanged";
            } catch (RuntimeException error) { status=error.getMessage(); }
            return;
        }
        if ("labToggle".equals(action)) {
            toggleLab();
            return;
        }
        if ("labOpen".equals(action)) {
            openLab();
            return;
        }
        ensureProfile();
        if (action.startsWith("bodyShape:")) {
            String shape = action.substring(10);
            if (!org.avatar2.core.GeometryProbe.SHAPES.contains(shape)) throw new IllegalArgumentException("Unknown body shape");
            profile.selectShape(shape);
            profile.preset = AvatarProfile.Preset.CUSTOM;
            changed("Shape preview updated; Save keeps it");
        } else if (action.startsWith("bodyAge:")) {
            if(!"proportions".equals(profile.shape))throw new IllegalStateException("Select Build before body age");
            profile.agePreset=org.avatar2.core.BodyAge.validate(action.substring(8));
            profile.ageSkin=profile.agePreset;
            profile.preset=AvatarProfile.Preset.CUSTOM;
            changed("Adult body and skin preview updated; Save keeps them");
        } else if (action.startsWith("bodySkin:")) {
            profile.ageSkin = org.avatar2.core.AgeSkin.validate(action.substring(9));
            profile.preset = AvatarProfile.Preset.CUSTOM;
            changed("Skin preview updated; Save keeps it");
        } else if ("nativeHeight".equals(action)) {
            profile.heightMetres = 0;
            changed("Original height restored; not saved");
        } else if ("sex".equals(action)) {
            profile.sex = profile.sex == AvatarProfile.Sex.MALE
                    ? AvatarProfile.Sex.FEMALE : AvatarProfile.Sex.MALE;
            profile.preset = AvatarProfile.Preset.CUSTOM;
            changed("Body sex preview updated; Save keeps it");
        } else if ("enabled".equals(action)) {
            profile.enabled = !profile.enabled;
            changed(profile.enabled ? "Avatar 2.0 enabled" : "Vanilla body selected");
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
            save(profile.copy(), false);
        } else if ("saveTestedBody".equals(action)) {
            try {
                save(AnimationLab.studioProfile(), true);
            } catch (RuntimeException error) {
                status = "Body not saved: " + error.getMessage();
            }
        } else if ("revert".equals(action)) {
            profile = saved.copy();
            AvatarRuntimeState.apply(profile);
            dirty = false;
            clearTests();
            status = "Reverted to last saved profile";
        } else if ("reset".equals(action)) {
            replace(AvatarProfile.bodybuilder(profile.sex), "Muscular defaults restored; not saved");
        }
    }

    public synchronized MaterialQualitySurface.Sample qualitySample() {
        return MaterialQualitySurface.evaluate(previewMaterial,
                previewQuality, profile.reflectionMultiplier(selectedSlot));
    }

    private void toggleLab() {
        if (AnimationLab.isEnabled()) {
            AnimationLab.stop();
            status = "Animation Lab off; returning to game motion";
            return;
        }
        try {
            AnimationLab.start();
            status = "Animation Lab on; Open Lab shows the controls";
        } catch (Exception error) {
            status = "Animation Lab could not start; see client log";
            LOG.log(Level.WARNING, "Could not start Animation Lab", error);
        }
    }

    private void openLab() {
        try {
            AnimationLab.start();
            AnimationLab.open();
            status = "Animation Lab on; controls opened in browser";
        } catch (Exception error) {
            status = AnimationLab.isEnabled()
                    ? "Animation Lab on; browser could not open"
                    : "Animation Lab could not start; see client log";
            LOG.log(Level.WARNING, "Could not open Animation Lab", error);
        }
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
        clearTests();
        status = "Applied to Avatar 2.0 runtime (revision "
                + AvatarRuntimeState.revision() + ")";
    }

    private void save(AvatarProfile chosen, boolean keepTests) {
        try {
            chosen.normalize();
            repository.save(chosen);
            profile = chosen;
            saved = profile.copy();
            AvatarRuntimeState.apply(profile);
            if (!keepTests) clearTests();
            dirty = false;
            status = "Saved and applied " + repository.file().getName();
        } catch (IOException error) {
            status = "Save failed; see client log";
            LOG.log(Level.WARNING, "Could not save avatar profile", error);
        }
    }

    private void clearTests() {
        if (AnimationLab.isEnabled() && AnimationLab.studioControls(false).ready) AnimationLab.studioReset();
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
        AvatarRuntimeState.apply(profile);
        clearTests();
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
