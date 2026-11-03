package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CcCamPadPayload(double x, double y, double z, float yaw, int targetX, int targetZ, long pad) implements CustomPacketPayload {

    public static final Type<CcCamPadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_cam_pad"));

    public static final StreamCodec<FriendlyByteBuf, CcCamPadPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeFloat(p.yaw()); buf.writeInt(p.targetX()); buf.writeInt(p.targetZ()); buf.writeLong(p.pad()); },
            buf -> new CcCamPadPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
