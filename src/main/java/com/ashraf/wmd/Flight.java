package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Flight(long id, int tier, double ox, double oy, double oz, int tx, int tz, double endY, double aimY,
                     long start, int duration, String launcher, String ownerId, boolean prepared, MissileBuild build,
                     int fx, int fz, long swerveAt, int ptx, int ptz, int pdur, java.util.List<Leg> legs, int uflags, CruiseRoute cruise) {

    public Flight(long id, int tier, double ox, double oy, double oz, int tx, int tz, double endY, double aimY,
                  long start, int duration, String launcher, String ownerId, boolean prepared, MissileBuild build,
                  int fx, int fz, long swerveAt, int ptx, int ptz, int pdur, java.util.List<Leg> legs, int uflags) {
        this(id, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, build, fx, fz, swerveAt, ptx, ptz, pdur, legs, uflags, null);
    }

    public boolean isCruise() { return cruise != null; }

    public boolean isDeadReckoned() { return cruise != null && cruise.dr != null; }

    public boolean targetKnown() { return cruise == null || cruise.dr == null || cruise.dr.known(); }

    public record Leg(long at, double s, double k, double x, double z, double h) {}

    public static final int U_RETARGETED = 1, U_HOMING_DONE = 2, U_HOMED = 4, U_LOCKED = 8, U_CHAFFED = 16;

    public static final int U_SEEK_DONE = 32, U_SEEK_LOCK = 64, U_SEEK_LOST = 128;

    public static final int U_AIRBURST = 256;

    public static final int MAX_LEGS = 8;

    public Flight(long id, int tier, double ox, double oy, double oz, int tx, int tz, double endY, double aimY,
                  long start, int duration, String launcher, String ownerId, boolean prepared, MissileBuild build,
                  int fx, int fz, long swerveAt) {
        this(id, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, build, fx, fz, swerveAt, tx, tz, duration, java.util.List.of(), 0);
    }

    public static final double LAUNCH_HEIGHT = 330.0;

    public static final double SWERVE_ALTITUDE = 450.0;
    public static final int SWERVE_TICKS = 100;
    public static final double MAX_DECOY = 50.0;
    public static final double V_START = 6.0;
    public static final double V_CLIMB = 7.5;
    public static final double V_CRUISE = 2.6;
    public static final double V_IMPACT = 3.5;
    public static final double END_SLOW = 0.1;

    public long endTime() { return start + duration; }

    public boolean isOwnedBy(java.util.UUID id, String name) {
        if (!ownerId.isEmpty()) return id != null && ownerId.equals(id.toString());
        return name != null && launcher.equals(name);
    }

    public double distance() {
        double dx = ptx + 0.5 - ox, dz = ptz + 0.5 - oz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double cruiseAltitude(int tier, double d) {
        double base = d >= 2000.0 ? Math.max(2000.0, d * 0.3) : 900.0 + d / 2000.0 * 1100.0;
        double f = tier <= 1 ? 0.55 : tier == 2 ? 0.8 : 1.0;
        return Math.max(700.0, base * f);
    }

    public static double[] plan(int tier, double d, double oy, double aimY) { return plan(tier, d, oy, aimY, 1.0); }

    public static double[] plan(int tier, double d, double oy, double aimY, double speed) { return plan(tier, d, oy, aimY, speed, 1.0); }

    public static double[] plan(int tier, double d, double oy, double aimY, double speed, double apexK) {
        double apex = Math.max(Math.max(oy + (cruiseAltitude(tier, d) - LAUNCH_HEIGHT) * apexK, aimY + 500.0 * apexK), oy + 150.0 * Math.max(apexK, 0.5));
        boolean hyper = apexK < 1.0;
        double climb = (apex - oy) / (V_CLIMB * (hyper ? Balance.HYPER_CLIMB : 1.0));
        double dive = hyper ? (apex - aimY) / Balance.HYPER_DIVE : 2.0 * (apex - aimY) / V_IMPACT;
        double cruise = d / (V_CRUISE * speed);
        return new double[] { apex, climb, cruise, dive };
    }

    public static int totalTicks(int tier, double d, double oy, double aimY) { return totalTicks(tier, d, oy, aimY, 1.0); }

    public static int totalTicks(int tier, double d, double oy, double aimY, double speed) { return totalTicks(tier, d, oy, aimY, speed, 1.0); }

    public static int totalTicks(int tier, double d, double oy, double aimY, double speed, double apexK) {
        double[] p = plan(tier, d, oy, aimY, speed, apexK);
        return (int) Math.round(p[1] + p[2] + p[3]);
    }

    public double apex() { return plan(tier, distance(), oy, aimY, 1.0, apexK())[0]; }

    public double apexK() { return build != null && build.hypersonic() ? Balance.HYPER_APEX : 1.0; }

    public Vec3 position(double gameTime) {
        Vec3 b = basePosition(gameTime);
        return Evade.any(id) ? b.add(Evade.offset(id, gameTime)) : b;
    }

    public Vec3 basePosition(double gameTime) {
        if (cruise != null) return cruise.position(this, gameTime);
        double t = Mth.clamp(gameTime - start, 0.0, (double) duration) + start;
        Leg leg = legAt(t);
        if (leg == null) {
            double[] yh = planPoint(t);
            double h = yh[1];
            double ax = ptx + 0.5, az = ptz + 0.5;
            if (swerveAt >= 0) {

                double q = Mth.clamp((t - swerveAt) / SWERVE_TICKS, 0.0, 1.0);
                q = q - Math.sin(2.0 * Math.PI * q) / (2.0 * Math.PI);
                ax = (fx + 0.5) + (ax - (fx + 0.5)) * q;
                az = (fz + 0.5) + (az - (fz + 0.5)) * q;
            }
            return new Vec3(ox + (ax - ox) * h, yh[0], oz + (az - oz) * h);
        }

        double[] yh = planPoint(planTime(t));
        double u = Mth.clamp((yh[1] - leg.h()) / Math.max(1.0E-6, 1.0 - leg.h()), 0.0, 1.0);
        return new Vec3(leg.x() + (tx + 0.5 - leg.x()) * u, yh[0], leg.z() + (tz + 0.5 - leg.z()) * u);
    }

    public Leg legAt(double t) {
        Leg best = null;
        for (Leg l : legs) if (l.at() <= t) best = l;
        return best;
    }

    public double planTime(double t) {
        Leg leg = legAt(t);
        return leg == null ? t : Math.min(leg.s() + (t - leg.at()) * leg.k(), start + (double) pdur);
    }

    public double[] planPoint(double s) {
        double t = Mth.clamp(s - start, 0.0, (double) pdur);
        double d = distance();
        double[] p = plan(tier, d, oy, aimY, 1.0, apexK());
        double apex = p[0];
        double k = pdur / (p[1] + p[2] + p[3]);
        double ta = p[1] * k, tc = p[2] * k, td = p[3] * k;
        double hup = apex - oy, hd = apex - aimY;
        double wc = d / (ta * 0.5 + tc + td * (1.0 + END_SLOW) * 0.5);
        double y, ground;
        if (t < ta) {
            double u = t / ta;
            double m0 = Mth.clamp((apexK() < 1.0 ? Balance.HYPER_V_START : V_START) * ta / hup, 0.0, 2.5);
            y = oy + hup * (m0 * (u * u * u - 2.0 * u * u + u) + 3.0 * u * u - 2.0 * u * u * u);
            ground = wc * ta * u * u * 0.5;
        } else if (t < ta + tc) {
            y = apex;
            ground = wc * (ta * 0.5 + (t - ta));
        } else {
            double sd = t - ta - tc;
            double u = Math.min(1.0, sd / td);
            double fall = apexK() < 1.0 ? u * (0.55 + 0.45 * u) : u * u;
            y = apex - hd * fall + (endY - aimY) * Math.pow(u, 8);
            ground = wc * (ta * 0.5 + tc + sd - (1.0 - END_SLOW) * sd * sd / (2.0 * td));
        }
        double h = d > 0.001 ? Mth.clamp(ground / d, 0.0, 1.0) : 1.0;
        return new double[] { y, h };
    }

    public Flight withLeg(long now, int nx, int nz, double k, int addFlags) {
        Vec3 p = basePosition(now);
        double s = planTime(now);
        double h = planPoint(s)[1];
        java.util.List<Leg> l = new java.util.ArrayList<>(legs);
        l.add(new Leg(now, s, k, p.x, p.z, h));
        while (l.size() > MAX_LEGS) l.remove(1);
        double end = now + (start + (double) pdur - s) / Math.max(1.0E-3, k);
        int dur = (int) Math.max(now + 1 - start, Math.round(end - start));
        return new Flight(id, tier, ox, oy, oz, nx, nz, endY, aimY, start, dur, launcher, ownerId, false, build, fx, fz, swerveAt,
                ptx, ptz, pdur, java.util.List.copyOf(l), uflags | addFlags, cruise);
    }

    public Flight withCruiseEnd(int nx, int nz, CruiseRoute r, int dur, double ey) {
        return new Flight(id, tier, ox, oy, oz, nx, nz, ey, aimY, start, dur, launcher, ownerId, prepared, build, fx, fz, swerveAt, ptx, ptz, pdur, legs, uflags, r);
    }

    public boolean inDive(double t) {
        double[] p = plan(tier, distance(), oy, aimY, 1.0, apexK());
        double k = pdur / (p[1] + p[2] + p[3]);
        return planTime(t) - start >= (p[1] + p[2]) * k;
    }

    public Flight withBuild(MissileBuild b) {
        return new Flight(id, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, b, fx, fz, swerveAt, ptx, ptz, pdur, legs, uflags, cruise);
    }

    public Flight withId(long nid) {
        return new Flight(nid, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, build, fx, fz, swerveAt, ptx, ptz, pdur, legs, uflags, cruise);
    }

    public Flight withFlags(int f) {
        return new Flight(id, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, build, fx, fz, swerveAt, ptx, ptz, pdur, legs, f, cruise);
    }

    public boolean hasLegs() { return !legs.isEmpty(); }

    public double planLeft(double t) { return Math.max(0.0, start + (double) pdur - planTime(t)); }

    public boolean diverts() { return swerveAt >= 0 && legs.isEmpty(); }

    public long swerveAt() { return swerveAt; }

    public Flight withSwerve() {
        long end = start + duration, t = end;
        while (t > start && basePosition(t).y - endY <= SWERVE_ALTITUDE) t--;
        return new Flight(id, tier, ox, oy, oz, tx, tz, endY, aimY, start, duration, launcher, ownerId, prepared, build, fx, fz, t, ptx, ptz, pdur, legs, uflags, cruise);
    }

    public Vec3 seenTarget(double gameTime) {
        if (swerveAt >= 0 && legs.isEmpty() && gameTime < swerveAt + SWERVE_TICKS * 0.5) return new Vec3(fx + 0.5, endY, fz + 0.5);
        return target();
    }

    public Vec3 target() { return new Vec3(tx + 0.5, endY, tz + 0.5); }

    public Flight withEndY(double y) {
        return new Flight(id, tier, ox, oy, oz, tx, tz, y, aimY, start, duration, launcher, ownerId, true, build, fx, fz, swerveAt, ptx, ptz, pdur, legs, uflags, cruise);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id); t.putInt("tier", tier);
        t.putDouble("ox", ox); t.putDouble("oy", oy); t.putDouble("oz", oz);
        t.putInt("tx", tx); t.putInt("tz", tz); t.putDouble("endY", endY); t.putDouble("aimY", aimY);
        t.putLong("start", start); t.putInt("duration", duration);
        t.putString("launcher", launcher); t.putString("ownerId", ownerId); t.putBoolean("prepared", prepared);
        t.put("build", build.save());
        t.putInt("fx", fx); t.putInt("fz", fz); t.putLong("sw", swerveAt);
        if (!legs.isEmpty() || uflags != 0) {
            t.putInt("ptx", ptx); t.putInt("ptz", ptz); t.putInt("pdur", pdur); t.putInt("uf", uflags);
            net.minecraft.nbt.ListTag lt = new net.minecraft.nbt.ListTag();
            for (Leg l : legs) {
                CompoundTag c = new CompoundTag();
                c.putLong("at", l.at()); c.putDouble("s", l.s()); c.putDouble("k", l.k()); c.putDouble("x", l.x()); c.putDouble("z", l.z()); c.putDouble("h", l.h());
                lt.add(c);
            }
            t.put("legs", lt);
        }
        if (cruise != null) t.put("cruise", cruise.save());
        return t;
    }

    public static Flight load(CompoundTag t) {
        Flight f = new Flight(t.getLong("id"), t.getInt("tier"), t.getDouble("ox"), t.getDouble("oy"), t.getDouble("oz"),
                t.getInt("tx"), t.getInt("tz"), t.getDouble("endY"), t.contains("aimY") ? t.getDouble("aimY") : t.getDouble("endY"), t.getLong("start"), t.getInt("duration"),
                t.getString("launcher"), t.getString("ownerId"), t.getBoolean("prepared"), MissileBuild.load(t.getCompound("build"), Math.max(1, t.getInt("tier"))),
                t.contains("fx") ? t.getInt("fx") : t.getInt("tx"), t.contains("fz") ? t.getInt("fz") : t.getInt("tz"), t.contains("sw") ? t.getLong("sw") : -1L);
        if (t.contains("cruise")) {
            CruiseRoute r = CruiseRoute.load(t.getCompound("cruise"));
            return new Flight(f.id, f.tier, f.ox, f.oy, f.oz, f.tx, f.tz, f.endY, f.aimY, f.start, f.duration, f.launcher, f.ownerId, f.prepared, f.build, f.fx, f.fz, f.swerveAt,
                    t.contains("ptx") ? t.getInt("ptx") : f.tx, t.contains("ptz") ? t.getInt("ptz") : f.tz, t.contains("pdur") ? Math.max(1, t.getInt("pdur")) : f.duration, java.util.List.of(), t.getInt("uf"), r);
        }
        if (!t.contains("pdur")) return f;
        java.util.List<Leg> legs = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag e : t.getList("legs", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            legs.add(new Leg(c.getLong("at"), c.getDouble("s"), c.getDouble("k"), c.getDouble("x"), c.getDouble("z"), c.getDouble("h")));
        }
        return new Flight(f.id, f.tier, f.ox, f.oy, f.oz, f.tx, f.tz, f.endY, f.aimY, f.start, f.duration, f.launcher, f.ownerId, f.prepared, f.build, f.fx, f.fz, f.swerveAt,
                t.getInt("ptx"), t.getInt("ptz"), Math.max(1, t.getInt("pdur")), java.util.List.copyOf(legs), t.getInt("uf"), null);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id); buf.writeVarInt(tier);
        buf.writeDouble(ox); buf.writeDouble(oy); buf.writeDouble(oz);
        buf.writeInt(tx); buf.writeInt(tz); buf.writeDouble(endY); buf.writeDouble(aimY);
        buf.writeLong(start); buf.writeVarInt(duration);
        buf.writeUtf(launcher, 64); buf.writeUtf(ownerId, 40); buf.writeBoolean(prepared);
        MissileBuild.STREAM_CODEC.encode(buf, build);
        buf.writeInt(fx); buf.writeInt(fz); buf.writeLong(swerveAt);
        buf.writeInt(ptx); buf.writeInt(ptz); buf.writeVarInt(pdur); buf.writeVarInt(uflags);
        buf.writeVarInt(legs.size());
        for (Leg l : legs) { buf.writeLong(l.at()); buf.writeDouble(l.s()); buf.writeDouble(l.k()); buf.writeDouble(l.x()); buf.writeDouble(l.z()); buf.writeDouble(l.h()); }
        buf.writeBoolean(cruise != null);
        if (cruise != null) cruise.write(buf);
    }

    public static Flight read(FriendlyByteBuf buf) {
        Flight f = new Flight(buf.readLong(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readVarInt(), buf.readUtf(64), buf.readUtf(40), buf.readBoolean(),
                MissileBuild.STREAM_CODEC.decode(buf), buf.readInt(), buf.readInt(), buf.readLong());
        int ptx = buf.readInt(), ptz = buf.readInt(), pdur = buf.readVarInt(), uf = buf.readVarInt(), n = buf.readVarInt();
        java.util.List<Leg> legs = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) legs.add(new Leg(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        CruiseRoute cr = buf.readBoolean() ? CruiseRoute.read(buf) : null;
        return new Flight(f.id, f.tier, f.ox, f.oy, f.oz, f.tx, f.tz, f.endY, f.aimY, f.start, f.duration, f.launcher, f.ownerId, f.prepared, f.build, f.fx, f.fz, f.swerveAt,
                ptx, ptz, pdur, java.util.List.copyOf(legs), uf, cr);
    }
}
