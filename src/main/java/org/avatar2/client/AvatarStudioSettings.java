package org.avatar2.client;

import java.io.File;
import java.util.Properties;

public final class AvatarStudioSettings {
    private static boolean panelVisible = true;
    private static boolean debug;
    private static String profileFile = "mods/avatar2/avatar-profile.properties";
    private static String resourcePack = "mods/avatar2/avatar2-resources-0.2.0.jar";

    private AvatarStudioSettings() { }

    public static void configure(Properties properties) {
        if (properties == null) return;
        panelVisible = bool(properties, "panelVisible", panelVisible);
        debug = bool(properties, "debug", debug);
        String configured = properties.getProperty("profileFile");
        if (configured != null && !configured.trim().isEmpty()) {
            profileFile = configured.trim();
        }
        configured = properties.getProperty("resourcePack");
        if (configured != null && !configured.trim().isEmpty()) {
            resourcePack = configured.trim();
        }
    }

    public static boolean panelVisible() { return panelVisible; }
    public static boolean debug() { return debug; }
    public static File profileFile() { return new File(profileFile).getAbsoluteFile(); }
    public static String resourcePack() { return resourcePack; }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }
}
