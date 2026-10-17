package org.avatar2.client;

import com.wurmonline.client.renderer.gui.AvatarStudioWindow;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.HudSettings;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Signature-checked access to Wurm's package-private HUD component lifecycle. */
final class HudBridge {
    private final Field hudSettings;
    private final Field savePosManager;
    private final Method addComponent;
    private final Method removeComponent;
    private final Method toggleComponent;
    private final Method hideComponent;
    private final Method showComponent;

    HudBridge() throws ReflectiveOperationException {
        hudSettings = HeadsUpDisplay.class.getDeclaredField("hudSettings");
        savePosManager = HeadsUpDisplay.class.getDeclaredField("savePosManager");
        addComponent = method("addComponent");
        removeComponent = method("removeComponent");
        toggleComponent = method("toggleComponent");
        hideComponent = method("hideComponent");
        showComponent = method("showComponent");
    }

    void register(HeadsUpDisplay hud, AvatarStudioWindow window, boolean visible)
            throws ReflectiveOperationException {
        ReflectionUtil.callPrivateMethod(hud, addComponent, window);
        HudSettings settings = ReflectionUtil.getPrivateField(hud, hudSettings);
        settings.registerComponent("Avatar Studio", window);
        settings.setAvailable(window, true);
        settings.setEnabled(window, visible);
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, savePosManager);
        positions.registerAndRefresh(window, "avatar2.studio");
        // SDK show also binds maximize/restore to this HUD, including hidden startup.
        window.show(hud);
        if (!visible) window.hide();
    }

    void add(HeadsUpDisplay hud, WurmComponent window) throws ReflectiveOperationException {
        ReflectionUtil.callPrivateMethod(hud, addComponent, window);
        ReflectionUtil.callPrivateMethod(hud, showComponent, window);
    }

    void remove(HeadsUpDisplay hud, WurmComponent window)
            throws ReflectiveOperationException {
        if (hud != null && window != null)
            ReflectionUtil.callPrivateMethod(hud, removeComponent, window);
    }

    void hide(HeadsUpDisplay hud, AvatarStudioWindow window)
            throws ReflectiveOperationException {
        if (hud != null && window != null)
            ReflectionUtil.callPrivateMethod(hud, hideComponent, window);
    }

    void setVisible(HeadsUpDisplay hud, AvatarStudioWindow window, boolean visible)
            throws ReflectiveOperationException {
        if (hud == null || window == null) return;
        ReflectionUtil.callPrivateMethod(hud, visible ? showComponent : hideComponent, window);
        HudSettings settings = ReflectionUtil.getPrivateField(hud, hudSettings);
        settings.setEnabled(window, visible);
        if (visible) window.show(hud); else window.hide();
    }

    void toggle(HeadsUpDisplay hud, AvatarStudioWindow window)
            throws ReflectiveOperationException {
        if (hud != null && window != null)
            ReflectionUtil.callPrivateMethod(hud, toggleComponent, window);
    }

    private static Method method(String name) throws ReflectiveOperationException {
        return ReflectionUtil.getMethod(HeadsUpDisplay.class, name,
                new Class[]{WurmComponent.class});
    }
}
