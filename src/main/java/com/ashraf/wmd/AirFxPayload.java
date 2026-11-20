package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public record AirFxPayload(int kind, UUID ship, Vec3 a, Vec3 b, Vec3 c, float power) implements CustomPacketPayload {

    public static final int CRASH = 0, EJECT = 1, WIRE = 2, CATAPULT = 3;

    public static final int TORPEDO = 4, BREACH = 5, PING = 6, MINE_ARM = 7, GEAR_SNAP = 8, PYLON = 9, ENGINE_HIT = 10, HACK = 11, DIESEL = 12, CRANE = 13;

    public static final int AIR_BURST = 14, FUEL_FIRE = 15;

    public static final int ROD_RING = 16;

    public static void at(ServerLevel sl, int kind, Vec3 at, Vec3 b, float power, double range) {
        send(sl, at, range, new AirFxPayload(kind, null, at, b == null ? Vec3.ZERO : b, Vec3.ZERO, power));
    }
    private static final UUID NONE = new UUID(0L, 0L);

    public static final Type<AirFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "air_fx"));

    public static final StreamCodec<FriendlyByteBuf, AirFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind()); buf.writeUUID(p.ship() == null ? NONE : p.ship());
                vec(buf, p.a()); vec(buf, p.b()); vec(buf, p.c()); buf.writeFloat(p.power());
            },
            buf -> {
                int k = buf.readByte(); UUID s = buf.readUUID();
                return new AirFxPayload(k, NONE.equals(s) ? null : s, vec(buf), vec(buf), vec(buf), buf.readFloat());
            });

    private static void vec(FriendlyByteBuf buf, Vec3 v) {
        Vec3 w = v == null ? Vec3.ZERO : v;
        buf.writeDouble(w.x); buf.writeDouble(w.y); buf.writeDouble(w.z);
    }

    private static Vec3 vec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }

    public static void send(ServerLevel sl, Vec3 at, double range, AirFxPayload p) {
        if (sl == null || at == null) return;
        double r2 = range * range;
        for (ServerPlayer pl : sl.players()) if (pl.position().distanceToSqr(at) <= r2) PacketDistributor.sendToPlayer(pl, p);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
