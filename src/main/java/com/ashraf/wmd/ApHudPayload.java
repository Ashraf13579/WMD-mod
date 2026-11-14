package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ApHudPayload(BlockPos cockpit, int mode, int phase, int reason, int leg, int legs, int flags, float tx, float ty, float tz,
                           float alt, float spd, float hdg, float dist, float radius, float throttle) implements CustomPacketPayload {

    public static final int F_FLOOR = 1, F_HOME = 2, F_RIGHT = 4;

    public boolean has(int f) { return (flags & f) != 0; }
    public boolean target() { return !Float.isNaN(tx); }

    public static final Type<ApHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ap_hud"));

    public static final StreamCodec<FriendlyByteBuf, ApHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.cockpit()); buf.writeByte(p.mode()); buf.writeByte(p.phase()); buf.writeByte(p.reason()); buf.writeByte(p.leg()); buf.writeByte(p.legs());
                buf.writeByte(p.flags()); buf.writeFloat(p.tx()); buf.writeFloat(p.ty()); buf.writeFloat(p.tz()); buf.writeFloat(p.alt()); buf.writeFloat(p.spd());
                buf.writeFloat(p.hdg()); buf.writeFloat(p.dist()); buf.writeFloat(p.radius()); buf.writeFloat(p.throttle());
            },
            buf -> new ApHudPayload(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readByte(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ApHudPayload p, IPayloadContext context) { context.enqueueWork(() -> ClientAutopilotBridge.onHud(p)); }
}
