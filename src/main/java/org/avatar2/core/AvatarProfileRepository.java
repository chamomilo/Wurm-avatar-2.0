package org.avatar2.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Crash-resistant single-profile store with one known-good backup. */
public final class AvatarProfileRepository {
    private final File file;

    public AvatarProfileRepository(File file) {
        this.file = file.getAbsoluteFile();
    }

    public AvatarProfile load() throws IOException {
        if (!file.isFile()) return new AvatarProfile();
        Properties values = new Properties();
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            values.load(input);
        }
        return AvatarProfile.fromProperties(values);
    }

    public void save(AvatarProfile profile) throws IOException {
        AvatarProfile safe = profile == null ? new AvatarProfile() : profile.copy();
        safe.normalize();
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File temporary = new File(file.getPath() + ".tmp");
        try (OutputStream output = new BufferedOutputStream(
                new FileOutputStream(temporary))) {
            safe.toProperties().store(output, "Avatar 2.0 character profile");
        }
        if (file.isFile()) {
            Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
        try {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public File file() {
        return file;
    }
}

