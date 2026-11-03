package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientColliderFx {

    private ClientColliderFx() {}

    private static final double RC = 96.0, LA = Math.PI / 2 * RC, BY = 3.5, CZ = 16.0;
    private static final double[][] SEGS = { { 0, -136, 24, 1, 0 }, { 1, 136, 120, -90, 0 }, { 0, 232, 120, 0, 1 }, { 1, 136, 392, 0, 0 },
            { 0, 136, 488, -1, 0 }, { 1, -136, 392, 90, 0 }, { 0, -232, 392, 0, -1 }, { 1, -136, 120, 180, 0 } };
    private static final int N = 512;
    private static final double[] PX = new double[N], PZ = new double[N], ANG = new double[N];
    static {
        double total = 0;
        for (double[] s : SEGS) total += s[0] == 0 ? 272.0 : LA;
        for (int i = 0; i < N; i++) {
            double s = total * i / N;
            for (double[] g : SEGS) {
                double L = g[0] == 0 ? 272.0 : LA;
                if (s <= L) {
                    double x, z;
                    if (g[0] == 0) { x = g[1] + g[3] * s; z = g[2] + g[4] * s; }
                    else { double t = Math.toRadians(g[3] + s / LA * 90.0); x = g[1] + RC * Math.cos(t); z = g[2] + RC * Math.sin(t); }
                    PX[i] = x / 16.0; PZ[i] = z / 16.0;
                    ANG[i] = (Math.toDegrees(Math.atan2(PZ[i] - CZ, PX[i])) + 360.0) % 360.0;
                    break;
                }
                s -= L;
            }
        }
    }

    private static final double[][] MAGNETS = new double[16][];
    static {
        int k = 0;
        double[][] corners = { { 136, 120, -90 }, { 136, 392, 0 }, { -136, 392, 90 }, { -136, 120, 180 } };
        for (double[] c : corners)
            for (double d : new double[] { 21.0, 69.0 }) {
                double t = Math.toRadians(c[2] + d);
                MAGNETS[k++] = new double[] { (c[0] + RC * Math.cos(t)) / 16.0, BY, (c[1] + RC * Math.sin(t)) / 16.0, 1 };
            }
        for (double[] g : SEGS)
            if (g[0] == 0)
                for (double u : new double[] { 32.0, 240.0 }) MAGNETS[k++] = new double[] { (g[1] + g[3] * u) / 16.0, BY, (g[2] + g[4] * u) / 16.0, 0 };
    }
    private static final double[][] RF = { { -14.5, BY, 14.75 }, { -14.5, BY, 17.25 } }, CAPS = { { -4.375, 5.85, 30.5 }, { 4.375, 5.85, 30.5 } };
    private static final double[] COOLER = { 0.0, BY, 30.5 }, TARGET = { 0.0, 5.6, 3.75 }, HALL = { 0.0, 4.0, 3.75 };
    private static final double[][] WINDOWS = { { -4.6, 5.6, 5.5, -1 }, { 4.6, 5.6, 5.5, 1 } }, ROOF = { { -2.5, 9.1, 2.0 }, { 2.5, 9.1, 5.5 } };

    static final int SHOT_AT = 25, SHOT_LEN = 6;

    private static double bunchAngle(double tt) { return (22.5 + 45.0 * ((tt * 0.2) % 8.0) + 20.0) % 360.0; }

    private static int indexAt(double ang) {
        int best = 0;
        double bd = 999;
        for (int i = 0; i < N; i++) {
            double d = Math.abs(Mth.wrapDegrees(ANG[i] - ang));
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private static final Map<BlockPos, AntimatterColliderBlockEntity> LIVE = new HashMap<>();
    private static final Map<BlockPos, Long> SEEN = new HashMap<>();

    private static void sound(Level level, double[] p, SoundEvent e, float vol, float pitch) {
        level.playLocalSound(p[0], p[1], p[2], e, SoundSource.BLOCKS, vol, pitch, false);
    }

    static void tick(AntimatterColliderBlockEntity be, Level level) {
        if (ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        long t = level.getGameTime();
        LIVE.put(be.getBlockPos(), be);
        SEEN.put(be.getBlockPos(), t);
        long key = be.getBlockPos().asLong() ^ 0x414D_4300_0000_0000L;
        RandomSource rnd = level.random;
        double ang = bunchAngle(t);
        int i = indexAt(ang);
        double[] b = be.worldPoint(PX[i], BY, PZ[i]);
        VeilBridge.glow(key, b[0], b[1], b[2], 0.75F, 0.45F, 1.0F, 7.0F, 2.2F, 0.4F, t);

        for (int m = 0; m < MAGNETS.length; m++) {
            double[] mg = MAGNETS[m];
            double ma = (Math.toDegrees(Math.atan2(mg[2] - CZ, mg[0])) + 360.0) % 360.0, d = Math.abs(Mth.wrapDegrees(ma - ang));
            if (d > 18.0) continue;
            float k = (float) (1.0 - d / 18.0);
            double[] q = be.worldPoint(mg[0], mg[1] + 0.6, mg[2]);
            if (mg[3] == 1) VeilBridge.glow(key + 1 + m, q[0], q[1], q[2], 0.35F, 0.55F, 1.0F, 6.0F, 2.4F * k, 0.3F, t);
            else VeilBridge.glow(key + 1 + m, q[0], q[1], q[2], 0.3F, 0.6F, 1.0F, 4.5F, 1.8F * k, 0.3F, t);
            if (k > 0.9F && rnd.nextFloat() < 0.25F)
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, q[0] + (rnd.nextDouble() - 0.5) * 1.5, q[1] + 0.6, q[2] + (rnd.nextDouble() - 0.5) * 1.5,
                        (rnd.nextDouble() - 0.5) * 0.2, 0.05 + rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.2);
        }

        boolean rf = t % 10 < 7;
        for (int r = 0; r < 2; r++) {
            double[] q = be.worldPoint(RF[r][0], RF[r][1], RF[r][2]);
            if (rf) VeilBridge.machineGlow(key + 20 + r, q[0], q[1], q[2], 0.3F, 0.55F, 1.0F, 4.0F, 1.3F, 0.4F, t);
            if (rnd.nextFloat() < 0.03F) {
                double[] c = be.worldPoint(RF[r][0], 5.1, RF[r][2]);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, c[0], c[1], c[2], (rnd.nextDouble() - 0.5) * 0.25, rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.25);
            }
        }

        double[] c = be.worldPoint(COOLER[0], COOLER[1], COOLER[2]);
        VeilBridge.machineGlow(key + 30, c[0], c[1], c[2], 0.3F, 0.6F, 1.0F, 6.0F, 0.9F + 0.5F * Mth.sin(t * 0.105F), 0.5F, t);
        if (t % 30 < 8 && t % 2 == 0)
            for (double[] cp : CAPS) {
                double[] q = be.worldPoint(cp[0] + (rnd.nextDouble() - 0.5) * 0.4, cp[1], cp[2] + (rnd.nextDouble() - 0.5) * 0.4);
                level.addParticle(ParticleTypes.CLOUD, q[0], q[1], q[2], (rnd.nextDouble() - 0.5) * 0.02, 0.08 + rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.02);
            }

        if (Math.floorMod(t - SHOT_AT, 40L) == 0) {
            double[] q = be.worldPoint(TARGET[0], TARGET[1], TARGET[2]);
            VeilBridge.flash(q[0], q[1], q[2], 0.8F, 0.4F, 1.0F, 12.0F, 4.0F, 0.5F, 8);
            sound(level, q, WmdSounds.AM_BEAM_TICK.get(), 0.6F, 0.9F + rnd.nextFloat() * 0.2F);
            for (double[] w : WINDOWS) {
                double[] o = be.worldPoint(w[0], w[1], w[2]);
                double[] out = be.worldPoint(w[0] + w[3], w[1], w[2]);
                double ox = out[0] - o[0], oz = out[2] - o[2];
                for (int s = 0; s < 6; s++)
                    level.addParticle(s % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o[0], o[1] + (rnd.nextDouble() - 0.5) * 1.2, o[2] + (rnd.nextDouble() - 0.5) * 2.0,
                            ox * (0.15 + rnd.nextDouble() * 0.2), (rnd.nextDouble() - 0.3) * 0.15, oz * (0.15 + rnd.nextDouble() * 0.2));
            }
        }
    }

    static void started(AntimatterColliderBlockEntity be, Level level) {
        double[] h = be.worldPoint(HALL[0], HALL[1], HALL[2]);
        VeilBridge.flash(h[0], h[1], h[2], 0.6F, 0.45F, 1.0F, 16.0F, 4.0F, 0.5F, 12);
        sound(level, h, WmdSounds.AM_INJECT.get(), 1.5F, 1.0F);
    }

    static void stopped(AntimatterColliderBlockEntity be, Level level, boolean cellDone) {
        LIVE.remove(be.getBlockPos());
        if (!cellDone) return;
        RandomSource rnd = level.random;
        double[] h = be.worldPoint(HALL[0], HALL[1], HALL[2]);
        VeilBridge.flash(h[0], h[1], h[2], 0.85F, 0.45F, 1.0F, 36.0F, 8.0F, 0.7F, 30);
        VeilBridge.bloomNear(h[0], h[1], h[2], 40.0, 0.6F);
        sound(level, h, WmdSounds.AM_CELL_DONE.get(), 2.0F, 1.0F);
        for (double[] v : ROOF) {
            double[] q = be.worldPoint(v[0], v[1], v[2]);
            for (int s = 0; s < 30; s++)
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, q[0] + (rnd.nextDouble() - 0.5) * 1.8, q[1], q[2] + (rnd.nextDouble() - 0.5) * 1.8,
                        (rnd.nextDouble() - 0.5) * 0.15, 0.25 + rnd.nextDouble() * 0.35, (rnd.nextDouble() - 0.5) * 0.15);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { LIVE.clear(); return; }
        long tick = level.getGameTime();
        for (Iterator<Map.Entry<BlockPos, AntimatterColliderBlockEntity>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().isRemoved() || e.getValue().getLevel() != level || tick - SEEN.getOrDefault(e.getKey(), 0L) > 5) it.remove();
        }
        if (LIVE.isEmpty()) return;
        ClientBlastGeo.beginFrame();
        Vec3 cam = event.getCamera().getPosition();
        double now = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (AntimatterColliderBlockEntity be : LIVE.values()) {
            double[] o = be.worldPoint(0.0, 0.0, CZ);
            if (Math.hypot(cam.x - o[0], cam.z - o[2]) > 160.0) continue;
            int head = indexAt(bunchAngle(now)), tail = 70;
            double[] prev = null;
            for (int j = tail; j >= 0; j--) {
                int i = Math.floorMod(head - j, N);
                double[] p = be.worldPoint(PX[i], BY, PZ[i]);
                if (prev != null) {
                    float a = (float) Math.pow(1.0 - j / (double) tail, 1.6);
                    ClientBlastGeo.streak(bb, cam, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.75 + 0.35 * a, 0.65F, 0.35F, 1.0F, 0.35F * a, 0.35F * a);
                    ClientBlastGeo.streak(bb, cam, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.62, 0.95F, 0.85F, 1.0F, 0.55F * a * a, 0.55F * a * a);
                }
                prev = p;
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }
}
