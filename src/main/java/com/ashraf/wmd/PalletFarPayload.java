package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PalletFarPayload(int id, double x, double y, double z, boolean chute, boolean heavy) implements CustomPacketPayload {

    public static final Type<PalletFarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pallet_far"));

    public static final StreamCodec<FriendlyByteBuf, PalletFarPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.id()); buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeByte((p.chute() ? 1 : 0) | (p.heavy() ? 2 : 0)); },
            buf -> { int id = buf.readVarInt(); double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble(); int f = buf.readByte();
                return new PalletFarPayload(id, x, y, z, (f & 1) != 0, (f & 2) != 0); });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
