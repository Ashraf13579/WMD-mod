package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmHudPayload(BlockPos helm, float depth, int crush, float heading, float speed, int step, int reply, int engines, int running,
                             float rudderOrder, float rudderReal, float planesOrder, float planesReal, float noise, float ballast, int flags) implements CustomPacketPayload {

    public static final int F_POWER = 1, F_SUB = 2, F_CONTROL = 4, F_TELEGRAPH = 8, F_SHIP = 16,
            F_AIR = 32;

    public static final int F_ANCHOR = 64, F_DRAG = 128;

    public static final Type<HelmHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_hud"));

    public static final StreamCodec<FriendlyByteBuf, HelmHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.helm()); buf.writeFloat(p.depth()); buf.writeVarInt(p.crush() + 1); buf.writeFloat(p.heading()); buf.writeFloat(p.speed());
                buf.writeByte(p.step()); buf.writeByte(p.reply()); buf.writeByte(p.engines()); buf.writeByte(p.running());
                buf.writeFloat(p.rudderOrder()); buf.writeFloat(p.rudderReal()); buf.writeFloat(p.planesOrder()); buf.writeFloat(p.planesReal());
                buf.writeFloat(p.noise()); buf.writeFloat(p.ballast()); buf.writeByte(p.flags());
            },
            buf -> new HelmHudPayload(buf.readBlockPos(), buf.readFloat(), buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte(),
                    buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean has(int f) { return (flags & f) != 0; }

    public static void handle(HelmHudPayload p, IPayloadContext context) { context.enqueueWork(() -> ClientHelm.onHud(p)); }
}
