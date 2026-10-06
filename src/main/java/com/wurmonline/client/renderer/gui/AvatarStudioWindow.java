package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.avatar2.client.Avatar2Mod;
import org.avatar2.client.AvatarStudioController;
import org.avatar2.core.ArmorSlot;
import org.avatar2.core.AvatarProfile;
import org.avatar2.core.MaterialQualitySurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native Wurm panel for the body-only 0.1 vertical slice. */
public final class AvatarStudioWindow extends WWindow {
    public AvatarStudioWindow(AvatarStudioController controller) {
        super("avatar2.studio", true);
        setTitle("Avatar Studio 2.0");
        resizable = false;
        setComponent(new AvatarStudioPanel(controller));
        setSize(620, 400);
    }

    @Override
    protected void closePressed() {
        Avatar2Mod.hideStudioWindow();
    }
}

final class AvatarStudioPanel extends FlexComponent {
    private enum Page { BODY, INFO }

    private static final int BUTTON_HEIGHT = 24;
    private static final int SLIDER_HEIGHT = 34;
    private final AvatarStudioController controller;
    private final List<Hit> hits = new ArrayList<Hit>();
    private Page page = Page.BODY;
    private Hit activeSlider;

    AvatarStudioPanel(AvatarStudioController controller) {
        super("Avatar Studio controls", 0, 0, 600, 370);
        this.controller = controller;
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        hits.clear();
        fillRect(queue, 0.050f, 0.047f, 0.041f, 0.98f,
                x, y, width, height);
        renderTabs(queue);
        if (page == Page.BODY) renderBody(queue);
        else renderInfo(queue);
        renderFooter(queue);
    }

    private void renderTabs(Queue queue) {
        int tabWidth = (width - 21) / 2;
        int bx = x + 7;
        for (Page value : Page.values()) {
            button(queue, bx, y + 7, tabWidth, value.name(),
                    "page:" + value.name(), page == value);
            bx += tabWidth + 5;
        }
        fillRect(queue, 0.47f, 0.38f, 0.21f, 0.75f,
                x + 8, y + 38, width - 16, 1);
    }

    private void renderBody(Queue queue) {
        AvatarProfile p = controller.profile();
        int left = x + 12;
        int top = y + 52;
        paint(queue, "Body-only preview: muscular CC0 mesh on a Wurm-compatible 91-joint rig",
                left, top, 0.87f, 0.84f, 0.73f, 1f);
        button(queue, left, top + 13, 174,
                (p.enabled ? "[ON]  Avatar 2.0" : "[OFF] Vanilla body"),
                "enabled", p.enabled);
        int bw = 126;
        button(queue, left, top + 48, bw, "Sex: " + p.sex,
                "sex", false);
        button(queue, left + bw + 6, top + 48, bw, "Athletic",
                "athletic", p.preset == AvatarProfile.Preset.ATHLETIC);
        button(queue, left + (bw + 6) * 2, top + 48, bw, "Bodybuilder",
                "bodybuilder", p.preset == AvatarProfile.Preset.BODYBUILDER);
        paint(queue, "Runtime model: " + controller.activeModel(), left, top + 104,
                0.90f, 0.79f, 0.46f, 1f);
        paint(queue, "Press Apply for a live rebuild; Save also makes the choice persistent.",
                left, top + 131, 0.72f, 0.75f, 0.68f, 1f);
        paint(queue, "For the 0.1 test, equipped armour is intentionally not rendered.",
                left, top + 153, 0.90f, 0.67f, 0.40f, 1f);
    }

    private void renderInfo(Queue queue) {
        int left = x + 12;
        int top = y + 55;
        paint(queue, "What this build proves", left, top,
                0.87f, 0.84f, 0.73f, 1f);
        paint(queue, "- Wurm loads the generated Collada body directly (no WOM compiler).",
                left, top + 30, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "- All 91 vanilla joints and attachment names are present.",
                left, top + 52, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "- Human idle, walk and run replace the stiff vanilla locomotion.",
                left, top + 74, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "- Combat/work actions fall back to vanilla; paperdoll uses this body.",
                left, top + 96, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "Fine morph sliders, armour fitting and collision guards follow after visual approval.",
                left, top + 134, 0.90f, 0.67f, 0.40f, 1f);
    }

