package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

final class SeaChart {

    private SeaChart() {}

    static final int SEA = 0x40000000, LAND = 0x20000000, OLD = 0x10000000;
    static final int V_OK = 0, V_UNKNOWN = 1, V_BAD = 2;
    private static final double LEG_STEP = 8.0;
    static final int LEG_SAMPLES_MAX = 512;
    private static final int MAX_MARKS = 24;

    record Leg(int min, boolean unknown, boolean land) {}

    static Leg leg(ServerLevel sl, double x0, double z0, double x1, double z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = Math.max(1, Math.min(LEG_SAMPLES_MAX, (int) Math.ceil(len / LEG_STEP))), min = Integer.MAX_VALUE;
        boolean unknown = false, land = false;
        for (int i = 0; i <= n; i++) {
            int d = TerrainAtlas.depthAt(sl, Mth.floor(x0 + (x1 - x0) * i / n), Mth.floor(z0 + (z1 - z0) * i / n));
            if (d < 0) unknown = true;
            else if (d == 0) { if (i > 0) land = true; }
            else min = Math.min(min, d);
        }
        return new Leg(min, unknown, land);
    }

    static double need(boolean sub, int legDepth, double keel, int margin) { return (sub ? legDepth + keel : keel) + margin; }

    static int verdict(Leg l, double need) {
        if (l.land() || (l.min() != Integer.MAX_VALUE && l.min() < need)) return V_BAD;
        return l.unknown() ? V_UNKNOWN : V_OK;
    }

    static int firstBadLeg(ServerLevel sl, Vec3 at, List<SeaPilot.Wp> route, boolean sub, double keel, int margin) {
        double px = at.x, pz = at.z;
        for (int i = 0; i < route.size(); i++) {
            SeaPilot.Wp w = route.get(i);
            if (verdict(leg(sl, px, pz, w.x(), w.z()), need(sub, w.depth(), keel, margin)) == V_BAD) return i;
            px = w.x(); pz = w.z();
        }
        return -1;
    }

    static void capDepths(ServerLevel sl, Vec3 at, SeaPilot p, double keel, int margin, int crush) {
        double px = at.x, pz = at.z;
        for (int i = 0; i < p.route.size(); i++) {
            SeaPilot.Wp w = p.route.get(i);
            Leg l = leg(sl, px, pz, w.x(), w.z());
            int d = w.depth();
            if (l.min() != Integer.MAX_VALUE) d = Math.min(d, (int) Math.floor(l.min() - keel - margin));
            if (crush > 0) d = Math.min(d, (int) Math.floor(Balance.SP_CRUSH_CAP * crush));
            d = Math.max(0, d);
            if (d != w.depth()) p.route.set(i, new SeaPilot.Wp(w.x(), w.z(), w.speed(), d));
            px = w.x(); pz = w.z();
        }
        if (crush > 0) {
            p.holdDepth = Math.min(p.holdDepth, (int) Math.floor(Balance.SP_CRUSH_CAP * crush));
            p.stDepth = Math.min(p.stDepth, (int) Math.floor(Balance.SP_CRUSH_CAP * crush));
        }
    }

    static int[] tile(ServerPlayer sp, int x0, int z0, int cell, int w, int h) { return area(sp.serverLevel(), x0, z0, cell, w, h, sp.getX(), sp.getZ()); }

