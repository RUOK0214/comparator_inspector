package io.github.ruok0214.comparatorinspector;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class InspectorPayloads {
    public static final int MAX_BLOCKS = 512;
    public static final int MAX_RADIUS = 32;
    public record Reading(BlockPos pos, Identifier block, int north, int east, int south, int west) {
        public boolean uniform() { return north == east && east == south && south == west; }
    }
    public record Query(Identifier dimension, int sequence, int radius, List<BlockPos> positions) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ComparatorInspector.id("query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC = StreamCodec.of((b, v) -> {
            b.writeIdentifier(v.dimension); b.writeVarInt(v.sequence); b.writeVarInt(v.radius);
            b.writeVarInt(v.positions.size()); v.positions.forEach(b::writeBlockPos);
        }, b -> {
            var dimension = b.readIdentifier(); int sequence = b.readVarInt(), radius = b.readVarInt();
            int size = count(b); var positions = new ArrayList<BlockPos>(size);
            for (int i = 0; i < size; i++) positions.add(b.readBlockPos());
            return new Query(dimension, sequence, radius, List.copyOf(positions));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(Identifier dimension, int sequence, List<Reading> readings) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ComparatorInspector.id("snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b, v) -> {
            b.writeIdentifier(v.dimension); b.writeVarInt(v.sequence); b.writeVarInt(v.readings.size());
            for (var r : v.readings) {
                b.writeBlockPos(r.pos); b.writeIdentifier(r.block);
                b.writeByte(r.north); b.writeByte(r.east); b.writeByte(r.south); b.writeByte(r.west);
            }
        }, b -> {
            var dimension = b.readIdentifier(); int sequence = b.readVarInt(), size = count(b);
            var readings = new ArrayList<Reading>(size);
            for (int i = 0; i < size; i++) readings.add(new Reading(b.readBlockPos(), b.readIdentifier(),
                    signal(b), signal(b), signal(b), signal(b)));
            return new Snapshot(dimension, sequence, List.copyOf(readings));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static int count(RegistryFriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > MAX_BLOCKS) throw new IllegalArgumentException("Invalid block count");
        return size;
    }
    private static int signal(RegistryFriendlyByteBuf b) {
        int signal = b.readUnsignedByte();
        if (signal > 15) throw new IllegalArgumentException("Invalid signal");
        return signal;
    }
}
