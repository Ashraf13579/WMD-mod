package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class CruiseRoute {

    public static final int STEP = 32;

    public static final double EXIT_D = 48.0, TURN_R = 60.0, CLIMB_D = 120.0, DIVE_D = 70.0, POP = 14.0, PITCH_DEG = 35.0;

    public static final int ACCEL_T = 30;

    public final double ox, oz, fx, fz;
    public final int[] xs, zs;
    public final float speed;
    public final short[] alt;

    public final Dr dr;

    private double[] px, pz, cum;

    private double lastCornerS, lastCornerIn;

    public CruiseRoute(double ox, double oz, double fx, double fz, int[] xs, int[] zs, float speed, short[] alt) {
        this(ox, oz, fx, fz, xs, zs, speed, alt, null);
    }

    private CruiseRoute(double ox, double oz, double fx, double fz, int[] xs, int[] zs, float speed, short[] alt, Dr dr) {
        this.dr = dr;
        this.ox = ox; this.oz = oz;
        double l = Math.hypot(fx, fz);
        this.fx = l > 1.0E-6 ? fx / l : 0.0; this.fz = l > 1.0E-6 ? fz / l : -1.0;
        this.xs = xs; this.zs = zs;
        this.speed = Math.max(0.05F, speed);
        this.alt = alt;
    }

    public record Dr(long t0, double x, double y, double z, double vx, double vy, double vz, double ex, double ey, double ez, double wx, double wy, double wz,
                     int blend, boolean known) {

        static final double BACK = 40.0;

        public Vec3 line(double t) {
            double dt = Math.max(t - t0, -BACK);
            return new Vec3(x + vx * dt, y + vy * dt, z + vz * dt);
        }

        public Vec3 at(double t) {
            double dt = Math.max(t - t0, -BACK);
            double a, b;
            if (dt < 0.0) { a = 1.0; b = dt; }
            else if (blend > 0 && dt < blend) {
                double u = dt / blend;
                a = 2 * u * u * u - 3 * u * u + 1;
                b = (u * u * u - 2 * u * u + u) * blend;
            } else return new Vec3(x + vx * dt, y + vy * dt, z + vz * dt);
            return new Vec3(x + vx * dt + a * ex + b * wx, y + vy * dt + a * ey + b * wy, z + vz * dt + a * ez + b * wz);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeLong(t0);
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
            buf.writeFloat((float) vx); buf.writeFloat((float) vy); buf.writeFloat((float) vz);
            buf.writeFloat((float) ex); buf.writeFloat((float) ey); buf.writeFloat((float) ez);
            buf.writeFloat((float) wx); buf.writeFloat((float) wy); buf.writeFloat((float) wz);
            buf.writeVarInt(blend); buf.writeBoolean(known);
        }

        static Dr read(FriendlyByteBuf buf) {
            long t0 = buf.readLong();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            double[] f = new double[9];
            for (int i = 0; i < 9; i++) {
                f[i] = buf.readFloat();
                if (!Double.isFinite(f[i])) f[i] = 0.0;
            }
            int blend = Mth.clamp(buf.readVarInt(), 0, 200);
            boolean known = buf.readBoolean();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("cruise dr: bad position");
            return new Dr(t0, x, y, z, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], blend, known);
        }
    }

    public static CruiseRoute deadReckoned(Dr d) {
        return new CruiseRoute(d.x(), d.z(), d.vx(), d.vz(), new int[] { Mth.floor(d.x()) }, new int[] { Mth.floor(d.z()) },
                (float) Math.hypot(d.vx(), d.vz()), new short[0], d);
    }

    public int segmentAt(double s) {
        build();
        s = Mth.clamp(s, 0.0, cum[cum.length - 1]);
        int lo = 0, hi = cum.length - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (cum[m] <= s) lo = m; else hi = m; }
        return lo;
    }

    public CruiseRoute withEnd(int x, int z) {
        if (dr != null) return this;
        int[] nx = xs.clone(), nz = zs.clone();
        nx[nx.length - 1] = x; nz[nz.length - 1] = z;
        return new CruiseRoute(ox, oz, fx, fz, nx, nz, speed, alt);
    }

    public CruiseRoute withAlt(short[] a) { return new CruiseRoute(ox, oz, fx, fz, xs, zs, speed, a); }

    public static double radarRange(Flight f, double normal) { return f.isCruise() ? Math.min(normal, Balance.CRUISE_RADAR_RANGE) : normal; }

    private void build() {
        if (cum != null) return;
        int n = xs.length;
        double[] qx = new double[n + 2], qz = new double[n + 2];
        qx[0] = ox; qz[0] = oz;
        qx[1] = ox + fx * EXIT_D; qz[1] = oz + fz * EXIT_D;
        for (int i = 0; i < n; i++) { qx[i + 2] = xs[i] + 0.5; qz[i + 2] = zs[i] + 0.5; }
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[] { qx[0], qz[0] });
        int lastIdx = 0;
        double lastC = 0.0;
        for (int i = 1; i < qx.length - 1; i++) {
            double ax = qx[i] - qx[i - 1], az = qz[i] - qz[i - 1], bx = qx[i + 1] - qx[i], bz = qz[i + 1] - qz[i];
            double la = Math.hypot(ax, az), lb = Math.hypot(bx, bz);
            if (i == qx.length - 2) { lastIdx = pts.size(); lastC = 0.0; lastCornerIn = la; }
            if (la < 1.0E-3 || lb < 1.0E-3) { pts.add(new double[] { qx[i], qz[i] }); continue; }
            double c = Math.min(TURN_R, Math.min(la, lb) / 2.2);
            if (i == qx.length - 2) lastC = c;
            double sx = qx[i] - ax / la * c, sz = qz[i] - az / la * c, ex = qx[i] + bx / lb * c, ez = qz[i] + bz / lb * c;
            for (int k = 0; k <= 8; k++) {
                double u = k / 8.0, a = (1 - u) * (1 - u), b = 2 * u * (1 - u), d = u * u;
                pts.add(new double[] { a * sx + b * qx[i] + d * ex, a * sz + b * qz[i] + d * ez });
            }
        }
        pts.add(new double[] { qx[qx.length - 1], qz[qz.length - 1] });
        double[] x = new double[pts.size()], z = new double[pts.size()], c = new double[pts.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = pts.get(i)[0]; z[i] = pts.get(i)[1];
            c[i] = i == 0 ? 0.0 : c[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
        }
        px = x; pz = z; cum = c;
        lastCornerS = c[Math.min(lastIdx, c.length - 1)] + lastC;
    }

    public double lastCorner() { build(); return lastCornerS; }

    public double lastTurnReach() { build(); return Math.min(TURN_R, lastCornerIn / 2.2); }

    public double length() { build(); return cum[cum.length - 1]; }

    public double[] ground(double s) {
        build();
        s = Mth.clamp(s, 0.0, cum[cum.length - 1]);
        int lo = 0, hi = cum.length - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (cum[m] <= s) lo = m; else hi = m; }
        double seg = cum[hi] - cum[lo], u = seg > 1.0E-9 ? (s - cum[lo]) / seg : 0.0;
        return new double[] { px[lo] + (px[hi] - px[lo]) * u, pz[lo] + (pz[hi] - pz[lo]) * u };
    }

    public double distanceAt(double t) {
        double v = speed, v0 = 0.5 * v;
        if (t <= 0.0) return 0.0;
        if (t < ACCEL_T) return v0 * t + (v - v0) * t * t / (2.0 * ACCEL_T);
        return v0 * ACCEL_T + (v - v0) * ACCEL_T * 0.5 + v * (t - ACCEL_T);
    }

    public int ticks() {
        double L = length(), v = speed, v0 = 0.5 * v, sA = v0 * ACCEL_T + (v - v0) * ACCEL_T * 0.5;
        if (L <= sA) {
            double a = (v - v0) / (2.0 * ACCEL_T);
            return Math.max(1, (int) Math.ceil((-v0 + Math.sqrt(v0 * v0 + 4.0 * a * L)) / (2.0 * a)));
        }
        return (int) Math.ceil(ACCEL_T + (L - sA) / v);
    }

    public double profile(double s) {
        if (alt == null || alt.length == 0) return 80.0;
        double f = Math.max(0.0, s) / STEP;
        int i = Math.min((int) f, alt.length - 1);
        double u = Math.min(1.0, f - i);
        double p0 = alt[Math.max(0, i - 1)], p1 = alt[i], p2 = alt[Math.min(alt.length - 1, i + 1)], p3 = alt[Math.min(alt.length - 1, i + 2)];
        return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
    }

    public Vec3 position(Flight f, double gameTime) {
        if (dr != null) return dr.at(Math.min(gameTime, (double) f.endTime()));
        double t = Mth.clamp(gameTime - f.start(), 0.0, (double) f.duration());
        double L = length();
        double s = Math.min(L, distanceAt(t));
        if (t >= f.duration()) s = L;
        double[] g = ground(s);
        double y = profile(s);
        if (s < CLIMB_D) {
            double k = smooth(s / CLIMB_D);
            y = (1.0 - k) * (f.oy() + s * Math.tan(Math.toRadians(PITCH_DEG))) + k * y;
        }
        double dStart = Math.max(0.0, L - DIVE_D);
        if (s > dStart && L > 1.0) {
            double u = Mth.clamp((s - dStart) / Math.max(1.0, L - dStart), 0.0, 1.0);
            double k = u * u;
            y = (1.0 - k) * (y + POP * Math.sin(Math.PI * u)) + k * f.endY();
        }
        return new Vec3(g[0], y, g[1]);
    }

    private static double smooth(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    public short[] sampleAltitude(ServerLevel level) {
        double L = length();
        int n = (int) Math.floor(L / STEP) + 2;
        double[] ground = new double[n];
        for (int i = 0; i < n; i++) {
            double[] p = ground(i * (double) STEP);
            ground[i] = groundAt(level, Mth.floor(p[0]), Mth.floor(p[1]));
        }
        double top = level.getMaxBuildHeight() - 8;
        short[] a = new short[n];
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double m = ground[i];
            for (int k = Math.max(0, i - 1); k <= Math.min(n - 1, i + 1); k++) m = Math.max(m, ground[k]);
            raw[i] = m + Balance.CRUISE_ALT;
        }
        for (int i = 0; i < n; i++) {
            double v = (raw[Math.max(0, i - 1)] + 2.0 * raw[i] + raw[Math.min(n - 1, i + 1)]) / 4.0;
            v = Math.max(v, ground[i] + Balance.CRUISE_ALT * 0.5);
            a[i] = (short) Mth.clamp(Math.round(v), level.getMinBuildHeight(), (long) top);
        }
        return a;
    }

    public static int groundAt(ServerLevel level, int x, int z) {
        try {
            if (level.hasChunkAt(new BlockPos(x, 0, z))) return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            var src = level.getChunkSource();
            return src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState());
        } catch (RuntimeException e) {
            return level.getSeaLevel();
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("ox", ox); t.putDouble("oz", oz); t.putDouble("fx", fx); t.putDouble("fz", fz);
        t.putIntArray("xs", xs); t.putIntArray("zs", zs); t.putFloat("v", speed);
        int[] a = new int[alt == null ? 0 : alt.length];
        for (int i = 0; i < a.length; i++) a[i] = alt[i];
        t.putIntArray("alt", a);
        return t;
    }

    public static CruiseRoute load(CompoundTag t) {
        int[] a = t.getIntArray("alt");
        short[] s = new short[a.length];
        for (int i = 0; i < a.length; i++) s[i] = (short) a[i];
        int[] xs = t.getIntArray("xs"), zs = t.getIntArray("zs");
        if (xs.length == 0 || xs.length != zs.length) return null;
        return new CruiseRoute(t.getDouble("ox"), t.getDouble("oz"), t.getDouble("fx"), t.getDouble("fz"), xs, zs, t.getFloat("v"), s);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(dr != null);
        if (dr != null) { dr.write(buf); return; }
        buf.writeDouble(ox); buf.writeDouble(oz); buf.writeDouble(fx); buf.writeDouble(fz);
        buf.writeVarInt(xs.length);
        for (int i = 0; i < xs.length; i++) { buf.writeInt(xs[i]); buf.writeInt(zs[i]); }
        buf.writeFloat(speed);
        buf.writeVarInt(alt == null ? 0 : alt.length);
        if (alt != null) for (short v : alt) buf.writeShort(v);
    }

    public static CruiseRoute read(FriendlyByteBuf buf) {
        if (buf.readBoolean()) return deadReckoned(Dr.read(buf));
        double ox = buf.readDouble(), oz = buf.readDouble(), fx = buf.readDouble(), fz = buf.readDouble();
        int n = buf.readVarInt();
        if (n < 1 || n > Balance.CRUISE_WAYPOINTS + 1) throw new IllegalArgumentException("cruise route: bad point count " + n);
        int[] xs = new int[n], zs = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = buf.readInt(); zs[i] = buf.readInt(); }
        float v = buf.readFloat();
        int m = buf.readVarInt();
        if (m < 0 || m > 4096) throw new IllegalArgumentException("cruise route: bad profile length " + m);
        short[] a = new short[m];
        for (int i = 0; i < m; i++) a[i] = buf.readShort();
        return new CruiseRoute(ox, oz, fx, fz, xs, zs, v, a);
    }
}
