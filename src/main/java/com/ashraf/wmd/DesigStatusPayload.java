package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DesigStatusPayload(int satId, int x, int z, int state, long leftMs, long nextMs, boolean onShip) implements CustomPacketPayload {

    public static final Type<DesigStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "desig_status"));

    public static final StreamCodec<FriendlyByteBuf, DesigStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.satId()); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeVarInt(p.state()); buf.writeLong(p.leftMs()); buf.writeLong(p.nextMs()); buf.writeBoolean(p.onShip()); },
            buf -> new DesigStatusPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static volatile DesigStatusPayload last = new DesigStatusPayload(-1, 0, 0, SatDesignator.NO_SAT, 0L, 0L, false);
    private static volatile long receivedMs;

    public static DesigStatusPayload last() { return last; }
    public static long receivedMs() { return receivedMs; }

    public static void onReceive(DesigStatusPayload p) { last = p; receivedMs = System.currentTimeMillis(); }

    public static void clear() { last = new DesigStatusPayload(-1, 0, 0, SatDesignator.NO_SAT, 0L, 0L, false); }
}
