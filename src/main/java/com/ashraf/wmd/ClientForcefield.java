package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
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
public final class ClientForcefield {

    private ClientForcefield() {}

    private record Cell(Vec3 c, Vec3[] k) {}
    private static List<Cell> cells;

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
        for (int level = 0; level < 4; level++) {
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
            if (n.y < -0.03) continue;
            Vec3 t1 = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), t2 = n.cross(t1);
            List<Vec3> cs = new ArrayList<>();
            for (int fi : around.get(i)) {
                int[] tri = f.get(fi);
                cs.add(v.get(tri[0]).add(v.get(tri[1])).add(v.get(tri[2])).normalize());
            }
            cs.sort((p, q) -> Double.compare(Math.atan2(p.subtract(n).dot(t2), p.subtract(n).dot(t1)), Math.atan2(q.subtract(n).dot(t2), q.subtract(n).dot(t1))));
            Vec3[] k = new Vec3[cs.size()];
            for (int j = 0; j < k.length; j++) { Vec3 p = cs.get(j); k[j] = new Vec3(p.x, Math.max(0.0, p.y), p.z); }
            out.add(new Cell(new Vec3(n.x, Math.max(0.0, n.y), n.z), k));
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

    private static final Map<BlockPos, ForcefieldBlockEntity> LIVE = new HashMap<>();
    private static final Map<BlockPos, Float> FADE = new HashMap<>();
    private static Level seenLevel;

    static void track(ForcefieldBlockEntity be) { LIVE.put(be.getBlockPos().immutable(), be); }