    private void renderFit(Queue queue) {
        AvatarProfile p = controller.profile();
        int left = x + 12;
        int top = y + 55;
        paint(queue, "Outfit Composer: layer masks for static overlap, proxies/IK for moving contact",
                left, top, 0.87f, 0.84f, 0.73f, 1f);
        int buttonWidth = width - 24;
        top += 18;
        top = toggle(queue, left, top, buttonWidth,
                "Automatic male/female armour fitting", "autoFitArmor", p.autoFitArmor);
        top = toggle(queue, left, top, buttonWidth,
                "Hide lower triangles covered by upper armour", "hideOccludedGeometry",
                p.hideOccludedGeometry);
        top = toggle(queue, left, top, buttonWidth,
                "Pose collision guard", "poseCollisionGuard", p.poseCollisionGuard);
        top = toggle(queue, left, top, buttonWidth,
                "Beard against chest/neck corrective", "beardChestGuard", p.beardChestGuard);
        top = toggle(queue, left, top, buttonWidth,
                "Shield grip anchor + arm IK guard", "shieldHandGuard", p.shieldHandGuard);
        top = toggle(queue, left, top, buttonWidth,
                "Use assembled Avatar 2.0 in paperdoll", "paperDollUsesAvatar",
                p.paperDollUsesAvatar);
        paint(queue, "Priority: pauldron > breastplate > sleeve > shirt > body.",
                left, top + 14, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "This directly covers the sleeve spike and chest-under-pauldron artifacts.",
                left, top + 33, 0.78f, 0.79f, 0.70f, 1f);
    }

    private void renderQuality(Queue queue) {
        AvatarProfile p = controller.profile();
        int left = x + 12;
        int top = y + 53;
        paint(queue, "Per-element material quality preview", left, top,
                0.87f, 0.84f, 0.73f, 1f);
        int slotWidth = (width - 29) / 4;
        int bx = left;
        int by = top + 12;
        ArmorSlot[] slots = ArmorSlot.values();
        for (int index = 0; index < slots.length; index++) {
            ArmorSlot slot = slots[index];
            button(queue, bx, by, slotWidth, slot.displayName(),
                    "slot:" + slot.name(), controller.selectedSlot() == slot);
            bx += slotWidth + 3;
            if (index == 3) {
                bx = left;
                by += BUTTON_HEIGHT + 3;
            }
        }
        by += BUTTON_HEIGHT + 12;
        button(queue, left, by, 190,
                "Material: " + controller.previewMaterial(), "material", false);
        by += BUTTON_HEIGHT + 8;
        renderSlider(queue, left, by, width - 24,
                spec("Preview QL", "previewQuality", 0f, 100f));
        by += SLIDER_HEIGHT;
        renderSlider(queue, left, by, width - 24,
                spec("Element reflection", "slotReflection", 0f, 1f));
        by += SLIDER_HEIGHT + 8;

        MaterialQualitySurface.Sample sample = controller.qualitySample();
        paint(queue, String.format(Locale.ROOT,
                        "Low-QL warp %.3f  roughness noise %.3f  tint variation %.3f",
                        sample.normalWarp, sample.roughnessNoise, sample.tintVariation),
                left, by + 10, 0.80f, 0.80f, 0.72f, 1f);
        paint(queue, String.format(Locale.ROOT,
                        "Polish %.3f  reflection %.2f%%  emission %.3f",
                        sample.polish, sample.reflection * 100f, sample.emission),
                left, by + 30, 0.90f, 0.79f, 0.46f, 1f);
        paint(queue, "Reflection: QL 50..95 quadratic; Gold/Silver max 30%, other metals 16%.",
                left, by + 54, 0.72f, 0.75f, 0.68f, 1f);
        paint(queue, "Emission begins above QL 90 and remains deliberately subtle.",
                left, by + 73, 0.72f, 0.75f, 0.68f, 1f);
    }

    private void renderDebug(Queue queue) {
        AvatarProfile p = controller.profile();
        int left = x + 12;
        int top = y + 55;
        paint(queue, "Validation and authoring overlays", left, top,
                0.87f, 0.84f, 0.73f, 1f);
        top = toggle(queue, left, top + 18, width - 24,
                "Show coverage regions and hidden triangles", "debugCoverage",
                p.debugCoverage);
        paint(queue, "Runtime body: MPFB muscular base on Wurm's 91-joint driver rig",
                left, top + 14, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "Required anchors: helmet, backpack, shield, hands, shoulders, quiver",
                left, top + 35, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "Scale gates: height 1.5%, shoulders 2.5%, pelvis 2.0%",
                left, top + 56, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "Paperdoll shares body, outfit assembly, material and correction data.",
                left, top + 77, 0.78f, 0.79f, 0.70f, 1f);
        paint(queue, "Remote item QL requires the optional server appearance extension.",
                left, top + 111, 0.90f, 0.67f, 0.40f, 1f);
    }

    private void renderFooter(Queue queue) {
        int lineY = y + height - 64;
        fillRect(queue, 0.47f, 0.38f, 0.21f, 0.70f,
                x + 8, lineY, width - 16, 1);
        int by = lineY + 8;
        int bw = 92;
        button(queue, x + 10, by, bw, "Apply", "apply", false);
        button(queue, x + 108, by, bw, "Save", "save", false);
        button(queue, x + 206, by, bw, "Revert", "revert", false);
        button(queue, x + 304, by, bw, "Reset", "reset", false);
        paint(queue, (controller.dirty() ? "* " : "") + controller.status(),
                x + 10, y + height - 8, 0.82f, 0.83f, 0.74f, 1f);
    }

