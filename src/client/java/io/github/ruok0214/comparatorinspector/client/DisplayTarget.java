package io.github.ruok0214.comparatorinspector.client;

/** The independently configurable display surfaces. */
public enum DisplayTarget {
    WORLD("world.title", DisplayStyle.WORLD, true),
    COMPARATOR("comparator.title", DisplayStyle.COMPARATOR, true),
    HUD("hud.title", DisplayStyle.HUD, false);

    private final String titleKey;
    private final DisplayStyle defaults;
    private final boolean inWorld;
    DisplayTarget(String titleKey, DisplayStyle defaults, boolean inWorld) {
        this.titleKey=titleKey; this.defaults=defaults; this.inWorld=inWorld;
    }
    public String titleKey() { return titleKey; }
    public DisplayStyle defaults() { return defaults; }
    public boolean inWorld() { return inWorld; }
    public int minHeight() { return this == COMPARATOR ? DisplayStyle.MIN_HEIGHT : 0; }
    public DisplayStyle normalize(DisplayStyle style) {
        return style.height(Math.clamp(style.heightHundredths(),minHeight(),DisplayStyle.MAX_HEIGHT));
    }
}
