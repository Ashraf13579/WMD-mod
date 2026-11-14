package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AamBurstPayload(long id, double x, double y, double z, boolean kill) implements CustomPacketPayload {

    public static final Type<AamBurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_burst"));

    public static final StreamCodec<FriendlyByteBuf, AamBurstPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.id()); buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeBoolean(p.kill()); },
            buf -> new AamBurstPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
