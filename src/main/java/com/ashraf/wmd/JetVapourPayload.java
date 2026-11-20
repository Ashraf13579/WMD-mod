package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JetVapourPayload(UUID ship, int kind, float dx, float dy, float dz) implements CustomPacketPayload {

    public static final int BARRIER = 0, PULL = 1;

    public static final Type<JetVapourPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_vapour"));

    public static final StreamCodec<FriendlyByteBuf, JetVapourPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.ship()); buf.writeByte(p.kind()); buf.writeFloat(p.dx()); buf.writeFloat(p.dy()); buf.writeFloat(p.dz()); },
            buf -> new JetVapourPayload(buf.readUUID(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
