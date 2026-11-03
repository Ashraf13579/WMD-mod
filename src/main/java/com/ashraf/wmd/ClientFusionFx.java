package com.ashraf.wmd;

import java.util.Iterator;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFusionFx {

    private ClientFusionFx() {}

    private static final double CX = 0.0, CY = 6.0, CZ = 8.0, BELT_R = 5.9, COIL_R = 6.75;
    private static final double[][] TANKS = { { 6.5, 3.4, 1.25 }, { 7.5, 3.4, 3.0 } };
    private static final double[] COLS = { 22.5, 112.5, 202.5, 292.5 }, NBI = { 45.0, 225.0 };

    private static double[] at(FusionReactorBlockEntity be, double r, double angDeg, double y) {
        double a = Math.toRadians(angDeg);
        return be.worldPoint(CX + r * Math.cos(a), y, CZ + r * Math.sin(a));
    }

    private static void sound(Level level, double[] p, SoundEvent e, float vol, float pitch) {
        level.playLocalSound(p[0], p[1], p[2], e, SoundSource.BLOCKS, vol, pitch, false);
    }

    static void tickReactor(FusionReactorBlockEntity be, Level level) {
        long t = level.getGameTime();
        int st = be.state();
        if (st != be.fxState) {
            int prev = be.fxState;
            be.fxState = st;
            if (prev >= 0) onEnter(be, level, prev, st);
        }
        if (ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        long key = be.getBlockPos().asLong() ^ 0x4655_5349_0000_0000L;
        float p = Math.max(0.1F, be.plasmaPct() / 100.0F);
        var rnd = level.random;
        double[] c = be.worldPoint(CX, CY, CZ);
        switch (st) {
            case FusionReactorBlockEntity.COOLDOWN -> {
                frost(be, level, rnd, 2, true);
                for (int i = 0; i < 2; i++) {
                    double[] tk = be.worldPoint(TANKS[i][0], TANKS[i][1], TANKS[i][2]);
                    VeilBridge.machineGlow(key + 20 + i, tk[0], tk[1], tk[2], 0.55F, 0.8F, 1.0F, 3.0F, 1.2F, 0.5F, t);
                }
            }
            case FusionReactorBlockEntity.ENERGISING -> {
                int lit = Math.min(16, be.chargePermille() * 16 / 1000);
                for (int k = be.fxLit; k < lit; k++) {
                    double[] q = at(be, COIL_R, 11.25 + 22.5 * k, 6.0);
                    VeilBridge.flash(q[0], q[1], q[2], 0.35F, 0.6F, 1.0F, 6.0F, 3.0F, 0.4F, 8);
                    sound(level, q, WmdSounds.FUSION_COIL.get(), 1.2F, 0.75F + 0.04F * k);
                }
                be.fxLit = lit;
                float ch = be.chargePermille() / 1000.0F;
                VeilBridge.machineGlow(key, c[0], c[1], c[2], 0.3F, 0.5F, 1.0F, 7.0F, 0.4F + 1.4F * ch, 0.5F, t);
                frost(be, level, rnd, 8, false);
            }
            case FusionReactorBlockEntity.IGNITION -> {
                float k = Mth.clamp(be.stateProgress(), 0.0F, 1.0F);
                VeilBridge.machineGlow(key, c[0], c[1], c[2], 0.8F + 0.2F * k, 0.75F, 1.0F, 6.0F + 4.0F * k, 0.8F + 3.0F * k * k, 0.7F, t);
                if (k > 0.7F) injectors(be, key, t, 1.6F);
            }
            case FusionReactorBlockEntity.RAMP, FusionReactorBlockEntity.RUNNING, FusionReactorBlockEntity.SHUTDOWN -> running(be, level, key, c, p, t, rnd);
            case FusionReactorBlockEntity.QUENCH -> {
                boolean on = t % 10 < 5;
                for (int i = 0; i < 4; i++) {
                    double[] q = at(be, 8.0, COLS[i], 10.5);
                    if (on) VeilBridge.machineGlow(key + 30 + i, q[0], q[1], q[2], 1.0F, 0.12F, 0.08F, 6.0F, 2.2F, 0.5F, t);
                }
                if (be.stateTicks() < 60) {
                    for (int i = 0; i < 2; i++) {
                        double[] tk = be.worldPoint(TANKS[i][0], TANKS[i][1] + 0.6, TANKS[i][2]);
                        for (int j = 0; j < 3; j++)
                            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, tk[0] + (rnd.nextDouble() - 0.5) * 0.3, tk[1], tk[2] + (rnd.nextDouble() - 0.5) * 0.3,
                                    (rnd.nextDouble() - 0.5) * 0.08, 0.45 + rnd.nextDouble() * 0.25, (rnd.nextDouble() - 0.5) * 0.08);
                    }
                }
                frost(be, level, rnd, 6, false);
            }
            default -> {
                if (st == FusionReactorBlockEntity.COLD && be.fxLit > 0) be.fxLit = 0;
            }
        }
        if (st != FusionReactorBlockEntity.ENERGISING && st != FusionReactorBlockEntity.COLD) be.fxLit = 16;
    }

    private static void running(FusionReactorBlockEntity be, Level level, long key, double[] c, float p, long t, RandomSource rnd) {

        float r = 0.55F + 0.45F * p, g = 0.18F + 0.5F * p * p, b = 1.0F;
        float breathe = 0.74F + 0.26F * Mth.sin(t * 0.157F);
        VeilBridge.machineGlow(key, c[0], c[1], c[2], r, g, b, 9.0F, (0.8F + 1.8F * p) * breathe, 0.6F, t);

        be.fxOrbit += 0.035 + 0.2 * p;
        if (be.fxOrbit > Math.PI * 200) be.fxOrbit -= Math.PI * 200;
        for (int i = 0; i < 3; i++) {
            double a = Math.toDegrees(be.fxOrbit) + i * 120.0;
            double[] q = at(be, BELT_R, a, CY);
            VeilBridge.glow(key + 1 + i, q[0], q[1], q[2], r, g, b, 3.5F, (1.0F + 2.0F * p) * (0.85F + 0.15F * breathe), 0.4F, t);
        }

        int k = (int) ((t * (0.25 + 0.5 * p)) % 16);
        double[] q = at(be, COIL_R + 0.3, 11.25 + 22.5 * k, CY);
        VeilBridge.glow(key + 5, q[0], q[1], q[2], 0.35F, 0.6F, 1.0F, 3.0F, 1.6F, 0.3F, t);
        injectors(be, key, t, 1.0F + 0.6F * p);

        VeilBridge.glow(key + 9, c[0], c[1], c[2], 0.45F, 0.45F, 1.0F, 15.0F, (0.18F + 0.3F * p) * (0.7F + 0.3F * rnd.nextFloat()), 0.35F, t);
        VeilBridge.haze(c[0], c[1] + 6.8, c[2], 7.0, 64.0, 0.2F + 0.5F * p);
        VeilBridge.bloomNear(c[0], c[1], c[2], 40.0, 0.2F + 0.45F * p);
        frost(be, level, rnd, 14, false);

        if (rnd.nextFloat() < 0.15F * p) {
            double[] s = at(be, BELT_R + 0.3, rnd.nextDouble() * 360.0, CY + (rnd.nextDouble() - 0.5) * 2.0);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, s[0], s[1], s[2], (rnd.nextDouble() - 0.5) * 0.3, rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.3);
        }
    }

    private static void injectors(FusionReactorBlockEntity be, long key, long t, float bright) {
        for (int i = 0; i < 2; i++) {
            double[] q = at(be, 7.0, NBI[i], 6.6);
            VeilBridge.glow(key + 6 + i, q[0], q[1], q[2], 0.3F, 0.95F, 1.0F, 2.6F, bright * (0.9F + 0.1F * Mth.sin(t * 0.9F + i)), 0.3F, t);
        }
    }

    private static void frost(FusionReactorBlockEntity be, Level level, RandomSource rnd, int every, boolean heavy) {
        if (level.getGameTime() % every != 0) return;
        for (double[] tk : TANKS) {
            double[] w = be.worldPoint(tk[0] + (rnd.nextDouble() - 0.5) * 1.2, tk[1] - rnd.nextDouble() * 2.0, tk[2] + (rnd.nextDouble() - 0.5) * 1.2);
            level.addParticle(heavy ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, w[0], w[1], w[2], (rnd.nextDouble() - 0.5) * 0.02, -0.02 - rnd.nextDouble() * 0.02, (rnd.nextDouble() - 0.5) * 0.02);
        }
    }

    private static void onEnter(FusionReactorBlockEntity be, Level level, int prev, int st) {
        double[] c = be.worldPoint(CX, CY, CZ), g = be.worldPoint(CX, 0.3, CZ);
        switch (st) {
            case FusionReactorBlockEntity.COOLDOWN -> sound(level, c, WmdSounds.FUSION_COOLDOWN.get(), 2.0F, 1.0F);
            case FusionReactorBlockEntity.IGNITION -> sound(level, c, WmdSounds.FUSION_CHARGE.get(), 2.5F, 1.0F);
            case FusionReactorBlockEntity.RAMP -> {
                if (prev != FusionReactorBlockEntity.IGNITION) return;

                VeilBridge.flash(c[0], c[1], c[2], 0.95F, 0.9F, 1.0F, 48.0F, 9.0F, 0.7F, 26);
                VeilBridge.shock(g[0], g[1], g[2]);
                sound(level, c, WmdSounds.FUSION_IGNITE.get(), 4.0F, 1.0F);
            }
            case FusionReactorBlockEntity.QUENCH -> {
                VeilBridge.flash(c[0], c[1], c[2], 1.0F, 0.3F, 0.3F, 30.0F, 5.0F, 0.5F, 14);
                sound(level, c, WmdSounds.FUSION_QUENCH.get(), 4.0F, 1.0F);
            }
            case FusionReactorBlockEntity.SHUTDOWN -> sound(level, c, WmdSounds.FUSION_SHUTDOWN.get(), 2.5F, 1.0F);
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { FusionArcs.LIVE.clear(); return; }
        if (FusionArcs.LIVE.isEmpty()) return;
        long t = level.getGameTime();
        for (Iterator<FusionArcs.Live> it = FusionArcs.LIVE.iterator(); it.hasNext(); ) {
            FusionArcs.Live l = it.next();
            if (l.born < 0) {
                l.born = t;
                FusionArcPayload.Arc a = l.arc;
                double[] e = { a.x1(), a.y1(), a.z1() };
                if (a.kind() == 0) VeilBridge.flash(e[0], e[1], e[2], 0.55F, 0.75F, 1.0F, 5.0F, 2.0F, 0.3F, 4);
                else if (a.kind() == 1) {
                    VeilBridge.flash(e[0], e[1], e[2], 0.6F, 0.75F, 1.0F, 9.0F, 3.5F, 0.4F, 6);
                    sound(level, e, WmdSounds.FUSION_ARC.get(), 0.8F, 0.85F + level.random.nextFloat() * 0.3F);
                } else {
                    VeilBridge.flash(e[0], e[1], e[2], 0.7F, 0.8F, 1.0F, 16.0F, 6.0F, 0.5F, 10);
                    VeilBridge.flash((a.x0() + e[0]) / 2, (a.y0() + e[1]) / 2, (a.z0() + e[2]) / 2, 0.6F, 0.75F, 1.0F, 12.0F, 4.0F, 0.5F, 8);
                    sound(level, e, WmdSounds.FUSION_DISCHARGE.get(), 2.5F, 0.95F + level.random.nextFloat() * 0.1F);
                }
            } else if (t - l.born > l.life()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FusionArcs.LIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        long tick = level.getGameTime();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (FusionArcs.Live l : FusionArcs.LIVE) {
            if (l.born < 0) continue;
            long age = tick - l.born;
            if (age > l.life()) continue;
            any |= arc(bb, cam, left, up, l.arc, age, l.life(), tick);
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static boolean arc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, FusionArcPayload.Arc a, long age, int life, long tick) {
        RandomSource rnd = RandomSource.create(a.seed() ^ (tick / 2) * 0x5DEECE66DL);
        float al = (1.0F - age / (float) (life + 1)) * (0.75F + 0.25F * rnd.nextFloat());
        double w = a.kind() == 0 ? 0.05 : a.kind() == 1 ? 0.09 : 0.16;
        double dx = a.x1() - a.x0(), dy = a.y1() - a.y0(), dz = a.z1() - a.z0(), len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double jit = len * (a.kind() == 2 ? 0.1 : 0.08);
        int n = a.kind() == 0 ? 6 : 10;
        double px = a.x0(), py = a.y0(), pz = a.z0();
        for (int i = 1; i <= n; i++) {
            double k = i / (double) n, env = Math.sin(Math.PI * k);
            double nx = a.x0() + dx * k + (i < n ? (rnd.nextDouble() - 0.5) * 2 * jit * env : 0);
            double ny = a.y0() + dy * k + (i < n ? (rnd.nextDouble() - 0.5) * 2 * jit * env : 0);
            double nz = a.z0() + dz * k + (i < n ? (rnd.nextDouble() - 0.5) * 2 * jit * env : 0);
            bolt(bb, cam, px, py, pz, nx, ny, nz, w, al);
            if ((i == 3 || i == 6) && a.kind() > 0 && rnd.nextFloat() < 0.65F) {
                double fx = nx, fy = ny, fz = nz;
                double fdx = dx / len + (rnd.nextDouble() - 0.5) * 1.4, fdy = (rnd.nextDouble() - 0.6), fdz = dz / len + (rnd.nextDouble() - 0.5) * 1.4;
                for (int f = 0; f < 3; f++) {
                    double s = len * 0.09;
                    double gx = fx + fdx * s + (rnd.nextDouble() - 0.5) * s * 0.6, gy = fy + fdy * s, gz = fz + fdz * s + (rnd.nextDouble() - 0.5) * s * 0.6;
                    bolt(bb, cam, fx, fy, fz, gx, gy, gz, w * 0.55, al * (0.6F - 0.15F * f));
                    fx = gx; fy = gy; fz = gz;
                }
            }
            px = nx; py = ny; pz = nz;
        }
        double puff = a.kind() == 0 ? 0.5 : a.kind() == 1 ? 1.1 : 2.0;
        ClientBlastGeo.disc(bb, cam, left, up, a.x1(), a.y1(), a.z1(), puff, 0.55F, 0.7F, 1.0F, 0.55F * al, 10);
        ClientBlastGeo.disc(bb, cam, left, up, a.x0(), a.y0(), a.z0(), puff * 0.5, 0.75F, 0.85F, 1.0F, 0.6F * al, 8);
        return true;
    }

    private static void bolt(BufferBuilder bb, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double w, float a) {
        double mx = (x0 + x1) * 0.5 - cam.x, my = (y0 + y1) * 0.5 - cam.y, mz = (z0 + z1) * 0.5 - cam.z;
        double ww = w * Math.max(1.0, Math.sqrt(mx * mx + my * my + mz * mz) / 48.0);
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, ww * 3.5, 0.4F, 0.6F, 1.0F, a * 0.35F, a * 0.35F);
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, ww, 0.88F, 0.95F, 1.0F, a, a);
    }
}
