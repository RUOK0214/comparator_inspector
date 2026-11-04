package io.github.ruok0214.comparatorinspector.client;

import io.github.ruok0214.comparatorinspector.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

public final class InspectorClient implements ClientModInitializer {
    private static InspectorConfig config;
    private static Map<BlockPos, InspectorPayloads.Reading> readings = Map.of();
    private static List<BlockPos> candidates = List.of();
    private static volatile List<Label> labels = List.of();
    private static Identifier dimension;
    private static int ticks, sequence, lastResponseTick = -100, lastSequence = -1;
    private static boolean openMenu, truncated;
    public record Label(Vec3 position, String text, int color, float scale) {}
    public record HudDisplay(Component text, int color, int background, float scale) {}
    public static InspectorConfig config() { return config; }
    public static Map<BlockPos, InspectorPayloads.Reading> readings() { return readings; }
    public static Component tr(String key, Object... args) { return Component.translatable("comparator_inspector." + key, args); }
    public static boolean available() { return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(InspectorPayloads.Query.TYPE); }
    public static void configure(InspectorConfig next) {
        config = next; candidates = List.of(); readings = Map.of(); labels = List.of();
        lastSequence = ++sequence; lastResponseTick = -100; ticks = 0;
    }
    private static void clear() { readings = Map.of(); candidates = List.of(); labels = List.of(); dimension = null; truncated = false; lastResponseTick = -100; lastSequence = -1; }
    @Override public void onInitializeClient() {
        config = InspectorConfig.load();
        ClientPlayNetworking.registerGlobalReceiver(InspectorPayloads.Snapshot.TYPE, (p, ctx) -> {
            if (ctx.client().level == null || !p.dimension().equals(ctx.client().level.dimension().identifier()) || p.sequence() < lastSequence) return;
            var map = new HashMap<BlockPos, InspectorPayloads.Reading>();
            p.readings().forEach(r -> map.put(r.pos(), r));
            readings = Map.copyOf(map); lastSequence = p.sequence(); lastResponseTick = ticks;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { clear(); openMenu = false; }));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("ci").executes(c -> { openMenu = true; return 1; }));
            dispatcher.register(ClientCommands.literal("comparatorinspector").executes(c -> { openMenu = true; return 1; }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(InspectorClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ComparatorInspector.id("reading"), (g, dt) -> hud(g));
        LevelExtractionEvents.END_EXTRACTION.register(context -> labels = extractLabels());
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (var label : labels) Gizmos.billboardText(label.text, label.position,
                        TextGizmo.Style.forColorAndCentered(label.color).withScale(label.scale));
            }
        });
    }
    private static void tick(Minecraft mc) {
        if (openMenu) { openMenu = false; mc.gui.setScreen(new InspectorScreen(null)); }
        if (mc.level == null || mc.player == null) { clear(); return; }
        var current = mc.level.dimension().identifier();
        if (!current.equals(dimension)) { clear(); dimension = current; ticks = 0; }
        if (!available() || (!config.targeted() && !config.nearby()) || (!config.world().visible() && !config.hud().visible())) { readings = Map.of(); labels = List.of(); return; }
        if (ticks++ % 20 == 0) scan(mc);
        if (ticks % 5 != 0) return;
        var positions = new LinkedHashSet<BlockPos>();
        var target = target(mc);
        if (config.targeted() && target != null) positions.add(target);
        if (config.nearby() && config.world().visible()) for (var pos : candidates) {
            if (positions.size() >= InspectorPayloads.MAX_BLOCKS) break;
            if (pos.distToCenterSqr(mc.player.position()) <= (double) config.radius() * config.radius()) positions.add(pos);
        }
        if (positions.isEmpty()) { readings = Map.of(); return; }
        ClientPlayNetworking.send(new InspectorPayloads.Query(current, ++sequence,
                Math.max(config.radius(), target == null ? 1 : (int) Math.ceil(Math.sqrt(target.distToCenterSqr(mc.player.position())))), List.copyOf(positions)));
    }
    public static BlockPos target(Minecraft mc) {
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        var pos = hit.getBlockPos();
        return pos.distToCenterSqr(mc.player.position()) <= 32 * 32 && mc.level.getBlockState(pos).hasAnalogOutputSignal() ? pos : null;
    }
    private static void scan(Minecraft mc) {
        truncated = false;
        if (!config.nearby() || !config.world().visible()) { candidates = List.of(); return; }
        var center = mc.player.position(); int r = config.radius(); var origin = mc.player.blockPosition();
        Comparator<BlockPos> nearest = Comparator.comparingDouble(p -> p.distToCenterSqr(center));
        var heap = new PriorityQueue<BlockPos>(InspectorPayloads.MAX_BLOCKS, nearest.reversed());
        int minY = Math.max(mc.level.getMinY(), origin.getY() - r), maxY = Math.min(mc.level.getMaxY(), origin.getY() + r);
        for (int cx = (origin.getX() - r) >> 4; cx <= (origin.getX() + r) >> 4; cx++) {
            for (int cz = (origin.getZ() - r) >> 4; cz <= (origin.getZ() + r) >> 4; cz++) {
                var chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    var section = chunk.getSection(mc.level.getSectionIndexFromSectionY(sy));
                    if (!section.maybeHas(BlockState::hasAnalogOutputSignal)) continue;
                    for (int y = Math.max(minY, sy * 16); y <= Math.min(maxY, sy * 16 + 15); y++)
                        for (int x = Math.max(origin.getX()-r, cx*16); x <= Math.min(origin.getX()+r, cx*16+15); x++)
                            for (int z = Math.max(origin.getZ()-r, cz*16); z <= Math.min(origin.getZ()+r, cz*16+15); z++) {
                                if (!section.getBlockState(x & 15, y & 15, z & 15).hasAnalogOutputSignal()) continue;
                                var pos = new BlockPos(x, y, z);
                                if (pos.distToCenterSqr(center) > (double) r*r) continue;
                                if (heap.size() < InspectorPayloads.MAX_BLOCKS) heap.add(pos);
                                else { truncated = true; if (nearest.compare(pos, heap.peek()) < 0) { heap.poll(); heap.add(pos); } }
                            }
                }
            }
        }
        candidates = heap.stream().sorted(nearest).toList();
    }
    public static InspectorPayloads.Reading validReading(BlockPos pos) {
        var mc = Minecraft.getInstance();
        if (pos == null || mc.level == null || ticks - lastResponseTick > 40) return null;
        var r = readings.get(pos);
        return r != null && mc.level.hasChunkAt(pos) && r.block().equals(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock())) ? r : null;
    }
    public static Component signalText(InspectorPayloads.Reading r) {
        return r.uniform() ? tr("signal", r.north()) : tr("directional", r.north(), r.east(), r.south(), r.west());
    }
    public static List<Label> extractLabels() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || !available() || !config.world().visible()) return List.of();
        var target = config.targeted() ? target(mc) : null;
        var result = new ArrayList<Label>();
        for (var r : readings.values()) {
            boolean aimed = r.pos().equals(target);
            if ((!aimed && (!config.nearby() || r.pos().distToCenterSqr(mc.player.position()) > (double) config.radius()*config.radius())) || validReading(r.pos()) == null) continue;
            String text = r.uniform() ? r.north() + " / 15" : signalText(r).getString();
            result.add(new Label(Vec3.atLowerCornerWithOffset(r.pos(), .5, 1 + config.world().height(), .5), text, config.world().argb(aimed), .32f * config.world().scale()));
        }
        return List.copyOf(result);
    }
    public static HudDisplay hudDisplay() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null || !config.hud().visible()) return null;
        var target = config.targeted() ? target(mc) : null;
        Component text = null;
        if (!available() && (target != null || config.nearby())) text = tr("server_required");
        else if (target != null) {
            var r = validReading(target);
            text = mc.level.getBlockState(target).getBlock().getName().copy().append(" · ")
                    .append(r == null ? tr("waiting") : signalText(r));
        } else if (config.nearby() && truncated) text = tr("limit", InspectorPayloads.MAX_BLOCKS);
        return text == null ? null : new HudDisplay(text,config.hud().argb(false),config.hud().backgroundArgb(),config.hud().scale());
    }
    private static void hud(GuiGraphicsExtractor g) {
        var display=hudDisplay(); if(display==null) return;
        var mc=Minecraft.getInstance();
        int w=mc.font.width(display.text());
        float scale=Math.min(display.scale(),(mc.getWindow().getGuiScaledWidth()-16f)/(w+12));
        g.pose().pushMatrix();
        g.pose().translate(mc.getWindow().getGuiScaledWidth()/2f,mc.getWindow().getGuiScaledHeight()/2f+24);
        g.pose().scale(scale,scale);
        g.fill(-w/2-6,-4,(w+1)/2+6,13,display.background());
        g.text(mc.font,display.text(),-w/2,0,display.color(),true);
        g.pose().popMatrix();
    }
}
