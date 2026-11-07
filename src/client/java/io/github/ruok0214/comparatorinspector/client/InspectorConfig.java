package io.github.ruok0214.comparatorinspector.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.nio.file.*;

public record InspectorConfig(boolean targeted, boolean nearby, int radius, boolean comparators, DisplayStyle world, DisplayStyle comparatorStyle, DisplayStyle hud) {
    public static final InspectorConfig DEFAULT = new InspectorConfig(true, false, 8);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("comparator-inspector.json");
    public InspectorConfig {
        radius = Math.clamp(radius, 1, 32);
        if (world == null) world = DisplayStyle.WORLD;
        if (comparatorStyle == null) comparatorStyle = world;
        if (hud == null) hud = DisplayStyle.HUD;
    }
    public InspectorConfig(boolean targeted, boolean nearby, int radius) { this(targeted,nearby,radius,false,DisplayStyle.WORLD,DisplayStyle.COMPARATOR,DisplayStyle.HUD); }
    public InspectorConfig detection(boolean targeted, boolean nearby, int radius) { return new InspectorConfig(targeted,nearby,radius,comparators,world,comparatorStyle,hud); }
    public InspectorConfig comparatorOutputs(boolean enabled) { return new InspectorConfig(targeted,nearby,radius,enabled,world,comparatorStyle,hud); }
    public DisplayStyle style(DisplayTarget target) {
        return switch (target) { case WORLD -> world; case COMPARATOR -> comparatorStyle; case HUD -> hud; };
    }
    public InspectorConfig appearance(DisplayTarget target, DisplayStyle style) {
        return new InspectorConfig(targeted,nearby,radius,comparators,
                target == DisplayTarget.WORLD ? style : world,
                target == DisplayTarget.COMPARATOR ? style : comparatorStyle,
                target == DisplayTarget.HUD ? style : hud);
    }
    public static InspectorConfig fromJson(String text) {
        var json = JsonParser.parseString(text).getAsJsonObject();
        var world = style(json,"world",DisplayStyle.WORLD);
        return new InspectorConfig(json.has("targeted") ? json.get("targeted").getAsBoolean() : true,
                json.has("nearby") && json.get("nearby").getAsBoolean(), json.has("radius") ? json.get("radius").getAsInt() : 8,
                json.has("comparators") && json.get("comparators").getAsBoolean(),
                world, style(json,"comparatorStyle",world), style(json,"hud",DisplayStyle.HUD));
    }
    private static DisplayStyle style(JsonObject json, String key, DisplayStyle fallback) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return fallback;
        var s = json.getAsJsonObject(key);
        return new DisplayStyle(s.has("enabled") ? s.get("enabled").getAsBoolean() : fallback.enabled(),
                number(s,"color",fallback.color()),number(s,"secondaryColor",fallback.secondaryColor()),
                number(s,"opacity",fallback.opacity()),number(s,"scalePercent",fallback.scalePercent()),
                number(s,"heightHundredths",fallback.heightHundredths()));
    }
    private static int number(JsonObject json,String key,int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    public static InspectorConfig load() {
        if (!Files.exists(FILE)) return DEFAULT;
        try {
            return fromJson(Files.readString(FILE));
        } catch (Exception e) {
            LoggerFactory.getLogger("comparator_inspector").warn("Could not load settings; using defaults", e);
            return DEFAULT;
        }
    }
    public boolean save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this));
            try { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception e) {
            LoggerFactory.getLogger("comparator_inspector").warn("Could not save settings", e);
            return false;
        }
    }
}