    private int toggle(Queue queue, int bx, int by, int bw,
                       String label, String name, boolean enabled) {
        button(queue, bx, by, bw, (enabled ? "[ON]  " : "[OFF] ") + label,
                "toggle:" + name, enabled);
        return by + BUTTON_HEIGHT + 7;
    }

    private void renderSlider(Queue queue, int sx, int sy, int sw,
                              SliderSpec spec) {
        float value = controller.value(spec.control);
        int labelWidth = 150;
        int valueWidth = 58;
        int barX = sx + labelWidth;
        int barWidth = Math.max(50, sw - labelWidth - valueWidth);
        int barY = sy + 14;
        paint(queue, spec.label, sx, sy + 19,
                0.86f, 0.85f, 0.80f, 1f);
        fillRect(queue, 0.022f, 0.024f, 0.026f, 1f,
                barX, barY, barWidth, 7);
        float fraction = clamp((value - spec.minimum) / (spec.maximum - spec.minimum));
        fillRect(queue, 0.52f, 0.43f, 0.24f, 1f,
                barX, barY, Math.max(1, Math.round(barWidth * fraction)), 7);
        int knob = barX + Math.round(barWidth * fraction);
        fillRect(queue, 0.94f, 0.83f, 0.47f, 1f,
                knob - 3, barY - 3, 7, 13);
        paint(queue, number(value), barX + barWidth + 7, sy + 19,
                0.94f, 0.90f, 0.74f, 1f);
        hits.add(Hit.slider(spec.control, barX, sy, barWidth,
                SLIDER_HEIGHT, spec.minimum, spec.maximum));
    }

    private void button(Queue queue, int bx, int by, int bw, String label,
                        String action, boolean active) {
        fillRect(queue, active ? 0.29f : 0.13f,
                active ? 0.24f : 0.12f,
                active ? 0.12f : 0.105f, 0.98f,
                bx, by, bw, BUTTON_HEIGHT);
        fillRect(queue, active ? 0.84f : 0.42f,
                active ? 0.69f : 0.37f,
                active ? 0.28f : 0.27f, 0.95f,
                bx, by + BUTTON_HEIGHT - 1, bw, 1);
        int textWidth = text == null ? 0 : text.getWidth(label);
        paint(queue, label, bx + Math.max(3, (bw - textWidth) / 2), by + 17,
                0.92f, 0.90f, 0.82f, 1f);
        hits.add(Hit.button(action, bx, by, bw, BUTTON_HEIGHT));
    }

    @Override
    protected void leftPressed(int mouseX, int mouseY, int buttons) {
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            if (hit.slider) {
                activeSlider = hit;
                updateSlider(hit, mouseX);
            } else perform(hit.action);
            return;
        }
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY) {
        if (activeSlider != null) updateSlider(activeSlider, mouseX);
    }

    @Override
    protected void leftReleased(int mouseX, int mouseY) {
        activeSlider = null;
    }

    private void updateSlider(Hit hit, int mouseX) {
        float fraction = clamp((mouseX - hit.x) / (float) hit.width);
        controller.setValue(hit.action,
                hit.minimum + (hit.maximum - hit.minimum) * fraction);
    }

    private void perform(String action) {
        if (action.startsWith("page:")) {
            page = Page.valueOf(action.substring(5));
        } else controller.perform(action);
    }

    private static SliderSpec spec(String label, String control,
                                   float minimum, float maximum) {
        return new SliderSpec(label, control, minimum, maximum);
    }

    private static String number(float value) {
        if (Math.abs(value) >= 10f) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void paint(Queue queue, String value, int px, int baseline,
                       float red, float green, float blue, float alpha) {
        if (text == null || value == null || value.isEmpty()) return;
        text.moveTo(px, baseline);
        text.paint(queue, value, red, green, blue, alpha);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class SliderSpec {
        final String label;
        final String control;
        final float minimum;
        final float maximum;
        SliderSpec(String label, String control, float minimum, float maximum) {
            this.label = label;
            this.control = control;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static final class Hit {
        final String action;
        final int x;
        final int y;
        final int width;
        final int height;
        final boolean slider;
        final float minimum;
        final float maximum;

        private Hit(String action, int x, int y, int width, int height,
                    boolean slider, float minimum, float maximum) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.slider = slider;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        static Hit button(String action, int x, int y, int width, int height) {
            return new Hit(action, x, y, width, height, false, 0f, 0f);
        }

        static Hit slider(String action, int x, int y, int width, int height,
                          float minimum, float maximum) {
            return new Hit(action, x, y, width, height, true, minimum, maximum);
        }

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
}
