package org.avatar2.client;

import org.avatar2.core.AvatarProfile;

/** Atomic hand-off from the editor to render/fit hooks added by later pipeline stages. */
public final class AvatarRuntimeState {
    private static volatile AvatarProfile active = AvatarProfile.bodybuilder(AvatarProfile.Sex.MALE);
    private static volatile int revision = 1;

    private AvatarRuntimeState() { }

    public static synchronized void apply(AvatarProfile profile) {
        AvatarProfile next = profile == null ? new AvatarProfile() : profile.copy();
        next.normalize();
        active = next;
        revision++;
    }

    public static AvatarProfile profile() { return active.copy(); }
    public static int revision() { return revision; }
}
