package io.github.ruok0214.comparatorinspector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import java.util.*;

public final class ComparatorInspector implements ModInitializer {
    private static final Map<UUID, Long> LAST_QUERY = new HashMap<>();
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("comparator_inspector", path); }
    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(InspectorPayloads.Query.TYPE, InspectorPayloads.Query.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InspectorPayloads.Snapshot.TYPE, InspectorPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(InspectorPayloads.Query.TYPE, (q, ctx) -> query(ctx.player(), q));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_QUERY.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_QUERY.clear());
    }
    private static void query(ServerPlayer player, InspectorPayloads.Query q) {
        var level = player.level();
        long now = level.getServer().getTickCount();
        if (now - LAST_QUERY.getOrDefault(player.getUUID(), -100L) < 5) return;
        LAST_QUERY.put(player.getUUID(), now);
        if (!q.dimension().equals(level.dimension().identifier()) || q.radius() < 1 || q.radius() > InspectorPayloads.MAX_RADIUS) return;
        var result = new ArrayList<InspectorPayloads.Reading>();
        for (var pos : new LinkedHashSet<>(q.positions())) {
            if (pos.distToCenterSqr(player.position()) > (double) q.radius() * q.radius()) continue;
            var reading = read(level, pos);
            if (reading != null) result.add(reading);
        }
        ServerPlayNetworking.send(player, new InspectorPayloads.Snapshot(q.dimension(), q.sequence(), List.copyOf(result)));
    }
    public static InspectorPayloads.Reading read(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return null;
        // Double chests can consult an adjacent block. Never load a chunk just to inspect it.
        for (var side : Direction.Plane.HORIZONTAL) if (!level.hasChunkAt(pos.relative(side))) return null;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ComparatorBlock) {
            if (!(level.getBlockEntity(pos) instanceof ComparatorBlockEntity comparator)) return null;
            // Use the circuit's current output, including vanilla update delay and side inputs.
            int output = state.getValue(ComparatorBlock.POWERED) ? Math.clamp(comparator.getOutputSignal(), 0, 15) : 0;
            return new InspectorPayloads.Reading(pos.immutable(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), output, output, output, output);
        }
        if (!state.hasAnalogOutputSignal()) return null;
        // Reading container slots can generate unopened structure loot. Leave it untouched.
        if (pendingLoot(level, pos)) return null;
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
            for (var side : Direction.Plane.HORIZONTAL) if (pendingLoot(level, pos.relative(side))) return null;
        return new InspectorPayloads.Reading(pos.immutable(), BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                value(level, pos, Direction.NORTH), value(level, pos, Direction.EAST),
                value(level, pos, Direction.SOUTH), value(level, pos, Direction.WEST));
    }
    private static int value(ServerLevel level, BlockPos pos, Direction side) {
        return Math.clamp(level.getBlockState(pos).getAnalogOutputSignal(level, pos, side), 0, 15);
    }
    private static boolean pendingLoot(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof net.minecraft.world.RandomizableContainer container && container.getLootTable() != null;
    }
}
