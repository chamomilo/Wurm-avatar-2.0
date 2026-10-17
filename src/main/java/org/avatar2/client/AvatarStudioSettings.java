package org.avatar2.client;

import java.io.File;
import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chamomilo.wurm.update.ModSetting;

public final class AvatarStudioSettings {
    private static boolean panelVisible = true;
    private static boolean debug;
    private static String profileFile = "mods/avatar2/avatar-profile.properties";
    private static String resourcePack = "mods/avatar2/avatar2-resources-" + Avatar2Mod.VERSION + ".jar";

    private AvatarStudioSettings() { }

    static List<ModSetting> specs() {
        return Arrays.asList(
                ModSetting.bool("Interface", "panelVisible", "Show Avatar Studio",
                        "Show or hide Avatar Studio now and when the HUD is created. You can also toggle it with /avatar2 or HUD Settings.", true, true),
                ModSetting.text("Storage", "profileFile", "Avatar profile file",
                        "File used to save and load your appearance. A relative path starts in WurmLauncher. Takes effect after restarting the client; existing profiles are not moved.",
                        "mods/avatar2/avatar-profile.properties", false),
                ModSetting.text("Resources", "resourcePack", "Avatar resource pack",
                        "Body, skin and animation resource JAR. Keep the release default unless using a custom pack. Takes effect after restarting the client.",
                        "mods/avatar2/avatar2-resources-" + Avatar2Mod.VERSION + ".jar", false),
                ModSetting.bool("Diagnostics", "debug", "Diagnostic logging",
                        "Enable fine-level Avatar diagnostic messages. The client's log handler may filter these messages.", false, true));
    }

    public static void configure(Properties properties) {
        if (properties == null) return;
        panelVisible = bool(properties, "panelVisible", panelVisible);
        debug = bool(properties, "debug", debug);
        Logger.getLogger("Avatar2").setLevel(debug ? Level.FINE : Level.INFO);
        Logger.getLogger("Avatar2").fine("Avatar settings refreshed");
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