    private record Ripple(BlockPos gen, Vec3 dir, long t0, float power) {}
    private record Crack(BlockPos gen, Vec3 c, int r, Vec3 dir, long t0, List<double[][]> lines) {}
    private static final class Shard {
        Vec3 p0, v, axis; double w; Vec3[] off; boolean glint;
    }
    private record Shatter(Vec3 c, int r, long t0, List<Shard> shards) {}
    private record Due(long tick, Runnable run) {}

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final List<Crack> CRACKS = new ArrayList<>();
    private static final List<Shatter> SHATTERS = new ArrayList<>();
    private static final List<Due> SOUNDS = new ArrayList<>();

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); FADE.clear(); RIPPLES.clear(); CRACKS.clear(); SHATTERS.clear(); SOUNDS.clear(); seenLevel = null; return; }
        if (mc.level != seenLevel) { seenLevel = mc.level; LIVE.clear(); FADE.clear(); RIPPLES.clear(); CRACKS.clear(); SHATTERS.clear(); SOUNDS.clear(); }
        long now = mc.level.getGameTime();
        for (Forcefield.Fx p; (p = Forcefield.PENDING.poll()) != null; ) arrive(mc, p, now);
        LIVE.values().removeIf(be -> be.isRemoved() || be.getLevel() != mc.level);
        for (Map.Entry<BlockPos, ForcefieldBlockEntity> e : LIVE.entrySet()) {
            float target = shown(e.getValue(), now) ? 1.0F : 0.0F;
            float f = FADE.getOrDefault(e.getKey(), 0.0F);
            FADE.put(e.getKey(), f + (target - f) * (target > f ? 0.2F : 0.08F));
        }
        FADE.keySet().removeIf(k -> !LIVE.containsKey(k));
        RIPPLES.removeIf(r -> now - r.t0() > 40);
        CRACKS.removeIf(c -> now - c.t0() > Balance.FF_CRACK_TICKS + 10);
        SHATTERS.removeIf(s -> now - s.t0() > Balance.FF_SHARD_TICKS);
        for (Iterator<Due> it = SOUNDS.iterator(); it.hasNext(); ) {
            Due d = it.next();
            if (now >= d.tick()) { it.remove(); d.run().run(); }
        }
    }

    private static boolean shown(ForcefieldBlockEntity be, long now) {
        int st = be.state();
        return st == ForcefieldBlockEntity.UP || st == ForcefieldBlockEntity.CRACK || st == ForcefieldBlockEntity.BOOT && be.shownTicks(now) >= 140;
    }

    private static void arrive(Minecraft mc, Forcefield.Fx p, long now) {
        Vec3 c = new Vec3(p.cx(), p.cy(), p.cz()), dir = new Vec3(p.dx(), p.dy(), p.dz()).normalize();
        Vec3 at = c.add(dir.scale(p.radius()));
        Level lv = mc.level;
        switch (p.kind()) {
            case Forcefield.FX_HIT -> {
                RIPPLES.add(new Ripple(p.gen(), dir, now, Math.max(0.25F, p.power())));
                lv.playLocalSound(at.x, at.y, at.z, WmdSounds.FF_HIT.get(), SoundSource.BLOCKS, 10.0F, 1.0F, false);
            }
            case Forcefield.FX_TOUCH -> {
                RIPPLES.add(new Ripple(p.gen(), dir, now, 0.2F));
                lv.playLocalSound(at.x, at.y, at.z, WmdSounds.FF_TOUCH.get(), SoundSource.BLOCKS, 2.0F, 1.0F, false);
            }
            case Forcefield.FX_CRACK -> {
                CRACKS.add(new Crack(p.gen(), c, p.radius(), dir, now, crackLines(dir, p.radius(), p.gen().asLong() ^ now)));
                lv.playLocalSound(at.x, at.y, at.z, WmdSounds.FF_CRACK.get(), SoundSource.BLOCKS, 12.0F, 1.0F, false);
                Random rnd = new Random(now);
                for (int i = 1; i <= 6; i++) {
                    final Vec3 q = c.add(dir.add(rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.4).normalize().scale(p.radius()));
                    final float pitch = 0.85F + rnd.nextFloat() * 0.3F;
                    SOUNDS.add(new Due(now + i * Balance.FF_CRACK_TICKS / 7, () -> {
                        if (Minecraft.getInstance().level != null) Minecraft.getInstance().level.playLocalSound(q.x, q.y, q.z, WmdSounds.FF_CRACKLE.get(), SoundSource.BLOCKS, 8.0F, pitch, false);
                    }));
                }
            }
            case Forcefield.FX_SHATTER -> {
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                double d = cam.distanceTo(c);
                if (d < Balance.FF_FX_RANGE + p.radius()) SHATTERS.add(new Shatter(c, p.radius(), now, shards(c, p.radius(), dir, p.gen().asLong() ^ now)));
                CRACKS.removeIf(k -> k.gen().equals(p.gen()));
                double shell = Math.max(0.0, d - p.radius());
                long delay = (long) (shell / Balance.FF_SOUND_SPEED);
                float vol = (float) Mth.clamp(1.0 - shell / 6000.0, 0.15, 1.0);
                SOUNDS.add(new Due(now + delay, () -> {
                    ClientRailgun.flat((shell < 200.0 ? WmdSounds.FF_SHATTER : WmdSounds.FF_SHATTER_FAR).get(), vol, 1.0F);
                }));
                if (shell < 64.0) {
                    Random rnd = new Random(now ^ 77);
                    for (int i = 0; i < 3; i++) {
                        final Vec3 q = c.add(new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.6, rnd.nextGaussian()).normalize().scale(p.radius() * 0.8));
                        final float pitch = 0.9F + rnd.nextFloat() * 0.2F;
                        SOUNDS.add(new Due(now + delay + 12 + i * 22, () -> {
                            if (Minecraft.getInstance().level != null) Minecraft.getInstance().level.playLocalSound(q.x, q.y, q.z, WmdSounds.FF_GLASS_RAIN.get(), SoundSource.BLOCKS, 6.0F, pitch, false);
                        }));
                    }
                }
            }
            default -> { }
        }
    }

    private static List<double[][]> crackLines(Vec3 dir, int r, long seed) {
        Random rnd = new Random(seed);
        Vec3 t1 = dir.cross(Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), t2 = dir.cross(t1);
        List<double[][]> out = new ArrayList<>();
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10 + rnd.nextGaussian() * 0.15;
            walk(out, rnd, dir, t1.scale(Math.cos(a)).add(t2.scale(Math.sin(a))), r, (55.0 + rnd.nextDouble() * 65.0) * r / 96.0, 0.0, 0, t1, t2);
        }
        for (double rr : new double[] {4.0, 9.0, 15.0}) {
            double a0 = rnd.nextDouble() * 6.28, rs = rr * r / 96.0;
            for (int seg = 0; seg < 4; seg++) {
                double[][] pts = new double[7][];
                for (int j = 0; j < 7; j++) {
                    double a = a0 + seg * Math.PI / 2 + j * 0.2;
                    Vec3 q = dir.scale(r).add(t1.scale(Math.cos(a) * rs * (0.9 + rnd.nextDouble() * 0.2))).add(t2.scale(Math.sin(a) * rs * (0.9 + rnd.nextDouble() * 0.2))).normalize();
                    pts[j] = new double[] {q.x, q.y, q.z, rs};
                }
                out.add(pts);
            }
        }
        return out;
    }

    private static void walk(List<double[][]> out, Random rnd, Vec3 p, Vec3 d, int r, double length, double start, int depth, Vec3 t1, Vec3 t2) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {p.x, p.y, p.z, start});
        double s = 0.0, step = 2.5;
        while (s < length) {
            d = d.add(t1.scale(rnd.nextDouble() * 2 - 1).add(t2.scale(rnd.nextDouble() * 2 - 1)).scale(0.35));
            d = d.subtract(p.scale(d.dot(p))).normalize();
            p = p.add(d.scale((1.8 + rnd.nextDouble() * 1.4) / r)).normalize();
            if (p.y < 0.003) break;
            s += step;
            pts.add(new double[] {p.x, p.y, p.z, start + s});
            if (depth < 2 && rnd.nextDouble() < 0.13) {
                Vec3 side = d.cross(p).scale(rnd.nextBoolean() ? 1.2 : -1.2);
                walk(out, rnd, p, d.add(side).normalize(), r, length * (0.2 + rnd.nextDouble() * 0.25), start + s, depth + 1, t1, t2);
            }
        }
        if (pts.size() > 1) out.add(pts.toArray(new double[0][]));
    }

    private static List<Shard> shards(Vec3 c, int r, Vec3 dir, long seed) {
        Random rnd = new Random(seed);
        List<Shard> out = new ArrayList<>();
        double scale = r / 96.0;
        for (Cell cell : cells()) {
            if (rnd.nextDouble() < 0.12) continue;
            Vec3[] k = cell.k();
            int n = k.length;
            List<Vec3[]> pieces = new ArrayList<>();
            double roll = rnd.nextDouble();
            if (roll < 0.4) pieces.add(k);
            else if (roll < 0.75) {
                int h = n / 2;
                Vec3[] a = new Vec3[h + 2], b = new Vec3[n - h + 2];
                a[0] = cell.c(); for (int j = 0; j <= h; j++) a[j + 1] = k[j];
                b[0] = cell.c(); for (int j = h; j <= n; j++) b[j - h + 1] = k[j % n];
                pieces.add(a); pieces.add(b);
            } else {
                int s3 = n / 3;
                for (int part = 0; part < 3; part++) {
                    int from = part * s3, to = part == 2 ? n : (part + 1) * s3;
                    Vec3[] a = new Vec3[to - from + 2];
                    a[0] = cell.c();
                    for (int j = from; j <= to; j++) a[j - from + 1] = k[j % n];
                    pieces.add(a);
                }
            }
            double s = Math.acos(Mth.clamp(cell.c().dot(dir), -1.0, 1.0)) * r;
            for (Vec3[] pc : pieces) {
                Vec3 cu = Vec3.ZERO;
                for (Vec3 q : pc) cu = cu.add(q);
                cu = cu.scale(1.0 / pc.length);
                Shard sh = new Shard();
                sh.p0 = c.add(cu.scale(r));
                sh.off = new Vec3[pc.length];
                double shrink = 0.85 + rnd.nextDouble() * 0.12;
                for (int j = 0; j < pc.length; j++) sh.off[j] = pc[j].subtract(cu).scale(r * shrink);
                double push = (0.1 + 1.0 * Math.exp(-s / (40.0 * scale))) * (0.5 + rnd.nextDouble() * 0.8) * Math.sqrt(scale);
                Vec3 nrm = cu.normalize();
                sh.v = nrm.scale(push).add(rnd.nextGaussian() * 0.08, rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.08);
                sh.axis = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
                sh.w = Math.toRadians(1.0 + rnd.nextDouble() * 5.0) * (rnd.nextBoolean() ? 1 : -1);
                sh.glint = rnd.nextDouble() < 0.07;
                out.add(sh);
            }
        }
        return out;
    }

    private static double hash(int a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (FADE.isEmpty() && SHATTERS.isEmpty())) return;
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<BlockPos, ForcefieldBlockEntity> e : LIVE.entrySet()) {
            float fade = FADE.getOrDefault(e.getKey(), 0.0F);
            if (fade < 0.01F) continue;
            any |= dome(bb, cam, e.getValue(), fade, now);
        }
        for (Shatter s : SHATTERS) any |= shatter(bb, cam, s, now);
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static boolean dome(BufferBuilder bb, Vec3 cam, ForcefieldBlockEntity be, float fade, double now) {
        Vec3 c = be.centre();
        int r = be.radius(), st = be.state();
        double t = be.shownTicks(now);
        double grow = st == ForcefieldBlockEntity.BOOT ? Mth.clamp((t - 140.0) / 80.0, 0.0, 1.0) : 1.0;
        boolean low = st == ForcefieldBlockEntity.UP && be.pct() < Balance.FF_LOW_PCT;
        long tick = (long) now;
        List<Ripple> rips = new ArrayList<>();
        for (Ripple rp : RIPPLES) if (rp.gen().equals(be.getBlockPos())) rips.add(rp);
        Crack crack = null;
        for (Crack k : CRACKS) if (k.gen().equals(be.getBlockPos())) crack = k;
        double crackAge = crack != null ? now - crack.t0() : -1.0;
        float flick = low ? 0.65F + 0.35F * (float) hash(7, tick / 2) : 1.0F;
        if (st == ForcefieldBlockEntity.CRACK) flick *= 0.8F + 0.2F * (float) hash(9, tick);
        List<Cell> cs = cells();
        for (int i = 0; i < cs.size(); i++) {
            Cell cell = cs.get(i);
            Vec3 n = cell.c();
            if (n.y > grow + 0.001) continue;
            if (low && hash(i, tick / 3) < 0.12) continue;
            Vec3 pc = c.add(n.scale(r));
            Vec3 view = pc.subtract(cam);
            double vl = view.length();
            double facing = vl < 1e-6 ? 1.0 : Math.abs(n.dot(view.scale(1.0 / vl)));
            double rim = 1.0 - facing;
            double add = 0.0;
            for (Ripple rp : rips) {
                double age = now - rp.t0();
                if (age < 0 || age > 40) continue;
                double s = Math.acos(Mth.clamp(n.dot(rp.dir()), -1.0, 1.0)) * r;
                double front = age * 4.0 * r / 96.0, fall = 1.0 - age / 40.0;
                for (int ring = 0; ring < 3; ring++) {
                    double fr = front - ring * 12.0 * r / 96.0;
                    if (fr < 0) continue;
                    double x = (s - fr) / (3.0 * r / 96.0 + 1.0);
                    add += rp.power() * fall * Math.exp(-x * x) * (0.9 - ring * 0.25);
                }
                if (s < 7.0 * r / 96.0 && age < 14) add += (1.0 - age / 14.0) * 1.2;
            }
            if (crack != null) {
                double s = Math.acos(Mth.clamp(n.dot(crack.dir()), -1.0, 1.0)) * r;
                if (s < crackAge * 3.2 * r / 96.0) add += 0.12 + 0.1 * hash(i, tick);
                if (s < 6.0 * r / 96.0) add += 0.6;
            }
            if (st == ForcefieldBlockEntity.BOOT && grow < 1.0 && grow - n.y < 0.06) add += 0.7;
            float a = (float) Math.min(0.9, (0.035 + 0.30 * rim * rim * rim + 0.35 * add) * fade * flick);
            float w = (float) Math.min(1.0, add * 0.6);
            float cr = 0.35F + 0.6F * w, cg = 0.72F + 0.25F * w;
            Vec3[] k = cell.k();
            int m = k.length;
            Vec3[] wk = new Vec3[m];
            for (int j = 0; j < m; j++) wk[j] = c.add(k[j].scale(r));
            for (int j = 0; j < m; j++) {
                Vec3 p1 = wk[j], p2 = wk[(j + 1) % m];
                ClientBlastGeo.v(bb, cam, pc.x, pc.y, pc.z, cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, p1.x, p1.y, p1.z, cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, a);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, a);
            }
            float fa = (float) Math.min(1.0, (0.16 + 0.45 * rim + 0.5 * add) * fade * flick);
            for (int j = 0; j < m; j++) {
                Vec3 p1 = wk[j], p2 = wk[(j + 1) % m];
                Vec3 i1 = p1.add(pc.subtract(p1).scale(0.07)), i2 = p2.add(pc.subtract(p2).scale(0.07));
                ClientBlastGeo.quad(bb, cam, p1, p2, i2, i1, 0.35F, 0.8F, 1.0F, fa, 0.35F, 0.8F, 1.0F, fa * 0.4F);
            }
        }
        if (crack != null) {
            double reach = crackAge * 3.2 * r / 96.0;
            for (double[][] line : crack.lines()) {
                for (int j = 1; j < line.length; j++) {
                    if (line[j][3] > reach) break;
                    Vec3 a0 = c.add(new Vec3(line[j - 1][0], line[j - 1][1], line[j - 1][2]).scale(r + 0.15));
                    Vec3 a1 = c.add(new Vec3(line[j][0], line[j][1], line[j][2]).scale(r + 0.15));
                    ClientBlastGeo.streak(bb, cam, a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, 0.55, 0.5F, 0.85F, 1.0F, 0.25F * fade, 0.25F * fade);
                    ClientBlastGeo.streak(bb, cam, a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, 0.14, 0.9F, 0.97F, 1.0F, 0.95F * fade, 0.95F * fade);
                }
            }
        }
        return true;
    }

    private static boolean shatter(BufferBuilder bb, Vec3 cam, Shatter s, double now) {
        double t = now - s.t0();
        if (t < 0 || t > Balance.FF_SHARD_TICKS) return false;
        double life = t / Balance.FF_SHARD_TICKS;
        float fade = (float) (life < 0.55 ? 1.0 : Math.max(0.0, 1.0 - (life - 0.55) / 0.45));
        float flash = (float) Math.max(0.0, 1.0 - t / 6.0);
        double g = 0.03, ground = s.c().y + 0.3;
        for (Shard sh : s.shards()) {
            Vec3 p = sh.p0.add(sh.v.scale(t)).add(0.0, -0.5 * g * t * t, 0.0);
            float a = fade;
            if (p.y < ground) { p = new Vec3(p.x, ground, p.z); a *= 0.3F; }
            double ang = sh.w * t, ca = Math.cos(ang), sa = Math.sin(ang);
            int m = sh.off.length;
            Vec3[] wp = new Vec3[m];
            for (int j = 0; j < m; j++) {
                Vec3 o = sh.off[j];
                Vec3 ro = o.scale(ca).add(sh.axis.cross(o).scale(sa)).add(sh.axis.scale(sh.axis.dot(o) * (1.0 - ca)));
                wp[j] = p.add(ro);
            }
            float fill = (sh.glint ? 0.45F : 0.08F) * a + 0.4F * flash;
            float cr = sh.glint ? 0.9F : 0.45F, cg = sh.glint ? 0.97F : 0.8F;
            for (int j = 0; j < m; j++) {
                Vec3 p1 = wp[j], p2 = wp[(j + 1) % m];
                ClientBlastGeo.v(bb, cam, p.x, p.y, p.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p1.x, p1.y, p1.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, fill);
                ClientBlastGeo.v(bb, cam, p2.x, p2.y, p2.z, cr, cg, 1.0F, fill);
            }
            float ea = Math.min(1.0F, 0.55F * a + 0.4F * flash);
            for (int j = 0; j < m; j++) {
                Vec3 p1 = wp[j], p2 = wp[(j + 1) % m];
                Vec3 i1 = p1.add(p.subtract(p1).scale(0.12)), i2 = p2.add(p.subtract(p2).scale(0.12));
                ClientBlastGeo.quad(bb, cam, p1, p2, i2, i1, 0.4F, 0.85F, 1.0F, ea, 0.4F, 0.85F, 1.0F, ea * 0.3F);
            }
        }
        return true;
    }
}
