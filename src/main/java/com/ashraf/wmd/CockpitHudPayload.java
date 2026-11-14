package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CockpitHudPayload(BlockPos cockpit, int seat, int flags, float heading, float pitch, float roll, float speed, float vy, float alt, float agl,
                                float throttle, int engines, int running, int station, float lx, float ly, float lz, float lockDist,
                                int stnKind, int stnCount, int flares, int chaff, int gunRounds, int gear, long pod, float px, float py, float pz,
                                float bDev, float bDist, float bSide, int sys, float fuel, float ix, float iy, float iz, float qx, float qy, float qz) implements CustomPacketPayload {

    public int chute() { return sys & 3; }
    public boolean hover() { return (sys & 4) != 0; }
    public int engineDamage() { return (sys >> 3) & 3; }
    public int damagedEngines() { return (sys >> 5) & 15; }
    public int rotorBlades() { return (sys >> 9) & 7; }
    public int rotorLength() { return (sys >> 12) & 31; }
    public int rotorSpool() { return (sys >> 17) & 127; }

    public int refuel() { return (sys >> 24) & 7; }
    public int hook() { return (sys >> 27) & 3; }

    static int packP7(int refuel, int hook) { return ((refuel & 7) << 24) | ((hook & 3) << 27); }

    public int ramp() { return (sys >>> 29) & 7; }
    static int packRamp(int ramp) { return (ramp & 7) << 29; }

    static int packSys(int chute, boolean hover, int dmg, int damaged, int blades, int length, int spool) {
        return ((chute + 1) & 3) | (hover ? 4 : 0) | ((dmg & 3) << 3) | ((Math.min(15, damaged) & 15) << 5) | ((blades & 7) << 9) | ((length & 31) << 12)
                | ((Math.max(0, Math.min(127, spool)) & 127) << 17);
    }

    public static final int F_POWER = 1, F_SHIP = 2, F_RADAR = 4, F_AB = 8, F_EJECTED = 16, F_TANDEM = 32, F_CONTROL = 64, F_NORADAR = 128, F_JAM = 256, F_LOCKJAM = 512, F_AGMGPS = 1024, F_GROWL = 2048, F_SEA = 4096, F_PIP_HIT = 8192, F_PIP_AIR = 16384, F_PODMEM = 32768;

    public static final long NONE = Long.MIN_VALUE;
    public boolean lasing() { return !Float.isNaN(px); }
    public boolean beacon() { return !Float.isNaN(bDev); }

    public boolean has(int f) { return (flags & f) != 0; }
    public boolean locked() { return !Float.isNaN(lx); }

    public boolean bombAim() { return !Float.isNaN(ix); }

    public boolean lead() { return !Float.isNaN(qx); }

    public static final Type<CockpitHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_hud"));

    public static final StreamCodec<FriendlyByteBuf, CockpitHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.cockpit()); buf.writeByte(p.seat()); buf.writeVarInt(p.flags());
                buf.writeFloat(p.heading()); buf.writeFloat(p.pitch()); buf.writeFloat(p.roll()); buf.writeFloat(p.speed()); buf.writeFloat(p.vy());
                buf.writeFloat(p.alt()); buf.writeFloat(p.agl()); buf.writeFloat(p.throttle()); buf.writeByte(p.engines()); buf.writeByte(p.running()); buf.writeByte(p.station());
                buf.writeFloat(p.lx()); buf.writeFloat(p.ly()); buf.writeFloat(p.lz()); buf.writeFloat(p.lockDist());
                buf.writeByte(p.stnKind()); buf.writeByte(p.stnCount()); buf.writeVarInt(p.flares()); buf.writeVarInt(p.chaff()); buf.writeVarInt(p.gunRounds());
                buf.writeByte(p.gear()); buf.writeLong(p.pod()); buf.writeFloat(p.px()); buf.writeFloat(p.py()); buf.writeFloat(p.pz());
                buf.writeFloat(p.bDev()); buf.writeFloat(p.bDist()); buf.writeFloat(p.bSide()); buf.writeVarInt(p.sys()); buf.writeFloat(p.fuel()); buf.writeFloat(p.ix()); buf.writeFloat(p.iy()); buf.writeFloat(p.iz());
                buf.writeFloat(p.qx()); buf.writeFloat(p.qy()); buf.writeFloat(p.qz());
            },
            buf -> new CockpitHudPayload(buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readByte(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte(), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CockpitHudPayload p, IPayloadContext context) { context.enqueueWork(() -> ClientCockpitBridge.onHud(p)); }
}
