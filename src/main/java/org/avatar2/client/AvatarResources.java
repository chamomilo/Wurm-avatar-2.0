package org.avatar2.client;

import org.gotti.wurmunlimited.modsupport.packs.ModPacks;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registers the generated bodies as a normal Wurm resource pack. */
final class AvatarResources {
    private static final Logger LOG = Logger.getLogger("Avatar2");
    private static boolean attempted;
    private static boolean loaded;

    private AvatarResources() { }

    static synchronized boolean ensureLoaded() {
        if (attempted) return loaded;
        attempted = true;
        File pack = new File(AvatarStudioSettings.resourcePack());
        if (!pack.isAbsolute()) pack = new File(System.getProperty("user.dir"), pack.getPath());
        pack = pack.getAbsoluteFile();
        if (!pack.isFile()) {
            LOG.severe("Avatar 2.0 resource pack is missing: " + pack);
            return false;
        }
        try {
            loaded = ModPacks.addPack(pack, ModPacks.Options.PREPEND,
                    ModPacks.Options.NORELOAD, ModPacks.Options.NOMAPS,
                    ModPacks.Options.NOARMOR);
            if (loaded) LOG.info("Avatar 2.0 body pack loaded: " + pack);
        } catch (RuntimeException error) {
            LOG.log(Level.SEVERE, "Could not load Avatar 2.0 body pack", error);
        }
        return loaded;
    }
}