    static int[] area(ServerLevel sl, int x0, int z0, int cell, int w, int h, double cx, double cz) {
        int[] out = new int[w * h], raw = new int[w * h];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int bx = x0 + i * cell + cell / 2, bz = z0 + j * cell + cell / 2;
            if (Math.abs(bx - cx) > Balance.CHART_RANGE || Math.abs(bz - cz) > Balance.CHART_RANGE) continue;
            int d = TerrainAtlas.depthAt(sl, bx, bz), e = TerrainAtlas.exploredAt(sl, bx, bz);
            raw[j * w + i] = e;
            if (d > 0) out[j * w + i] = SEA | d;
            else if (e != 0) out[j * w + i] = (d == 0 ? LAND : OLD) | (e >>> 8);
        }
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int v = out[j * w + i];
            if ((v & (LAND | OLD)) == 0) continue;
            int e = raw[j * w + i], n = j > 0 && raw[(j - 1) * w + i] != 0 ? raw[(j - 1) * w + i] : e;
            int hv = e & 0xFF, hn = n & 0xFF;
            float k = hv > hn ? 1.0F : hv < hn ? 0.71F : 0.86F;
            int rgb = v & 0xFFFFFF, r = (int) (((rgb >> 16) & 0xFF) * k), g = (int) (((rgb >> 8) & 0xFF) * k), b = (int) ((rgb & 0xFF) * k);
            out[j * w + i] = (v & (LAND | OLD)) | r << 16 | g << 8 | b;
        }
        return out;
    }

    static CompoundTag state(ServerLevel sl, BlockPos source, UUID ship, HelmStationBlockEntity helm, SeaPilot.Ctx c, String msg) {
        CompoundTag t = new CompoundTag();
        t.putString("msg", msg == null ? "" : msg);
        Vec3 at = c != null ? c.at() : Vec3.atCenterOf(source);
        t.putDouble("x", at.x); t.putDouble("z", at.z);
        t.putBoolean("craft", ship != null);
        t.putBoolean("helm", helm != null);
        if (c != null) {
            t.putDouble("hdg", c.heading()); t.putDouble("v", c.fwdSpeed()); t.putDouble("dep", c.depth()); t.putBoolean("sub", c.sub());
            t.putInt("crush", c.crush()); t.putDouble("keel", c.sub() ? c.keelBelow() : c.draft()); t.putBoolean("pow", c.power());
        }
        if (helm != null) {
            SeaPilot p = helm.pilot;
            p.writeSettings(t);
            t.putInt("why", p.reasonShown(sl.getGameTime()));
            t.putBoolean("cap", p.capped);
            if (c != null) {
                int mar = p.margin(c.sub());
                t.putInt("marEff", mar);
                double keel = c.sub() ? c.keelBelow() : c.draft(), px = at.x, pz = at.z;
                int[] lv = new int[p.route.size() * 2];
                for (int i = 0; i < p.route.size(); i++) {
                    SeaPilot.Wp w = p.route.get(i);
                    Leg l = leg(sl, px, pz, w.x(), w.z());
                    lv[2 * i] = verdict(l, need(c.sub(), w.depth(), keel, mar));
                    lv[2 * i + 1] = l.min() == Integer.MAX_VALUE ? -1 : l.min();
                    px = w.x(); pz = w.z();
                }
                t.put("lv", new IntArrayTag(lv));
            }
            t.put("trk", new IntArrayTag(p.trackXZ()));
            profile(sl, at, p, t);
        }
        if (ship != null) marks(sl, ship, t);
        return t;
    }

    private static void profile(ServerLevel sl, Vec3 at, SeaPilot p, CompoundTag t) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[] { at.x, at.z });
        if (!p.route.isEmpty() && (p.mode == SeaPilot.ROUTE || p.mode == SeaPilot.OFF)) for (SeaPilot.Wp w : p.route) pts.add(new double[] { w.x(), w.z() });
        else if (p.mode == SeaPilot.STATION) pts.add(new double[] { p.stX, p.stZ });
        else { double r = Math.toRadians(p.holdCourse); pts.add(new double[] { at.x + Math.sin(r) * 1000.0, at.z - Math.cos(r) * 1000.0 }); }
        double[] cum = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) cum[i] = cum[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
        double len = cum[cum.length - 1];
        int[] pf = new int[PROFILE_N];
        int seg = 1;
        for (int k = 0; k < PROFILE_N; k++) {
            double d = len * k / (PROFILE_N - 1);
            while (seg < pts.size() - 1 && d > cum[seg]) seg++;
            double[] a = pts.get(seg - 1), b = pts.get(seg);
            double f = cum[seg] - cum[seg - 1] < 1.0E-6 ? 0.0 : (d - cum[seg - 1]) / (cum[seg] - cum[seg - 1]);
            pf[k] = TerrainAtlas.depthAt(sl, Mth.floor(a[0] + (b[0] - a[0]) * f), Mth.floor(a[1] + (b[1] - a[1]) * f));
        }
        int[] ends = new int[cum.length - 1];
        for (int i = 1; i < cum.length; i++) ends[i - 1] = (int) Math.round(cum[i]);
        t.put("pf", new IntArrayTag(pf));
        t.putDouble("pfL", len);
        t.put("pfB", new IntArrayTag(ends));
    }

    static final int PROFILE_N = 96;

    private static void marks(ServerLevel sl, UUID ship, CompoundTag t) {
        ListTag con = new ListTag(), fld = new ListTag();
        List<BlockPos> bes = SableBridge.blockEntitiesOn(sl, ship);
        int seen = 0;
        for (BlockPos p : bes) {
            if (++seen > 4096 || (con.size() >= MAX_MARKS && fld.size() >= MAX_MARKS)) break;
            BlockEntity be = sl.getBlockEntity(p);
            if (!(be instanceof SonarConsoleBlockEntity s)) continue;
            for (CompoundTag r : s.fixRows()) {
                if (con.size() >= MAX_MARKS) break;
                CompoundTag m = new CompoundTag();
                m.putInt("x", r.getInt("x")); m.putInt("z", r.getInt("z")); m.putString("n", r.getString("n")); m.putInt("k", r.getBoolean("s") ? 2 : 1);
                con.add(m);
            }
            for (CompoundTag r : s.contactRows()) {
                if (con.size() >= MAX_MARKS) break;
                if (!r.getBoolean("f")) continue;
                CompoundTag m = new CompoundTag();
                m.putInt("x", r.getInt("fx")); m.putInt("z", r.getInt("fz")); m.putString("n", ""); m.putInt("k", 0);
                con.add(m);
            }
            for (CompoundTag r : s.surveyRows()) {
                if (fld.size() >= MAX_MARKS) break;
                CompoundTag m = new CompoundTag();
                m.putInt("x", r.getInt("x")); m.putInt("z", r.getInt("z")); m.putInt("r", r.getInt("r")); m.putInt("n", r.getInt("n"));
                fld.add(m);
            }
        }
        t.put("con", con);
        t.put("fld", fld);
    }
}
