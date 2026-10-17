package org.avatar2.client;

import org.chamomilo.wurm.update.SharedModSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.Assert.*;

public class SharedSettingsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void startupPathsStayInactiveAndUneditedPackFollowsNextRelease() throws Exception {
        String previousHome = System.getProperty("user.home");
        Properties original = defaults();
        try {
            System.setProperty("user.home", temporary.getRoot().getAbsolutePath());
            new Avatar2Mod().configure(original);
            SharedModSettings.Provider provider = SharedModSettings.provider("avatar2");
            assertEquals(4, provider.settings().size());
            provider.change("profileFile", "profiles/custom.properties");
            provider.change("panelVisible", "false");
            provider.change("debug", "true");
            assertFalse(AvatarStudioSettings.panelVisible());
            assertTrue(AvatarStudioSettings.debug());
            assertEquals(new java.io.File(original.getProperty("profileFile")).getAbsoluteFile(), AvatarStudioSettings.profileFile());
            Path store = temporary.getRoot().toPath().resolve(".chamomilo/mod-settings/avatar2.properties");
            Properties overrides = new Properties();
            try (InputStream input = Files.newInputStream(store)) { overrides.load(input); }
            assertEquals("profiles/custom.properties", overrides.getProperty("profileFile"));
            assertFalse(overrides.containsKey("resourcePack"));
            Properties upgraded = defaults();
            upgraded.setProperty("resourcePack", "mods/avatar2/avatar2-resources-next.jar");
            new Avatar2Mod().configure(upgraded);
            assertEquals(new java.io.File("profiles/custom.properties").getAbsoluteFile(), AvatarStudioSettings.profileFile());
            assertEquals(upgraded.getProperty("resourcePack"), AvatarStudioSettings.resourcePack());
        } finally {
            System.setProperty("user.home", previousHome);
            AvatarStudioSettings.configure(original);
        }
    }

    @Test public void invalidPackNeverReachesStorageOrActiveSettings() throws Exception {
        String previousHome = System.getProperty("user.home");
        Properties original = defaults();
        try {
            System.setProperty("user.home", temporary.getRoot().getAbsolutePath());
            new Avatar2Mod().configure(original);
            SharedModSettings.Provider provider = SharedModSettings.provider("avatar2");
            try { provider.change("resourcePack", " "); fail("Empty resource path accepted"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(original.getProperty("resourcePack"), provider.value("resourcePack"));
            assertEquals(original.getProperty("resourcePack"), AvatarStudioSettings.resourcePack());
            assertFalse(Files.exists(temporary.getRoot().toPath().resolve(".chamomilo/mod-settings/avatar2.properties")));
        } finally {
            System.setProperty("user.home", previousHome);
            AvatarStudioSettings.configure(original);
        }
    }

    private static Properties defaults() {
        Properties result = new Properties();
        for (org.chamomilo.wurm.update.ModSetting spec : AvatarStudioSettings.specs()) result.setProperty(spec.key, spec.defaultValue);
        return result;
    }
}
