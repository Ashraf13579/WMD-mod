package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCraftShield {

    private ClientCraftShield() {}

    private record Cell(Vec3 c, Vec3[] k) {}
    private static List<Cell> cells;

    private static final class Entry {
        double cx, cy, cz, ax, ay, az;
        int pct;
        long seen;
        float fade;
    }

    private record Ripple(UUID ship, Vec3 dir, long t0, float power) {}
    private static final class Shard { Vec3 p0, v, axis; double w; Vec3[] off; boolean glint; }
    private record Shatter(long t0, List<Shard> shards) {}

    private static final Map<UUID, Entry> LIVE = Statics.client(new HashMap<>());
    private static final List<Ripple> RIPPLES = Statics.client(new ArrayList<>());
    private static final List<Shatter> SHATTERS = Statics.client(new ArrayList<>());
    private static Level seenLevel;

    static void receive(CraftShieldPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Entry e = LIVE.get(p.ship());
        if (p.kind() != CraftShieldPayload.SHATTER) {
            if (e == null) { e = new Entry(); LIVE.put(p.ship(), e); }
            e.cx = p.cx(); e.cy = p.cy(); e.cz = p.cz(); e.ax = p.ax(); e.ay = p.ay(); e.az = p.az(); e.pct = p.pct(); e.seen = now;
        }
        Vec3 u = new Vec3(p.ux(), p.uy(), p.uz());
        Vec3 dir = u.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : u.normalize();
        Matrix4d m = ShipFrame.craftMatrix(mc.level, p.ship(), 1.0F);
        Vec3 at = m == null ? null : world(m, p.cx() + dir.x * p.ax(), p.cy() + dir.y * p.ay(), p.cz() + dir.z * p.az());
        if (p.kind() == CraftShieldPayload.HIT) {
            if (RIPPLES.size() >= 16) RIPPLES.remove(0);
            RIPPLES.add(new Ripple(p.ship(), dir, now, Math.max(0.3F, p.power())));
            if (at != null) mc.level.playLocalSound(at.x, at.y, at.z, WmdSounds.SHIELD_HIT.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F, false);
        } else if (p.kind() == CraftShieldPayload.SHATTER) {
            LIVE.remove(p.ship());
            RIPPLES.removeIf(r -> r.ship().equals(p.ship()));
            if (m != null && !ClientCloak.craftHidden(p.ship())) { if (SHATTERS.size() >= 6) SHATTERS.remove(0); SHATTERS.add(new Shatter(now, shards(m, p, dir, p.ship().getLeastSignificantBits() ^ now))); }
            if (at != null) {
                mc.level.playLocalSound(at.x, at.y, at.z, WmdSounds.SHIELD_SHATTER.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F, false);
                mc.level.playLocalSound(at.x, at.y, at.z, WmdSounds.FF_GLASS_RAIN.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.2F, false);
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != seenLevel) { seenLevel = mc.level; LIVE.clear(); RIPPLES.clear(); SHATTERS.clear(); }
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<UUID, Entry>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            Entry e = it.next().getValue();
            boolean fresh = now - e.seen <= 3L * Balance.CSH_SYNC_TICKS && now >= e.seen;
            e.fade = Mth.clamp(e.fade + (fresh ? 0.12F : -0.08F), 0.0F, 1.0F);
            if (!fresh && e.fade <= 0.0F) it.remove();
        }
        RIPPLES.removeIf(r -> now - r.t0() > Balance.CSH_RIPPLE_TICKS || now < r.t0());
        SHATTERS.removeIf(s -> now - s.t0() > Balance.CSH_SHARD_TICKS || now < s.t0());
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (LIVE.isEmpty() && SHATTERS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<UUID, Entry> en : LIVE.entrySet()) {
            Entry e = en.getValue();
            if (e.fade < 0.01F || ClientCloak.craftHidden(en.getKey())) continue;
            Matrix4d m = ShipFrame.craftMatrix(mc.level, en.getKey(), pt);
            if (m == null) continue;
            any |= bubble(bb, cam, m, en.getKey(), e, now);
        }
        for (Shatter s : SHATTERS) any |= shatter(bb, cam, s, now);
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static final Vector3d TMP_V = new Vector3d();
    private static final double[] WK_X = new double[8], WK_Y = new double[8], WK_Z = new double[8];

    private static Vec3 world(Matrix4d m, double x, double y, double z) {
        Vector3d v = m.transformPosition(TMP_V.set(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    private static boolean bubble(BufferBuilder bb, Vec3 cam, Matrix4d m, UUID ship, Entry e, double now) {
        m.transformPosition(TMP_V.set(e.cx, e.cy, e.cz));
        double cx = TMP_V.x, cy = TMP_V.y, cz = TMP_V.z;
        double cdx = cx - cam.x, cdy = cy - cam.y, cdz = cz - cam.z;
        if (cdx * cdx + cdy * cdy + cdz * cdz > Balance.CSH_FX_RANGE * (double) Balance.CSH_FX_RANGE) return false;
        long tick = (long) now;
        boolean low = e.pct < Balance.CSH_LOW_PCT;
        float flick = low ? 0.6F + 0.4F * (float) hash(7, tick / 2) : 1.0F;
        List<Ripple> rips = null;
        for (Ripple r : RIPPLES) if (r.ship().equals(ship)) { if (rips == null) rips = new ArrayList<>(); rips.add(r); }
        double rr = Math.max(e.ax, Math.max(e.ay, e.az));
        List<Cell> cs = cells();
        for (int i = 0; i < cs.size(); i++) {
            Cell cell = cs.get(i);
            Vec3 n = cell.c();
            if (low && hash(i, tick / 3) < 0.1) continue;
            double add = 0.0;
            if (rips != null) for (Ripple rp : rips) {
                double age = now - rp.t0();
                if (age < 0 || age > Balance.CSH_RIPPLE_TICKS) continue;
                double s = Math.acos(Mth.clamp(n.dot(rp.dir()), -1.0, 1.0)) * rr;
                double front = age * 1.4, fall = 1.0 - age / Balance.CSH_RIPPLE_TICKS;
                for (int ring = 0; ring < 2; ring++) {
                    double fr = front - ring * 4.0;
                    if (fr < 0) continue;
                    double x = (s - fr) / 1.6;
                    add += rp.power() * fall * Math.exp(-x * x) * (0.95 - ring * 0.3);
                }
                if (s < 3.0 && age < 10) add += (1.0 - age / 10.0) * 1.4;
            }
            m.transformPosition(TMP_V.set(e.cx + n.x * e.ax, e.cy + n.y * e.ay, e.cz + n.z * e.az));
            double pcx = TMP_V.x, pcy = TMP_V.y, pcz = TMP_V.z;
            double vx = pcx - cam.x, vy = pcy - cam.y, vz = pcz - cam.z;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double nwx = pcx - cx, nwy = pcy - cy, nwz = pcz - cz;
            double nl = Math.sqrt(nwx * nwx + nwy * nwy + nwz * nwz);
            double facing = vl < 1e-6 || nl < 1e-6 ? 1.0 : Math.abs((nwx * vx + nwy * vy + nwz * vz) / (nl * vl));
            double rim = 1.0 - facing;
            float a = (float) Math.min(0.85, (0.012 + 0.16 * rim * rim * rim + 0.4 * add) * e.fade * flick);
            float fa = (float) Math.min(1.0, (0.03 + 0.22 * rim * rim + 0.6 * add) * e.fade * flick);
            if (a < 0.006F && fa < 0.01F) continue;
            float w = (float) Math.min(1.0, add * 0.7);
            float cr = 0.3F + 0.65F * w, cg = 0.68F + 0.3F * w;
            Vec3[] k = cell.k();
            int mm = k.length;
            for (int j = 0; j < mm; j++) {
                m.transformPosition(TMP_V.set(e.cx + k[j].x * e.ax, e.cy + k[j].y * e.ay, e.cz + k[j].z * e.az));
                WK_X[j] = TMP_V.x; WK_Y[j] = TMP_V.y; WK_Z[j] = TMP_V.z;
            }
            for (int j = 0; j < mm; j++) {
                int j2 = (j + 1) % mm;
                ClientBlastGeo.v(bb, cam, pcx, pcy, pcz, cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, WK_X[j], WK_Y[j], WK_Z[j], cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, WK_X[j2], WK_Y[j2], WK_Z[j2], cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, WK_X[j2], WK_Y[j2], WK_Z[j2], cr, cg, 1.0F, a);
            }
            float faIn = fa * 0.35F;
            for (int j = 0; j < mm; j++) {
                int j2 = (j + 1) % mm;
                double p1x = WK_X[j], p1y = WK_Y[j], p1z = WK_Z[j];
                double p2x = WK_X[j2], p2y = WK_Y[j2], p2z = WK_Z[j2];
                double i1x = p1x + (pcx - p1x) * 0.09, i1y = p1y + (pcy - p1y) * 0.09, i1z = p1z + (pcz - p1z) * 0.09;
                double i2x = p2x + (pcx - p2x) * 0.09, i2y = p2y + (pcy - p2y) * 0.09, i2z = p2z + (pcz - p2z) * 0.09;
                ClientBlastGeo.v(bb, cam, p1x, p1y, p1z, 0.4F, 0.82F, 1.0F, fa);
                ClientBlastGeo.v(bb, cam, p2x, p2y, p2z, 0.4F, 0.82F, 1.0F, fa);
                ClientBlastGeo.v(bb, cam, i2x, i2y, i2z, 0.4F, 0.82F, 1.0F, faIn);
                ClientBlastGeo.v(bb, cam, i1x, i1y, i1z, 0.4F, 0.82F, 1.0F, faIn);
            }
        }
        return true;
    }

    private static boolean shatter(BufferBuilder bb, Vec3 cam, Shatter s, double now) {
        double t = now - s.t0();
        if (t < 0 || t > Balance.CSH_SHARD_TICKS) return false;
        double life = t / Balance.CSH_SHARD_TICKS;
        float fade = (float) (life < 0.4 ? 1.0 : Math.max(0.0, 1.0 - (life - 0.4) / 0.6));
        float flash = (float) Math.max(0.0, 1.0 - t / 5.0);
        for (Shard sh : s.shards()) {
            Vec3 p = sh.p0.add(sh.v.scale(t)).add(0.0, -0.012 * t * t, 0.0);
            double ang = sh.w * t, ca = Math.cos(ang), sa = Math.sin(ang);
            int mm = sh.off.length;
            Vec3[] wp = new Vec3[mm];
            for (int j = 0; j < mm; j++) {
                Vec3 o = sh.off[j];
                wp[j] = p.add(o.scale(ca).add(sh.axis.cross(o).scale(sa)).add(sh.axis.scale(sh.axis.dot(o) * (1.0 - ca))));
            }
            float fill = (sh.glint ? 0.4F : 0.06F) * fade + 0.35F * flash;
            float cr = sh.glint ? 0.9F : 0.45F, cg = sh.glint ? 0.97F : 0.8F;
            for (int j = 0; j < mm; j++) {
                Vec3 p1 = wp[j], p2 = wp[(j + 1) % mm];
                ClientBlastGeo.v(bb, cam, p.x, p.y, p.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p1.x, p1.y, p1.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, fill);
            }
            float ea = Math.min(1.0F, 0.6F * fade + 0.4F * flash);
            for (int j = 0; j < mm; j++) {
                Vec3 p1 = wp[j], p2 = wp[(j + 1) % mm];
                Vec3 i1 = p1.add(p.subtract(p1).scale(0.14)), i2 = p2.add(p.subtract(p2).scale(0.14));
                ClientBlastGeo.quad(bb, cam, p1, p2, i2, i1, 0.4F, 0.85F, 1.0F, ea, 0.4F, 0.85F, 1.0F, ea * 0.3F);
            }
        }
        return true;
    }

    private static List<Shard> shards(Matrix4d m, CraftShieldPayload p, Vec3 dir, long seed) {
        Random rnd = new Random(seed);
        List<Shard> out = new ArrayList<>();
        Vec3 c = world(m, p.cx(), p.cy(), p.cz());
        double rr = Math.max(p.ax(), Math.max(p.ay(), p.az()));
        for (Cell cell : cells()) {
            if (rnd.nextDouble() < 0.15) continue;
            Vec3 n = cell.c();
            Vec3 cu = world(m, p.cx() + n.x * p.ax(), p.cy() + n.y * p.ay(), p.cz() + n.z * p.az());
            Shard sh = new Shard();
            sh.p0 = cu;
            Vec3[] k = cell.k();
            sh.off = new Vec3[k.length];
            double shrink = 0.8 + rnd.nextDouble() * 0.15;
            for (int j = 0; j < k.length; j++) sh.off[j] = world(m, p.cx() + k[j].x * p.ax(), p.cy() + k[j].y * p.ay(), p.cz() + k[j].z * p.az()).subtract(cu).scale(shrink);
            double s = Math.acos(Mth.clamp(n.dot(dir), -1.0, 1.0)) * rr;
            double push = (0.05 + 0.5 * Math.exp(-s / 6.0)) * (0.5 + rnd.nextDouble() * 0.8);
            Vec3 out0 = cu.subtract(c);
            Vec3 nrm = out0.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 1.0, 0.0) : out0.normalize();
            sh.v = nrm.scale(push).add(rnd.nextGaussian() * 0.04, rnd.nextDouble() * 0.03, rnd.nextGaussian() * 0.04);
            sh.axis = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            sh.w = Math.toRadians(2.0 + rnd.nextDouble() * 7.0) * (rnd.nextBoolean() ? 1 : -1);
            sh.glint = rnd.nextDouble() < 0.08;
            out.add(sh);
        }
        return out;
    }

    private static List<Cell> cells() {
        if (cells == null) cells = buildCells();
        return cells;
    }

    private static List<Cell> buildCells() {
        double t = (1.0 + Math.sqrt(5.0)) / 2.0;
        List<Vec3> v = new ArrayList<>();
        double[][] base = { {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1} };
        for (double[] b : base) v.add(new Vec3(b[0], b[1], b[2]).normalize());
        List<int[]> f = new ArrayList<>(List.of(new int[] {0, 11, 5}, new int[] {0, 5, 1}, new int[] {0, 1, 7}, new int[] {0, 7, 10}, new int[] {0, 10, 11},
                new int[] {1, 5, 9}, new int[] {5, 11, 4}, new int[] {11, 10, 2}, new int[] {10, 7, 6}, new int[] {7, 1, 8}, new int[] {3, 9, 4}, new int[] {3, 4, 2},
                new int[] {3, 2, 6}, new int[] {3, 6, 8}, new int[] {3, 8, 9}, new int[] {4, 9, 5}, new int[] {2, 4, 11}, new int[] {6, 2, 10}, new int[] {8, 6, 7},
                new int[] {9, 8, 1}));
        for (int level = 0; level < 3; level++) {
            Map<Long, Integer> mid = new HashMap<>();
            List<int[]> nf = new ArrayList<>();
            for (int[] tri : f) {
                int a = midpoint(v, mid, tri[0], tri[1]), b = midpoint(v, mid, tri[1], tri[2]), c = midpoint(v, mid, tri[2], tri[0]);
                nf.add(new int[] {tri[0], a, c}); nf.add(new int[] {tri[1], b, a}); nf.add(new int[] {tri[2], c, b}); nf.add(new int[] {a, b, c});
            }
            f = nf;
        }
        List<List<Integer>> around = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) around.add(new ArrayList<>());
        for (int i = 0; i < f.size(); i++) for (int k : f.get(i)) around.get(k).add(i);
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) {
            Vec3 n = v.get(i);
            Vec3 t1 = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), t2 = n.cross(t1);
            List<Vec3> cs = new ArrayList<>();
            for (int fi : around.get(i)) {
                int[] tri = f.get(fi);
                cs.add(v.get(tri[0]).add(v.get(tri[1])).add(v.get(tri[2])).normalize());
            }
            cs.sort((p, q) -> Double.compare(Math.atan2(p.subtract(n).dot(t2), p.subtract(n).dot(t1)), Math.atan2(q.subtract(n).dot(t2), q.subtract(n).dot(t1))));
            out.add(new Cell(n, cs.toArray(new Vec3[0])));
        }
        return out;
    }

    private static int midpoint(List<Vec3> v, Map<Long, Integer> mid, int a, int b) {
        long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
        Integer m = mid.get(key);
        if (m != null) return m;
        v.add(v.get(a).add(v.get(b)).normalize());
        mid.put(key, v.size() - 1);
        return v.size() - 1;
    }

    private static double hash(int a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
