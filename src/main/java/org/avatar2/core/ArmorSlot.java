package org.avatar2.core;

/** Stable authoring/render slots. They are deliberately independent of UI order. */
public enum ArmorSlot {
    HEAD("Head"),
    SHOULDERS("Shoulders"),
    TORSO("Torso"),
    ARMS("Arms"),
    HANDS("Hands"),
    LEGS("Legs"),
    FEET("Feet"),
    SHIELD("Shield");

    private final String displayName;

    ArmorSlot(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

