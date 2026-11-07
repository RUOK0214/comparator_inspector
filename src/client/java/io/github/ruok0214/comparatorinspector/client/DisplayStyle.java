package io.github.ruok0214.comparatorinspector.client;

/** Independent appearance for one display surface. Colors are RGB, opacity is percent. */
public record DisplayStyle(boolean enabled, int color, int secondaryColor, int opacity, int scalePercent, int heightHundredths) {
    public static final int DEFAULT_HEIGHT = 18;
    public static final int MAX_HEIGHT = 300;
    public static final DisplayStyle WORLD = new DisplayStyle(true, 0xFFFFFF, 0xFFD166, 100, 100);
    public static final DisplayStyle COMPARATOR = new DisplayStyle(true, 0xFFFFFF, 0xFFD166, 100, 100);
    public static final DisplayStyle HUD = new DisplayStyle(true, 0xFFD166, 0x141B24, 100, 100);
    public DisplayStyle {
        color &= 0xFFFFFF; secondaryColor &= 0xFFFFFF;
        opacity = Math.clamp(opacity, 0, 100); scalePercent = Math.clamp(scalePercent, 50, 200);
        heightHundredths = Math.clamp(heightHundredths, 0, MAX_HEIGHT);
    }
    public DisplayStyle(boolean enabled, int color, int secondaryColor, int opacity, int scalePercent) {
        this(enabled,color,secondaryColor,opacity,scalePercent,DEFAULT_HEIGHT);
    }
    /** Offset above the top of the block's grid cell, in blocks. Only used by world labels. */
    public double height() { return heightHundredths / 100.0; }
    public int argb(boolean secondary) { return (Math.round(opacity * 2.55f) << 24) | (secondary ? secondaryColor : color); }
    public int backgroundArgb() { return (Math.round(opacity * 2.04f) << 24) | secondaryColor; }
    public float scale() { return scalePercent / 100f; }
    public boolean visible() { return enabled && opacity > 0; }
    public DisplayStyle enabled(boolean value) { return new DisplayStyle(value,color,secondaryColor,opacity,scalePercent,heightHundredths); }
    public DisplayStyle colors(int primary, int secondary) { return new DisplayStyle(enabled,primary,secondary,opacity,scalePercent,heightHundredths); }
    public DisplayStyle opacity(int value) { return new DisplayStyle(enabled,color,secondaryColor,value,scalePercent,heightHundredths); }
    public DisplayStyle size(int value) { return new DisplayStyle(enabled,color,secondaryColor,opacity,value,heightHundredths); }
    public DisplayStyle height(int value) { return new DisplayStyle(enabled,color,secondaryColor,opacity,scalePercent,value); }
}
