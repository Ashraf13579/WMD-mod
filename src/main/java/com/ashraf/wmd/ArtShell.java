package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record ArtShell(long id, UUID owner, String ownerName, int gun, int type, double ox, double oy, double oz, double tx, double ty, double tz,
                       long start, int dur, long gunPos, int seq, boolean last, boolean prepared) {

    private static final UUID NONE = new UUID(0L, 0L);

    public ArtShell {
        owner = owner == null ? NONE : owner;
        ownerName = ownerName == null ? "" : ownerName.length() > 32 ? ownerName.substring(0, 32) : ownerName;
        dur = Math.max(1, dur);
    }

    public long endTime() { return start + dur; }

    public boolean interceptable() { return gun != Artillery.SUPER_GUN; }

    public ArtShell forStranger() {
        int h = net.minecraft.util.Mth.murmurHash3Mixer((int) (id ^ (id >>> 32)));
        double a = Math.atan2(oz - tz, ox - tx) + ((h & 0xFFFF) / 32767.5 - 1.0) * 0.25;
        double d = Math.max(120.0, dur * (3.0 + ((h >>> 16) & 0xFF) / 255.0));
        return new ArtShell(id, null, "", gun, type, tx + Math.cos(a) * d, ty, tz + Math.sin(a) * d, tx, ty, tz, start, dur, 0L, 0, false, false);
    }

    public ArtShell forStrangerAtGun() { return new ArtShell(id, null, "", gun, type, ox, oy, oz, tx, ty, tz, start, dur, 0L, 0, false, false); }

    public ArtShell asPrepared() { return new ArtShell(id, owner, ownerName, gun, type, ox, oy, oz, tx, ty, tz, start, dur, gunPos, seq, last, true); }

    public double vy0() { return (ty - oy) / dur + Balance.ART_GRAVITY * dur * 0.5; }

    public Vec3 position(double t) {
        double tau = Math.max(0.0, Math.min(dur, t - start)), f = tau / dur;
        return new Vec3(ox + (tx - ox) * f, oy + vy0() * tau - Balance.ART_GRAVITY * tau * tau * 0.5, oz + (tz - oz) * f);
    }

    public Vec3 velocity(double t) {
        double tau = Math.max(0.0, Math.min(dur, t - start));
        return new Vec3((tx - ox) / dur, vy0() - Balance.ART_GRAVITY * tau, (tz - oz) / dur);
    }

    public double apex() {
        double tTop = Math.max(0.0, Math.min(dur, vy0() / Balance.ART_GRAVITY));
        return Math.max(Math.max(oy, ty), oy + vy0() * tTop - Balance.ART_GRAVITY * tTop * tTop * 0.5);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id); t.putUUID("o", owner); t.putString("on", ownerName); t.putInt("g", gun); t.putInt("ty", type);
        t.putDouble("ox", ox); t.putDouble("oy", oy); t.putDouble("oz", oz); t.putDouble("tx", tx); t.putDouble("tyy", ty); t.putDouble("tz", tz);
        t.putLong("s", start); t.putInt("d", dur); t.putLong("gp", gunPos); t.putInt("seq", seq); t.putBoolean("last", last); t.putBoolean("prep", prepared);
        return t;
    }

    public static ArtShell load(CompoundTag t) {
        return new ArtShell(t.getLong("id"), t.hasUUID("o") ? t.getUUID("o") : null, t.getString("on"), Math.max(0, Math.min(Artillery.SUPER_GUN, t.getInt("g"))), Artillery.clampType(t.getInt("ty")),
                t.getDouble("ox"), t.getDouble("oy"), t.getDouble("oz"), t.getDouble("tx"), t.getDouble("tyy"), t.getDouble("tz"),
                t.getLong("s"), t.getInt("d"), t.getLong("gp"), t.getInt("seq"), t.getBoolean("last"), t.getBoolean("prep"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id); buf.writeUUID(owner); buf.writeByte(gun); buf.writeByte(type);
        buf.writeDouble(ox); buf.writeDouble(oy); buf.writeDouble(oz); buf.writeDouble(tx); buf.writeDouble(ty); buf.writeDouble(tz);
        buf.writeLong(start); buf.writeVarInt(dur); buf.writeLong(gunPos);
    }

    public static ArtShell read(FriendlyByteBuf buf) {
        long id = buf.readLong();
        UUID o = buf.readUUID();
        int g = Math.max(0, Math.min(Artillery.SUPER_GUN, buf.readByte())), ty = Artillery.clampType(buf.readByte());
        double ox = buf.readDouble(), oy = buf.readDouble(), oz = buf.readDouble(), tx = buf.readDouble(), tyy = buf.readDouble(), tz = buf.readDouble();
        long s = buf.readLong();
        int d = buf.readVarInt();
        long gp = buf.readLong();
        return new ArtShell(id, o, "", g, ty, ox, oy, oz, tx, tyy, tz, s, d, gp, 0, false, false);
    }
}
