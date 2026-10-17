package org.avatar2.client;

import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.math.Quaternion;

/** Render-level posture; skeletal motion, heading and mounted transforms stay native. */
public final class AvatarRenderPosture {
    private static final Quaternion UPRIGHT = new Quaternion().fromAngles(0,0,0);

    private AvatarRenderPosture() { }

    public static float pitch(PlayerBodyRenderable body, float nativePitch) {
        return Avatar2Mod.usesAvatar(body) ? 0f : nativePitch;
    }

    public static Quaternion viewRotation(PlayerBodyRenderable body, Quaternion nativeRotation) {
        return Avatar2Mod.usesAvatar(body) ? UPRIGHT : nativeRotation;
    }
}
