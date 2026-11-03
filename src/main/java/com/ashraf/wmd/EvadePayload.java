package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EvadePayload(long flight, long arrive, double x, double y, double z) implements CustomPacketPayload {

    public static final Type<EvadePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "evade"));

    public static final StreamCodec<FriendlyByteBuf, EvadePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarLong(p.flight); buf.writeVarLong(p.arrive); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); },
            buf -> new EvadePayload(buf.readVarLong(), buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
