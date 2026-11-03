package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public final class OrbitMath {

    private OrbitMath() {}

    public static final double HALF_D = 5.67;

    public static final double BLOCKS_PER_UNIT = 2000.0;

    public static long periodMs() { return Balance.SAT_PASS_MINUTES * 60_000L; }
    public static long windowMs() { return Balance.SAT_OVERHEAD_SECONDS * 1000L; }
    public static long lifeMs() { return Balance.SAT_LIFE_HOURS * 3_600_000L; }

    public static final String CUBESAT = "cubesat";

    public static double speed() { return 2.0 * HALF_D / Balance.SAT_OVERHEAD_SECONDS; }

    public static double wrap() { return speed() * Balance.SAT_PASS_MINUTES * 60.0; }

    public record Sat(int id, UUID owner, String ownerName, String type, long launchMs, long launchGameTime, double phase, double heading, double cross) {

        public boolean isCubesat() { return CUBESAT.equals(type); }

        public long lifeMs() { return isCubesat() ? Balance.CUBESAT_MINUTES * 60_000L : OrbitMath.lifeMs(); }

        public long endMs() { return launchMs + lifeMs(); }

        public Sat withPhase(double p) { return new Sat(id, owner, ownerName, type, launchMs, launchGameTime, frac(p), heading, cross); }

        public Sat withLaunchMs(long ms) { return new Sat(id, owner, ownerName, type, ms, launchGameTime, phase, heading, cross); }

        public Sat withOwner(UUID o, String name) { return new Sat(id, o, name == null ? "" : name, type, launchMs, launchGameTime, phase, heading, cross); }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id); t.putUUID("owner", owner); t.putString("ownerName", ownerName); t.putString("type", type);
            t.putLong("launchMs", launchMs); t.putLong("launchGt", launchGameTime);
            t.putDouble("phase", phase); t.putDouble("heading", heading); t.putDouble("cross", cross);
            return t;
        }

        public static Sat load(CompoundTag t) {
            return new Sat(t.getInt("id"), t.hasUUID("owner") ? t.getUUID("owner") : new UUID(0L, 0L), t.getString("ownerName"), t.getString("type"),
                    t.getLong("launchMs"), t.getLong("launchGt"), t.getDouble("phase"), t.getDouble("heading"), t.getDouble("cross"));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id); buf.writeUUID(owner); buf.writeUtf(ownerName, 64); buf.writeUtf(type, 16);
            buf.writeLong(launchMs); buf.writeLong(launchGameTime);
            buf.writeDouble(phase); buf.writeDouble(heading); buf.writeDouble(cross);
        }

        public static Sat read(FriendlyByteBuf buf) {
            return new Sat(buf.readVarInt(), buf.readUUID(), buf.readUtf(64), buf.readUtf(16), buf.readLong(), buf.readLong(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }

    public static double frac(double v) { return v - Math.floor(v); }

    private static double centred(double a) { double l = wrap(); return a - l * Math.floor(a / l + 0.5); }

    static double along(Sat s, double x, double z) { return (x * Math.sin(s.heading()) - z * Math.cos(s.heading())) / BLOCKS_PER_UNIT; }

    public static double offset(Sat s, double x, double z, long nowMs) {
        long p = periodMs();
        double t = Math.floorMod(nowMs, p) / 1000.0;
        return centred(speed() * t + s.phase() * wrap() - along(s, x, z));
    }

    public static boolean isOverhead(Sat s, double x, double z, long nowMs) { return Math.abs(offset(s, x, z, nowMs)) <= HALF_D; }

    public static long nextPass(Sat s, double x, double z, long nowMs) {
        double d = offset(s, x, z, nowMs);
        if (Math.abs(d) <= HALF_D) return 0L;
        double l = wrap(), gap = (-HALF_D - d) % l;
        if (gap < 0) gap += l;
        return (long) Math.ceil(gap / speed() * 1000.0);
    }

    public static long overheadLeft(Sat s, double x, double z, long nowMs) {
        double d = offset(s, x, z, nowMs);
        if (Math.abs(d) > HALF_D) return 0L;
        return (long) Math.ceil((HALF_D - d) / speed() * 1000.0);
    }

    public static double[] sky(Sat s, double x, double z, long nowMs) {
        double d = offset(s, x, z, nowMs), c = s.cross();
        double sh = Math.sin(s.heading()), ch = Math.cos(s.heading());

        double dx = d * sh + c * ch, dz = -d * ch + c * sh, dy = 1.0;
        double flat = Math.sqrt(dx * dx + dz * dz), len = Math.sqrt(flat * flat + dy * dy);
        double az = Math.toDegrees(Math.atan2(dx, -dz));
        if (az < 0) az += 360.0;
        double el = Math.toDegrees(Math.atan2(dy, flat));
        return new double[] { az, el, dx / len, dy / len, dz / len };
    }

    public static double phaseForPass(Sat s, double x, double z, long nowMs, double secondsIn) {
        double want = -HALF_D + speed() * secondsIn;
        double t = Math.floorMod(nowMs, periodMs()) / 1000.0;
        return frac((want + along(s, x, z) - speed() * t) / wrap());
    }

    public static String mmss(long ms) {
        long s = Math.max(0L, (ms + 999L) / 1000L);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
