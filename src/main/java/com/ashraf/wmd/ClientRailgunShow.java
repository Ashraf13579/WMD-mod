package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientRailgunShow {

    private ClientRailgunShow() {}

    private static final class Gun {
        RailgunBlockEntity be;
        long seen;
        int stage = -1, state = -1;
        long stageAt = -1000, shotAt = -1000;
        boolean whined;
        final List<long[]> arcs = new ArrayList<>();
        final List<double[]> holds = new ArrayList<>();
        final List<double[]> strikes = new ArrayList<>();
        boolean clawA, clawB, vented;
    }

    private static void strike(Gun g, RailgunBlockEntity be, Level level, long t) {
        RandomSource rnd = level.random;
        double[] c = be.worldPoint(0.0, 0.0, 7.0);
        double a = rnd.nextDouble() * Math.PI * 2, d = Balance.RG_STORM_R0 + rnd.nextDouble() * (Balance.RG_STORM_R1 - Balance.RG_STORM_R0);
        int bx = (int) Math.floor(c[0] + Math.cos(a) * d), bz = (int) Math.floor(c[2] + Math.sin(a) * d);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return;
        double x = bx + 0.5, z = bz + 0.5, y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        g.strikes.add(new double[] { t, x, y, z, rnd.nextLong() });
        VeilBridge.flash(x, y + 1.0, z, 0.45F, 0.65F, 1.0F, 22.0F, 6.0F, 0.6F, 8);
        for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, rnd.nextGaussian() * 0.35, rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.35);
        level.playLocalSound(x, y, z, WmdSounds.RG_STRIKE.get(), SoundSource.WEATHER, 5.0F, 0.9F + rnd.nextFloat() * 0.2F, false);
    }

    private static final Map<Long, Gun> GUNS = new HashMap<>();

    static Vec3 pivotNear(Vec3 m) {
        for (Gun g : GUNS.values()) {
            double[] mm = g.be.muzzle();
            if (m.distanceToSqr(mm[0], mm[1], mm[2]) < 100.0) { double[] p = g.be.worldPoint(0.0, 8.0, 7.0); return new Vec3(p[0], p[1], p[2]); }
        }
        return null;
    }
    private static float whiteMax;
    private static long whiteAt = -1000;

    static void tick(RailgunBlockEntity be, Level level) {
        long t = level.getGameTime();
        Gun g = GUNS.computeIfAbsent(be.getBlockPos().asLong(), k -> new Gun());
        g.be = be;
        g.seen = t;
        int st = be.state();
        RandomSource rnd = level.random;
        double[] c = be.worldPoint(0.0, 4.0, 7.0);
        Vec3 mid = new Vec3(c[0], c[1], c[2]);
        if (st == RailgunBlockEntity.CHARGE) {
            int stage = Math.min(9, (int) (be.charge01() * 10.0F));
            if (g.state == RailgunBlockEntity.CHARGE && stage != g.stage) stageEvent(g, be, level, t, mid);
            g.stage = stage;
            float ch = be.charge01();
            boolean fin = be.secondsToShot() <= Balance.RG_FINAL_TICKS / 20;
            if (rnd.nextFloat() < 0.04F + 0.35F * ch + (fin ? 0.3F : 0.0F)) {
                float kind = rnd.nextFloat();
                int bank = rnd.nextInt(4);
                g.arcs.add(new long[] { t, rnd.nextLong(), bank, kind < 0.35F ? 1 : kind < 0.55F ? 2 : 0 });
                if (rnd.nextFloat() < 0.35F) {
                    double[] bt = bankTop(be, bank);
                    level.playLocalSound(bt[0], bt[1], bt[2], WmdSounds.RG_SPARK.get(), SoundSource.BLOCKS, 0.7F + 0.5F * ch, 0.8F + rnd.nextFloat() * 0.6F, false);
                }
            }
            float k = fin ? 1.0F - be.secondsToShot() / (float) (Balance.RG_FINAL_TICKS / 20) : 0.0F;
            double[] mz = be.muzzle();
            if (!g.clawA && ch >= 0.1F) { g.clawA = true; level.playLocalSound(mz[0], mz[1], mz[2], WmdSounds.RG_CLAWS.get(), SoundSource.BLOCKS, 4.0F, 0.85F, false); }
            if (!g.clawB && be.secondsToShot() <= 10) { g.clawB = true; level.playLocalSound(mz[0], mz[1], mz[2], WmdSounds.RG_CLAWS.get(), SoundSource.BLOCKS, 6.0F, 1.2F, false); }

            if (fin && rnd.nextFloat() < Balance.RG_STORM_CHANCE * (0.2F + 0.8F * k)) strike(g, be, level, t);
            if (fin) T4Fx.shakeNear(mid, 64.0, 0.25F + 1.1F * k);
            else if (ch > 0.3F) T4Fx.shakeNear(mid, 32.0, 0.25F * ch);

            g.holds.removeIf(h -> t - h[0] > h[1]);
            int banks = 0, barrel = 0;
            for (double[] h : g.holds) if (h[3] == 0) banks++; else barrel++;
            int wantBanks = (int) (1 + 5 * ch + 4 * k), wantBarrel = be.secondsToShot() <= 30 ? 2 + (int) (5 * (1.0 - be.secondsToShot() / 30.0)) : 0;
            double[] c0 = be.worldPoint(0.0, 0.0, 7.0);
            for (; banks < wantBanks; banks++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 9.0 + rnd.nextDouble() * 8.0;
                g.holds.add(new double[] { t, 40 + rnd.nextInt(80), rnd.nextLong(), 0, rnd.nextInt(4), c0[0] + Math.cos(a) * d, c0[2] + Math.sin(a) * d });
            }
            for (; barrel < wantBarrel; barrel++) {
                double sAlong = 6.0 + rnd.nextDouble() * 18.0, a = rnd.nextDouble() * Math.PI * 2, d = 3.0 + rnd.nextDouble() * 7.0;
                double[] u = be.worldPoint(0.0, 8.0, 7.0), m = be.muzzle();
                double bx = u[0] + (m[0] - u[0]) * sAlong / 28.0, bz = u[2] + (m[2] - u[2]) * sAlong / 28.0;
                g.holds.add(new double[] { t, 20 + rnd.nextInt(40), rnd.nextLong(), 1, sAlong, bx + Math.cos(a) * d, bz + Math.sin(a) * d });
            }
        } else {
            g.stage = -1;
            if (st != RailgunBlockEntity.AFTER) { g.clawA = false; g.clawB = false; }
        }
        if (st == RailgunBlockEntity.AFTER && !g.vented && t - g.shotAt >= 8 && t - g.shotAt < 200) {
            g.vented = true;
            double[] pv = be.worldPoint(0.0, 8.0, 7.0), mv = be.muzzle();
            level.playLocalSound(pv[0] + (mv[0] - pv[0]) * 0.86, pv[1] + (mv[1] - pv[1]) * 0.86, pv[2] + (mv[2] - pv[2]) * 0.86, WmdSounds.RG_VENT.get(), SoundSource.BLOCKS, 5.0F, 1.0F, false);
        }
        if (st != RailgunBlockEntity.AFTER) g.vented = false;
        if (st == RailgunBlockEntity.AFTER) {
            long sa = t - g.shotAt;
            double[] pv = be.worldPoint(0.0, 8.0, 7.0);
            if (!g.whined && sa >= 30 && sa < 200) { g.whined = true; level.playLocalSound(pv[0], pv[1], pv[2], WmdSounds.RG_WHINE_DOWN.get(), SoundSource.BLOCKS, 4.0F, 1.0F, false); }
            if (sa >= 80 && sa < Balance.RG_STEAM_TICKS && (sa - 80) % 300 == 0)
                level.playLocalSound(pv[0], pv[1], pv[2], WmdSounds.RG_TICK.get(), SoundSource.BLOCKS, 2.0F, 0.9F + 0.2F * rnd.nextFloat(), false);
        } else g.whined = false;
        double steam = t - g.shotAt;
        if (steam >= 10 && steam < Balance.RG_STEAM_TICKS && rnd.nextFloat() < 0.9F * (1.0F - (float) (steam / Balance.RG_STEAM_TICKS))) {
            double[] pv = be.worldPoint(0.0, 8.0, 7.0), mv = be.muzzle();
            double s = 0.15 + 0.85 * rnd.nextDouble();
            level.addParticle(ParticleTypes.CLOUD, pv[0] + (mv[0] - pv[0]) * s + rnd.nextGaussian() * 0.8, pv[1] + (mv[1] - pv[1]) * s + 2.2, pv[2] + (mv[2] - pv[2]) * s + rnd.nextGaussian() * 0.8,
                    rnd.nextGaussian() * 0.01, 0.06 + 0.06 * rnd.nextDouble(), rnd.nextGaussian() * 0.01);
        }
        if (st == RailgunBlockEntity.AFTER && g.state != RailgunBlockEntity.AFTER && be.stateTicks() < 10) {
            g.shotAt = t;
            T4Fx.shakeNear(mid, 160.0, 4.0F);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(mid);
                if (d < Balance.RG_SHOW_FLASH_R) { whiteMax = (float) (1.0 - d / Balance.RG_SHOW_FLASH_R) * 0.95F + 0.05F; whiteAt = t; }
            }
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                level.addParticle(ParticleTypes.CLOUD, c[0] + Math.cos(a) * 8.0, c[1] - 3.5, c[2] + Math.sin(a) * 8.0, Math.cos(a) * 0.9, 0.05, Math.sin(a) * 0.9);
            }
        }
        g.state = st;
        g.arcs.removeIf(a -> t - a[0] > 5);
        g.strikes.removeIf(s -> t - s[0] > 9);
    }

    private static void stageEvent(Gun g, RailgunBlockEntity be, Level level, long t, Vec3 mid) {
        g.stageAt = t;
        RandomSource rnd = level.random;
        for (int i = 0; i < 4; i++) g.arcs.add(new long[] { t, rnd.nextLong(), i, 0 });
        T4Fx.shakeNear(mid, 48.0, 0.9F);
        level.playLocalSound(mid.x, mid.y, mid.z, WmdSounds.FUSION_DISCHARGE.get(), SoundSource.BLOCKS, 4.0F, 0.55F + 0.05F * g.stage, false);
        double[] m = be.muzzle();
        VeilBridge.flash(m[0], m[1], m[2], 0.6F, 0.8F, 1.0F, 30.0F, 5.0F, 0.6F, 10);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { GUNS.clear(); whiteAt = -1000; }

    private static double[] bankTop(RailgunBlockEntity be, int i) {
        return be.worldPoint(i % 2 == 0 ? -6.19 : 6.19, 8.05, i < 2 ? 0.81 : 13.19);
    }

    private static double[] collarPoint(RailgunBlockEntity be, int i, RandomSource r) {
        double[] p = be.worldPoint(0.0, 8.0, 7.0), m = be.muzzle();
        double s = RailgunBlockEntity.collarS(i) / 28.0;
        return new double[] { p[0] + (m[0] - p[0]) * s + r.nextGaussian() * 0.7, p[1] + (m[1] - p[1]) * s + r.nextGaussian() * 0.7, p[2] + (m[2] - p[2]) * s + r.nextGaussian() * 0.7 };
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GUNS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long tick = level.getGameTime();
        for (Iterator<Gun> it = GUNS.values().iterator(); it.hasNext(); ) {
            Gun g = it.next();
            if (tick - g.seen > 40 || g.be.isRemoved() || g.be.getLevel() != level) it.remove();
        }
        if (GUNS.isEmpty()) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Gun g : GUNS.values()) {
            float k = finalK(g.be);
            if (k <= 0.0F) continue;
            double[] c = g.be.worldPoint(0.0, 0.0, 7.0);
            if (Math.hypot(cam.x - c[0], cam.z - c[2]) > 600.0) continue;
            vortex(bb, cam, c[0], c[1] + Balance.RG_SHOW_STORM_H, c[2], now, k);
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, false);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Gun g : GUNS.values()) {
            double[] c = g.be.worldPoint(0.0, 0.0, 7.0);
            if (Math.hypot(cam.x - c[0], cam.z - c[2]) > 600.0) continue;
            any |= glow(bb, cam, left, up, g, now, tick);
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static float finalK(RailgunBlockEntity be) {
        if (be.state() != RailgunBlockEntity.CHARGE) return 0.0F;
        int left = be.secondsToShot(), fin = Balance.RG_FINAL_TICKS / 20;
        return left > fin ? 0.0F : 1.0F - left / (float) fin;
    }

    private static boolean glow(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Gun g, double now, long tick) {
        RailgunBlockEntity be = g.be;
        if (ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return false;
        int st = be.state();
        boolean drew = false;
        double[] c = be.worldPoint(0.0, 0.0, 7.0);
        double gy = c[1] + 0.15;
        double[] m = be.muzzle();
        double[] p = be.worldPoint(0.0, 8.0, 7.0);

        if (st == RailgunBlockEntity.CHARGE || st == RailgunBlockEntity.LOAD || st == RailgunBlockEntity.SLEW) {
            int stage = st == RailgunBlockEntity.CHARGE ? Math.min(10, (int) (be.charge01() * 10.0F)) : 0;
            double R = Balance.RG_SHOW_DIAL_R;
            for (int s = 0; s < 10; s++) {
                boolean lit = s < stage, next = s == stage && st == RailgunBlockEntity.CHARGE;
                float a = lit ? 0.9F : next ? (((tick / 6) % 2 == 0) ? 0.8F : 0.15F) : 0.12F;
                float r = finalK(be) > 0 && lit ? 1.0F : 0.45F, gg = finalK(be) > 0 && lit ? 0.35F : 0.75F, b = finalK(be) > 0 && lit ? 0.3F : 1.0F;
                double a0 = Math.PI * 2 * s / 10 + 0.04, a1 = Math.PI * 2 * (s + 1) / 10 - 0.04;
                for (int j = 0; j < 6; j++) {
                    double b0 = a0 + (a1 - a0) * j / 6, b1 = a0 + (a1 - a0) * (j + 1) / 6;
                    ClientBlastGeo.streak(bb, cam, c[0] + Math.cos(b0) * R, gy, c[2] + Math.sin(b0) * R, c[0] + Math.cos(b1) * R, gy, c[2] + Math.sin(b1) * R, 0.45, r, gg, b, a, a);
                }
            }
            drew = true;
        }
        if (st == RailgunBlockEntity.CHARGE) {
            float ch = be.charge01(), k = finalK(be);

            double age = now - g.stageAt;
            if (age >= 0 && age < 30) {
                double R = Balance.RG_SHOW_DIAL_R + 50.0 * age / 30.0;
                float a = (float) (1.0 - age / 30.0);
                for (int i = 0; i < 72; i++) {
                    double a0 = Math.PI * 2 * i / 72, a1 = Math.PI * 2 * (i + 1) / 72;
                    ClientBlastGeo.streak(bb, cam, c[0] + Math.cos(a0) * R, gy + 0.2, c[2] + Math.sin(a0) * R, c[0] + Math.cos(a1) * R, gy + 0.2, c[2] + Math.sin(a1) * R, 0.9 * a + 0.2, 0.55F, 0.8F, 1.0F, 0.8F * a, 0.8F * a);
                }
                if (age < 15) {
                    double s = age / 15.0;
                    double bx = p[0] + (m[0] - p[0]) * s, by = p[1] + (m[1] - p[1]) * s + 0.3, bz = p[2] + (m[2] - p[2]) * s;
                    ClientBlastGeo.disc(bb, cam, left, up, bx, by, bz, 3.2, 0.7F, 0.88F, 1.0F, 0.9F, 14);
                    ClientBlastGeo.disc(bb, cam, left, up, bx, by, bz, 1.2, 1.0F, 1.0F, 1.0F, 1.0F, 10);
                }
            }
            coreStream(bb, cam, p, m, ch, k, be.secondsToShot() <= 2, now, tick);

            for (long[] arc : g.arcs) {
                double[] a = bankTop(be, (int) arc[2]);
                RandomSource r = RandomSource.create(arc[1] + tick / 2);
                double[] b;
                if (arc[3] == 1) b = collarPoint(be, (int) Math.floorMod(arc[1], 4L), r);
                else if (arc[3] == 2) b = bankTop(be, (int) arc[2] ^ (Math.floorMod(arc[1], 2L) == 0 ? 1 : 2));
                else {
                    double ang = r.nextDouble() * Math.PI * 2, d = 8.0 + r.nextDouble() * 7.0;
                    b = new double[] { c[0] + Math.cos(ang) * d, gy, c[2] + Math.sin(ang) * d };
                }
                float fade = (float) (1.0 - (now - arc[0]) / 6.0);
                if (fade > 0) bolt(bb, cam, a, b, arc[1] + tick / 2, 0.06, fade);
            }

            for (double[] hld : g.holds) {
                double[] a0;
                if (hld[3] == 0) a0 = bankTop(be, (int) hld[4]);
                else {
                    double sa = hld[4];
                    a0 = new double[] { p[0] + (m[0] - p[0]) * sa / 28.0, p[1] + (m[1] - p[1]) * sa / 28.0 - 1.4, p[2] + (m[2] - p[2]) * sa / 28.0 };
                }
                double age2 = now - hld[0];
                float fl = (float) Math.min(1.0, Math.min(age2 / 4.0, (hld[1] - age2) / 6.0));
                if (fl <= 0) continue;
                float flick = 0.55F + 0.45F * (float) Math.abs(Math.sin(now * 1.7 + hld[2] % 97));
                bolt(bb, cam, a0, new double[] { hld[5], gy, hld[6] }, (long) hld[2] + tick / 2, hld[3] == 0 ? 0.07 : 0.09, fl * flick);
            }

            if (ch > 0.6F) {
                double q = (ch - 0.6) / 0.4;
                int rings = 1 + (int) Math.round(11.0 * q * q);
                double ax = m[0] - p[0], ay = m[1] - p[1], az = m[2] - p[2], al = Math.sqrt(ax * ax + ay * ay + az * az);
                ax /= al; ay /= al; az /= al;
                double ux = az, uy = 0.0, uz = -ax, ul = Math.sqrt(ux * ux + uz * uz);
                if (ul < 1.0E-4) { ux = 1.0; uz = 0.0; ul = 1.0; }
                ux /= ul; uz /= ul;
                double vx = uy * az - uz * ay, vy = uz * ax - ux * az, vz = ux * ay - uy * ax;
                int step = (int) Math.floor(now * (0.04 + 0.2 * k));
                for (int i = 0; i < rings; i++) {
                    double sa = RailgunBlockEntity.collarS((i * 5 + step) % 12) * al / 28.0;
                    double cx0 = p[0] + ax * sa, cy0 = p[1] + ay * sa, cz0 = p[2] + az * sa;
                    RandomSource rr = RandomSource.create(i * 977L + tick / 2);
                    double rad = 2.05 + 0.25 * Math.sin(now * 0.3 + i), spin = now * 0.2 + i;
                    int n = 18;
                    double[] prev = null, first = null;
                    for (int j = 0; j <= n; j++) {
                        double ang = spin + Math.PI * 2 * j / n, jr = j == n ? 0.0 : rr.nextGaussian() * 0.35;
                        double cr = Math.cos(ang) * (rad + jr), sr = Math.sin(ang) * (rad + jr);
                        double[] pt = j == n ? first : new double[] { cx0 + ux * cr + vx * sr + ax * rr.nextGaussian() * 0.25, cy0 + uy * cr + vy * sr, cz0 + uz * cr + vz * sr + az * rr.nextGaussian() * 0.25 };
                        if (j == 0) first = pt;
                        if (prev != null) {
                            float ra = (float) (0.6 + 0.4 * Math.abs(Math.sin(now * 2.1 + i * 1.3)));
                            ClientBlastGeo.streak(bb, cam, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.5, 0.45F, 0.65F, 1.0F, 0.35F * ra, 0.35F * ra);
                            ClientBlastGeo.streak(bb, cam, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.11, 0.9F, 0.95F, 1.0F, ra, ra);
                        }
                        prev = pt;
                    }
                }
            }

            int n = 10 + (int) (50 * ch);
            double speed = 0.02 + 0.06 * ch + 0.08 * k;
            for (int i = 0; i < n; i++) {
                double ph = frac(now * speed + i * 0.618034);
                double th = i * 2.399963, z = 1.0 - 2.0 * frac(i * 0.37 + 0.13);
                double rr = Math.sqrt(Math.max(0.0, 1.0 - z * z)), dist = (4.0 + 12.0 * (1.0 - ph));
                double dx = Math.cos(th) * rr, dy = z, dz = Math.sin(th) * rr;
                double x0 = m[0] + dx * dist, y0 = m[1] + dy * dist, z0 = m[2] + dz * dist;
                double x1 = m[0] + dx * (dist - 1.8), y1 = m[1] + dy * (dist - 1.8), z1 = m[2] + dz * (dist - 1.8);
                ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, 0.08, 0.6F, 0.85F, 1.0F, 0.0F, (float) (0.9 * ph));
            }
            double cr = 0.8 + 2.2 * ch + 2.5 * k + (k > 0 ? 0.4 * Math.sin(now * 0.9) : 0.0);
            ClientBlastGeo.disc(bb, cam, left, up, m[0], m[1], m[2], cr * 2.0, 0.45F, 0.7F, 1.0F, 0.5F, 16);
            ClientBlastGeo.disc(bb, cam, left, up, m[0], m[1], m[2], cr, 0.9F, 0.96F, 1.0F, 0.9F, 14);

            int sec = be.secondsToShot();
            if (sec <= 10) {
                double q = 1.0 - sec / 10.0;
                ClientBlastGeo.disc(bb, cam, left, up, m[0], m[1], m[2], 4.0 + 6.0 * q, 1.0F, 1.0F, 1.0F, (float) (0.5 + 0.4 * q), 18);
                for (int i = 0; i < 16; i++) {
                    double th = i * 2.399963 + now * 0.05, z = 1.0 - 2.0 * frac(i * 0.37 + 0.13), rr = Math.sqrt(Math.max(0.0, 1.0 - z * z));
                    double L = (6.0 + 18.0 * q) * (0.6 + 0.4 * Math.sin(now * 0.7 + i));
                    ClientBlastGeo.streak(bb, cam, m[0], m[1], m[2], m[0] + Math.cos(th) * rr * L, m[1] + z * L, m[2] + Math.sin(th) * rr * L, 0.25, 0.75F, 0.9F, 1.0F, 0.8F, 0.0F);
                }
            }

            if (st == RailgunBlockEntity.CHARGE) {
                float chc = be.charge01(), kc = finalK(be);
                double bx = m[0] - p[0], by = m[1] - p[1], bz = m[2] - p[2], hl = Math.hypot(bx, bz);
                double sx = hl < 1.0E-4 ? 1.0 : bz / hl, sz = hl < 1.0E-4 ? 0.0 : -bx / hl;
                int crawl = 1 + (int) (5 * chc) + (int) (4 * kc);
                for (int i = 0; i < crawl; i++)
                    for (int rail = -1; rail <= 1; rail += 2) {
                        double s0 = frac(now * (0.004 + 0.02 * chc) + i / (double) crawl + (rail > 0 ? 0.5 / crawl : 0.0)), s1 = Math.min(1.0, s0 + 0.05);
                        bolt(bb, cam, new double[] { p[0] + bx * s0 + sx * rail * 2.1, p[1] + by * s0 + 0.4, p[2] + bz * s0 + sz * rail * 2.1 },
                                new double[] { p[0] + bx * s1 + sx * rail * 2.1, p[1] + by * s1 + 0.4, p[2] + bz * s1 + sz * rail * 2.1 }, tick / 2 * 31 + i * 7L + rail, 0.05, 0.75F, 0.15);
                    }
            }

            if (k > 0 && Math.floorMod(tick + g.be.getBlockPos().getX(), 7L) < 2) {
                RandomSource r = RandomSource.create(tick * 31 + g.be.getBlockPos().asLong());
                double sy = c[1] + Balance.RG_SHOW_STORM_H;
                for (int i = 0; i < 1 + (int) (3 * k); i++) {
                    double a0 = r.nextDouble() * Math.PI * 2, d0 = 10 + r.nextDouble() * 40, a1 = a0 + r.nextGaussian() * 0.8, d1 = 10 + r.nextDouble() * 40;
                    bolt(bb, cam, new double[] { c[0] + Math.cos(a0) * d0, sy, c[2] + Math.sin(a0) * d0 }, new double[] { c[0] + Math.cos(a1) * d1, sy - 2 - r.nextDouble() * 6, c[2] + Math.sin(a1) * d1 },
                            r.nextLong(), 0.14, 0.9F);
                }
            }

            for (double[] s : g.strikes) {
                double sAge = now - s[0];
                if (sAge < 0 || sAge > 9) continue;
                float fl = sAge < 1 ? 1.0F : sAge < 2 ? 0.25F : sAge < 3 ? 0.9F : sAge < 4 ? 0.4F : (float) (0.5 * (1.0 - (sAge - 4) / 5.0));
                RandomSource r = RandomSource.create((long) s[4]);
                double[] top = { s[1] + r.nextGaussian() * 10.0, s[2] + 60.0 + r.nextDouble() * 30.0, s[3] + r.nextGaussian() * 10.0 };
                bolt(bb, cam, top, new double[] { s[1], s[2], s[3] }, (long) s[4] + (sAge < 2 ? 0 : 1), 0.09, fl);
            }
            drew = true;
        }

        double sa = now - g.shotAt;
        if (sa >= 0 && sa < Balance.RG_BLOOM_TICKS) {
            double ax = m[0] - p[0], ay = m[1] - p[1], az = m[2] - p[2], al = Math.sqrt(ax * ax + ay * ay + az * az);
            if (al > 1.0E-4) {
                ax /= al; ay /= al; az /= al;
                double u = sa / Balance.RG_BLOOM_TICKS;
                float a = (float) Math.pow(1.0 - u, 1.5);
                ClientBlastGeo.disc(bb, cam, left, up, m[0], m[1], m[2], 6.0 + 10.0 * Math.sqrt(u), 0.75F, 0.88F, 1.0F, 0.8F * a, 20);
                ClientBlastGeo.streak(bb, cam, m[0], m[1], m[2], m[0] + ax * 60.0, m[1] + ay * 60.0, m[2] + az * 60.0, 2.4 * (1.0 - u) + 0.4, 0.5F, 0.72F, 1.0F, 0.6F * a, 0.0F);
                for (int i = 0; i < 7; i++) {
                    double dd = (4.0 + i * 6.5) * (1.0 + u * 0.6), rad = (i % 2 == 0 ? 2.6 : 1.4) * (1.0 - i * 0.08);
                    ClientBlastGeo.disc(bb, cam, left, up, m[0] + ax * dd, m[1] + ay * dd, m[2] + az * dd, rad, 0.88F, 0.94F, 1.0F, a * (1.0F - i / 8.0F), 14);
                }
                drew = true;
            }
        }
        if (sa >= 0 && sa < 40) {
            double R = 8.0 + 60.0 * Math.sqrt(sa / 40.0);
            float a = (float) (1.0 - sa / 40.0);
            for (int i = 0; i < 96; i++) {
                double a0 = Math.PI * 2 * i / 96, a1 = Math.PI * 2 * (i + 1) / 96;
                ClientBlastGeo.streak(bb, cam, c[0] + Math.cos(a0) * R, gy + 0.6, c[2] + Math.sin(a0) * R, c[0] + Math.cos(a1) * R, gy + 0.6, c[2] + Math.sin(a1) * R, 1.5 * a + 0.3, 0.9F, 0.95F, 1.0F, 0.7F * a, 0.7F * a);
            }
            drew = true;
        }
        return drew;
    }

    private static void bolt(BufferBuilder bb, Vec3 cam, double[] a, double[] b, long seed, double w, float alpha) { bolt(bb, cam, a, b, seed, w, alpha, 0.07); }

    static void bolt(BufferBuilder bb, Vec3 cam, double[] a, double[] b, long seed, double w, float alpha, double jag) {
        RandomSource r = RandomSource.create(seed);
        double len = Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
        int n = Math.max(6, Math.min(24, (int) (len * 1.2)));
        double jit = len * jag;
        double px = a[0], py = a[1], pz = a[2];
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            double qx = a[0] + (b[0] - a[0]) * f, qy = a[1] + (b[1] - a[1]) * f, qz = a[2] + (b[2] - a[2]) * f;
            if (i < n) { qx += r.nextGaussian() * jit; qy += r.nextGaussian() * jit; qz += r.nextGaussian() * jit; }
            segment(bb, cam, px, py, pz, qx, qy, qz, w, alpha);
            if (i < n && r.nextFloat() < 0.22F) {
                double fx = qx, fy = qy, fz = qz, step = len / n;
                for (int j = 0; j < 2 + r.nextInt(2); j++) {
                    double gx = fx + (b[0] - a[0]) / len * step * 0.6 + r.nextGaussian() * step * 0.7, gy = fy + (b[1] - a[1]) / len * step * 0.6 + r.nextGaussian() * step * 0.7;
                    double gz = fz + (b[2] - a[2]) / len * step * 0.6 + r.nextGaussian() * step * 0.7;
                    segment(bb, cam, fx, fy, fz, gx, gy, gz, w * 0.6, alpha * (0.7F - 0.2F * j));
                    fx = gx; fy = gy; fz = gz;
                }
            }
            px = qx; py = qy; pz = qz;
        }
    }

    static void electricTrail(BufferBuilder bb, Vec3 cam, List<Vec3> pts, double heat, long tick, double now, float fade) {
        int n = pts.size() - 1;
        if (n < 1 || fade <= 0.0F) return;
        for (int i = 1; i <= n; i++) {
            Vec3 a = pts.get(i - 1), b = pts.get(i);
            double u = i / (double) n, in = Math.min(1.0, (0.25 + 0.75 * u) * (0.35 + 0.65 * heat));
            float al = (float) (fade * (0.35 + 0.65 * in));
            ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.8 + 2.4 * in, 0.25F, 0.5F, 1.0F, 0.22F * al, 0.22F * al);
            ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.18 + 0.35 * in, 0.8F, 0.92F, 1.0F, 0.95F * al, 0.95F * al);
            int strands = in > 0.75 ? 2 : 1;
            for (int k = 0; k < strands; k++)
                bolt(bb, cam, new double[] { a.x, a.y, a.z }, new double[] { b.x, b.y, b.z }, i * 7919L + k * 104729L + tick / 2, 0.03 + 0.04 * in,
                        al * (0.3F + 0.25F * (float) Math.abs(Math.sin(now * 0.9 + i + k * 2.1))), 0.02);
        }
        int beads = 3 + (int) (7 * heat);
        for (int k = 0; k < beads; k++) {
            double pos = frac(now * (0.015 + 0.03 * heat) + k / (double) beads) * n;
            int i0 = Math.min(n - 1, (int) Math.floor(pos));
            Vec3 p = pts.get(i0).lerp(pts.get(i0 + 1), pos - i0), q = pts.get(i0 + 1);
            Vec3 d = q.subtract(p);
            double dl = Math.max(1.0E-3, d.length()), L = Math.min(dl, 6.0);
            ClientBlastGeo.streak(bb, cam, p.x, p.y, p.z, p.x + d.x / dl * L, p.y + d.y / dl * L, p.z + d.z / dl * L, 0.5 + 0.6 * heat, 0.8F, 0.92F, 1.0F, 0.0F, 0.9F * fade);
        }
    }

    private static void coreStream(BufferBuilder bb, Vec3 cam, double[] p, double[] m, float ch, float k, boolean blaze, double now, long tick) {
        double ax = m[0] - p[0], ay = m[1] - p[1], az = m[2] - p[2], al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-3) return;
        ax /= al; ay /= al; az /= al;
        double ux = az, uz = -ax, ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1.0E-4) { ux = 1.0; uz = 0.0; ul = 1.0; }
        ux /= ul; uz /= ul;
        double vx = -uz * ay, vy = uz * ax - ux * az, vz = ux * ay;
        double s0 = 4.8 * al / 28.0, s1 = 25.5 * al / 28.0, len = s1 - s0;
        float flick = k > 0 ? 0.7F + 0.3F * (float) Math.abs(Math.sin(now * 2.3 + Math.sin(now * 0.7) * 3.0)) : 1.0F;
        float lit = (float) Math.min(1.0, (0.45 + 0.55 * ch) * flick);

        ClientBlastGeo.streak(bb, cam, p[0] + ax * s0, p[1] + ay * s0, p[2] + az * s0, p[0] + ax * s1, p[1] + ay * s1, p[2] + az * s1,
                0.12 + 0.3 * ch + 0.1 * k, 0.25F, 0.5F, 1.0F, (0.08F + 0.22F * ch) * lit, (0.08F + 0.22F * ch) * lit);
        if (blaze) ClientBlastGeo.streak(bb, cam, p[0] + ax * s0, p[1] + ay * s0, p[2] + az * s0, p[0] + ax * s1, p[1] + ay * s1, p[2] + az * s1,
                0.32, 0.9F, 0.96F, 1.0F, 1.0F, 1.0F);

        int strands = blaze ? 6 : 1 + (int) Math.round(4.0 * ch) + (k > 0.5F ? 1 : 0);
        double amp = Math.min(0.2, 0.05 + 0.12 * ch + 0.04 * k), spd = 0.25 + 0.9 * ch + 0.4 * k, w = 0.022 + 0.05 * ch + 0.02 * k;
        int n = Math.max(12, (int) (len * 3.0));
        for (int j = 0; j < strands; j++) {
            RandomSource r = RandomSource.create(j * 7919L + tick / 2);
            double f1 = 1.7 + 0.4 * j, f2 = 3.1 + 0.6 * j, ph = j * 2.4;
            double px = 0, py = 0, pz = 0;
            for (int i = 0; i <= n; i++) {
                double s = s0 + len * i / n, x = s - now * spd;
                double end = Math.min(1.0, Math.min(i, n - i) / 2.0);
                double ou = end * (amp * (0.65 * Math.sin(x * f1 + ph) + 0.35 * Math.sin(x * f2 + ph * 1.7)) + r.nextGaussian() * amp * 0.25);
                double ov = end * (amp * (0.65 * Math.cos(x * f1 * 0.9 + ph * 1.3) + 0.35 * Math.sin(x * f2 * 1.1 + ph)) + r.nextGaussian() * amp * 0.25);
                double rr = Math.hypot(ou, ov);
                if (rr > 0.28) { ou *= 0.28 / rr; ov *= 0.28 / rr; }
                double qx = p[0] + ax * s + ux * ou + vx * ov, qy = p[1] + ay * s + vy * ov, qz = p[2] + az * s + uz * ou + vz * ov;
                if (i > 0) segment(bb, cam, px, py, pz, qx, qy, qz, w, (0.55F + 0.45F * (float) Math.abs(Math.sin(now * 1.3 + j * 1.9 + i * 0.4))) * lit);
                px = qx; py = qy; pz = qz;
            }
        }

        int beads = 2 + (int) (6 * ch) + (k > 0 ? 2 : 0);
        double bs = 0.012 + 0.03 * ch + 0.02 * k;
        for (int i = 0; i < beads; i++) {
            double s = s0 + len * frac(now * bs + i / (double) beads), e = Math.min(s1, s + 0.6 + 0.8 * ch);
            ClientBlastGeo.streak(bb, cam, p[0] + ax * s, p[1] + ay * s, p[2] + az * s, p[0] + ax * e, p[1] + ay * e, p[2] + az * e,
                    0.1 + 0.12 * ch, 0.8F, 0.92F, 1.0F, 0.0F, 0.85F * lit);
        }
    }

    private static void segment(BufferBuilder bb, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double w, float alpha) {
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, w * 3.0, 0.22F, 0.48F, 1.0F, 0.32F * alpha, 0.32F * alpha);
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, w, 0.72F, 0.88F, 1.0F, alpha, alpha);
    }

    private static void vortex(BufferBuilder bb, Vec3 cam, double x, double y, double z, double now, float k) {
        for (int ring = 0; ring < 7; ring++) {
            double r0 = 6.0 + ring * 11.0, r1 = r0 + 13.0;
            double spin = now * (0.012 - ring * 0.0012), dip = ring * 1.2;
            float a = (float) (k * (0.62 - ring * 0.06));
            int n = 36;
            for (int i = 0; i < n; i++) {
                double a0 = spin + Math.PI * 2 * i / n, a1 = spin + Math.PI * 2 * (i + 1) / n;
                double rag = 0.6 + 0.4 * Math.sin(i * 1.7 + ring * 2.3 + now * 0.01);
                float ai = (float) (a * rag);
                float tone = 0.10F + 0.04F * ring;
                ClientBlastGeo.v(bb, cam, x + Math.cos(a0) * r0, y + dip, z + Math.sin(a0) * r0, tone, tone, tone + 0.05F, ai);
                ClientBlastGeo.v(bb, cam, x + Math.cos(a1) * r0, y + dip, z + Math.sin(a1) * r0, tone, tone, tone + 0.05F, ai);
                ClientBlastGeo.v(bb, cam, x + Math.cos(a1) * r1, y + dip + 1.2, z + Math.sin(a1) * r1, tone, tone, tone + 0.05F, ai * 0.5F);
                ClientBlastGeo.v(bb, cam, x + Math.cos(a0) * r1, y + dip + 1.2, z + Math.sin(a0) * r1, tone, tone, tone + 0.05F, ai * 0.5F);
            }
        }
    }

    private static double frac(double v) { return v - Math.floor(v); }

    private static float brownout(Vec3 cam) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0.0F;
        long tk = level.getGameTime();
        float best = 0.0F;
        for (Gun g : GUNS.values()) {
            if (g.be.state() != RailgunBlockEntity.CHARGE) continue;
            double[] c = g.be.worldPoint(0.0, 0.0, 7.0);
            double d = Math.hypot(cam.x - c[0], cam.z - c[2]);
            if (d >= Balance.RG_SHOW_DARK_R) continue;
            double sAge = tk - g.stageAt;
            float fl = sAge >= 0 && sAge < 30 ? (tk % 3 == 0 ? 0.15F : 0.5F) * (float) (1.0 - sAge / 30.0)
                    : Math.floorMod(tk * 7919L + g.be.getBlockPos().asLong(), 11L) == 0 ? 0.35F * finalK(g.be) : 0.0F;
            best = Math.max(best, fl * (float) (1.0 - d / Balance.RG_SHOW_DARK_R));
        }
        return best;
    }

    static Vec3 near(Vec3 cam, Vec3 v) {
        double d = v.distanceTo(cam);
        return d <= 400.0 ? v : cam.add(v.subtract(cam).scale(400.0 / d));
    }

    static void slugDart(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Vec3 s, Vec3 dir, double now, double k) {
        if (dir.lengthSqr() < 1.0E-8) return;
        Vec3 d = dir.normalize(), nose = s.add(d.scale(3.0 * k)), tail = s.subtract(d.scale(9.0 * k));
        ClientBlastGeo.streak(bb, cam, nose.x, nose.y, nose.z, tail.x, tail.y, tail.z, 1.6 * k, 0.45F, 0.65F, 1.0F, 0.6F, 0.0F);
        ClientBlastGeo.streak(bb, cam, nose.x, nose.y, nose.z, tail.x, tail.y, tail.z, 0.45 * k, 1.0F, 0.97F, 0.9F, 1.0F, 0.6F);
        ClientBlastGeo.disc(bb, cam, left, up, nose.x, nose.y, nose.z, 3.0 * k, 0.8F, 0.9F, 1.0F, 0.9F, 14);
        Vec3 s1 = d.cross(new Vec3(0.0, 1.0, 0.0));
        s1 = s1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : s1.normalize();
        Vec3 s2 = d.cross(s1).normalize();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + now * 0.05;
            Vec3 end = nose.subtract(d.scale(40.0 * k)).add(s1.scale(Math.cos(a) * 10.0 * k)).add(s2.scale(Math.sin(a) * 10.0 * k));
            ClientBlastGeo.streak(bb, cam, nose.x, nose.y, nose.z, end.x, end.y, end.z, 0.25 * k, 0.75F, 0.88F, 1.0F, 0.55F, 0.0F);
        }
    }

    static boolean crown(BufferBuilder bb, Vec3 cam, double x, double y, double z, double age, long seed) {
        double u = age / Balance.RG_CROWN_TICKS;
        if (u < 0.0 || u > 1.0) return false;
        float hr = (float) (1.0 - 0.5 * u), hg = (float) (0.85 - 0.6 * u), hb = (float) (0.5 - 0.45 * u);
        double r0 = Balance.RG_SHAFT_R + 1.0, rr = r0 + 0.35 * age, h = Math.min(30.0, 1.6 * age) * (1.0 - u);
        int seg = 48;
        for (int i = 0; i < seg && h > 0.5; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double h0 = h * (0.7 + 0.3 * Math.sin(i * 2.3 + seed)), h1 = h * (0.7 + 0.3 * Math.sin((i + 1) * 2.3 + seed));
            ClientBlastGeo.quad(bb, cam, new Vec3(x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0), new Vec3(x + Math.cos(a1) * r0, y, z + Math.sin(a1) * r0),
                    new Vec3(x + Math.cos(a1) * rr, y + h1, z + Math.sin(a1) * rr), new Vec3(x + Math.cos(a0) * rr, y + h0, z + Math.sin(a0) * rr),
                    hr, hg, hb, 0.7F * (float) (1.0 - u), hr, hg, hb, 0.0F);
        }
        for (int j = 0; j < 40; j++) {
            long rs = seed * 31L + j * 7919L;
            double ang = j * 2.399963 + Math.floorMod(rs, 100L) * 0.01, v = Math.floorMod(rs * 131L, 1000L) / 1000.0;
            double vh = 0.25 + 0.35 * v, vy = 1.2 + 1.0 * v, t1 = age, t0 = Math.max(0.0, age - 2.0);
            double y1 = vy * t1 - 0.015 * t1 * t1, y0 = vy * t0 - 0.015 * t0 * t0;
            if (y1 < -2.0) continue;
            ClientBlastGeo.streak(bb, cam, x + Math.cos(ang) * (r0 + vh * t1), y + y1, z + Math.sin(ang) * (r0 + vh * t1),
                    x + Math.cos(ang) * (r0 + vh * t0), y + y0, z + Math.sin(ang) * (r0 + vh * t0), 0.5 + 0.4 * v, hr, hg, hb, 1.0F - 0.6F * (float) u, 0.0F);
        }
        ClientBlastGeo.flatDisc(bb, cam, x, y + 0.3, z, r0 * 2.2, 1.0F, 0.5F, 0.15F, 0.6F * (float) (1.0 - u), 24);
        return true;
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (GUNS.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        float best = 0.0F;
        for (Gun g : GUNS.values()) {
            float k = finalK(g.be);
            if (k <= 0) continue;
            double[] c = g.be.worldPoint(0.0, 0.0, 7.0);
            double d = Math.hypot(cam.x - c[0], cam.z - c[2]);
            if (d < Balance.RG_SHOW_DARK_R) best = Math.max(best, (float) (k * (1.0 - d / Balance.RG_SHOW_DARK_R)));
        }
        float dim = brownout(cam);
        if (dim > 0.005F) {
            event.setRed(event.getRed() * (1.0F - dim));
            event.setGreen(event.getGreen() * (1.0F - dim));
            event.setBlue(event.getBlue() * (1.0F - dim));
        }
        if (best <= 0.005F) return;
        float f = 0.7F * best;
        event.setRed(event.getRed() + (0.08F - event.getRed()) * f);
        event.setGreen(event.getGreen() + (0.09F - event.getGreen()) * f);
        event.setBlue(event.getBlue() + (0.14F - event.getBlue()) * f);
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double age = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false) - whiteAt;
        if (age < 0 || age > 40) return;
        float a = whiteMax * (float) (age < 3 ? 1.0 : Math.pow(1.0 - (age - 3) / 37.0, 2.0));
        GuiGraphics gg = event.getGuiGraphics();
        gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), ((int) (255 * Math.min(1.0F, a)) << 24) | 0xF2F6FF);
    }
}
