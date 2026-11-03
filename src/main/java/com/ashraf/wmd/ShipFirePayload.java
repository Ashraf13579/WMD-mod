package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShipFirePayload(UUID id, double x, double y, double z, float hx, float hy, float hz, float vx, float vy, float vz, float heat) implements CustomPacketPayload {

    public static final Type<ShipFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ship_fire"));

    public static final StreamCodec<FriendlyByteBuf, ShipFirePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.id()); b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeFloat(p.hx()); b.writeFloat(p.hy()); b.writeFloat(p.hz());
                        b.writeFloat(p.vx()); b.writeFloat(p.vy()); b.writeFloat(p.vz()); b.writeFloat(p.heat()); },
            b -> new ShipFirePayload(b.readUUID(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
