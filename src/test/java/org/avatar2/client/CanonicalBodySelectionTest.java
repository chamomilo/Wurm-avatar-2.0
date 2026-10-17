package org.avatar2.client;

import org.avatar2.core.AvatarProfile;
import org.avatar2.core.AvatarProfileRepository;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class CanonicalBodySelectionTest {
    @Test public void oldPresetsAndParameterThresholdsUseOneBindingPerSex() {
        for (AvatarProfile.Sex sex : AvatarProfile.Sex.values())
            for (AvatarProfile.Preset preset : AvatarProfile.Preset.values())
                for (float muscle : new float[]{0, .89f, .9f, 1})
                    for (float mass : new float[]{0, .67f, .68f, 1}) {
                        AvatarProfile profile = new AvatarProfile();
                        profile.sex = sex; profile.preset = preset;
                        profile.muscle = muscle; profile.mass = mass;
                        assertEquals("model.avatar2.body." + sex.name().toLowerCase(java.util.Locale.ROOT) + ".bodybuilder",
                                AvatarModelRuntime.modelFor(profile));
                        profile.enabled = false;
                        assertEquals("model.creature.humanoid.human.player." + sex.name().toLowerCase(java.util.Locale.ROOT),
                                AvatarModelRuntime.modelFor(profile));
                    }
    }

    @Test public void legacyAthleticProfileLoadsWithoutRewritingTheSavedFile() throws Exception {
        Path folder = Files.createTempDirectory("avatar2-canonical-profile-");
        Path file = folder.resolve("profile.properties");
        byte[] original = "schema=1\nsex=FEMALE\npreset=ATHLETIC\nmuscle=0.72\nmass=0.50\nenabled=true\n".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(file, original);
            AvatarStudioController controller = new AvatarStudioController(null, new AvatarProfileRepository(file.toFile()));
            controller.initialize();
            assertEquals("model.avatar2.body.female.bodybuilder", controller.activeModel());
            assertEquals(.72f, controller.profile().muscle, 0);
            assertArrayEquals(original, Files.readAllBytes(file));
            assertFalse(controller.dirty());
            controller.perform("sex");
            assertEquals("model.avatar2.body.male.bodybuilder", controller.activeModel());
            controller.perform("revert");
            assertEquals("model.avatar2.body.female.bodybuilder", controller.activeModel());
            assertArrayEquals(original, Files.readAllBytes(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        }
    }
}
