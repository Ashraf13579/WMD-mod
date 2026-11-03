package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CiwsFirePayload(net.minecraft.core.BlockPos pos, double fx, double fy, double fz, double tx, double ty, double tz, boolean hit) implements CustomPacketPayload {

    public static final Type<CiwsFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ciws_fire"));

    public static final StreamCodec<FriendlyByteBuf, CiwsFirePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeDouble(p.fx); buf.writeDouble(p.fy); buf.writeDouble(p.fz); buf.writeDouble(p.tx); buf.writeDouble(p.ty); buf.writeDouble(p.tz); buf.writeBoolean(p.hit); },
            buf -> new CiwsFirePayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
