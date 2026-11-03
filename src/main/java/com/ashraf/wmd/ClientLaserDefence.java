package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientLaserDefence {

    private ClientLaserDefence() {}

    private static final int PULSE_MIN = 8, PULSE_MAX = 12, PULSES = 4;
    private static final double TRAVEL = 3.0, FADE = 7.0;

    private static final Map<BlockPos, LaserDefenceBlockEntity> LIVE = new HashMap<>();

    private static final Map<BlockPos, double[]> FX = new HashMap<>();

    static void track(LaserDefenceBlockEntity be) { LIVE.put(be.getBlockPos().immutable(), be); }

    static float kick(BlockPos pos) { double[] f = FX.get(pos); return f == null ? 0.0F : (float) f[2]; }

    static float flash(BlockPos pos, double now) {
        double[] f = FX.get(pos);
        if (f == null) return 0.0F;
        double best = 0.0;
        for (int i = 0; i < PULSES; i++) best = Math.max(best, 1.0 - (now - f[3 + i]) / 3.0);
        return (float) Math.max(0.0, Math.min(1.0, best));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); FX.clear(); return; }
        LIVE.values().removeIf(be -> be.isRemoved() || be.getLevel() != mc.level);
        FX.keySet().removeIf(p -> !LIVE.containsKey(p));
        long now = mc.level.getGameTime();
        var r = mc.level.random;
        for (LaserDefenceBlockEntity be : LIVE.values()) {
            double[] f = FX.computeIfAbsent(be.getBlockPos(), k -> { double[] a = new double[3 + PULSES]; java.util.Arrays.fill(a, 3, a.length, -1e9); return a; });
            f[1] = Math.max(0.0, f[1] - 0.004);
            f[2] *= 0.6;
            Vec3 a = be.muzzle(), p = be.aim();
            for (int i = 0; i < PULSES; i++) {
                double age = now - f[3 + i];
                if (age >= TRAVEL && age < TRAVEL + 1.0 && be.firing()) {
                    for (int k = 0; k < 12; k++) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5);
                    for (int k = 0; k < 3; k++) mc.level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, r.nextGaussian() * 0.15, r.nextGaussian() * 0.15, r.nextGaussian() * 0.15);
                    if (r.nextInt(2) == 0) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0.0, 0.06, 0.0);
                    VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.75F, 0.45F, 14.0F, 2.5F, 6);
                }
            }
            if (!be.firing()) { f[0] = now + 4; continue; }
            if (now >= f[0]) {
                int slot = 0;
                for (int i = 1; i < PULSES; i++) if (f[3 + i] < f[3 + slot]) slot = i;
                f[3 + slot] = now;
                f[0] = now + PULSE_MIN + r.nextInt(PULSE_MAX - PULSE_MIN + 1);
                f[1] = Math.min(1.0, f[1] + 0.06);
                f[2] = 1.0;
                mc.level.playLocalSound(a.x, a.y, a.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.6F, 1.7F + r.nextFloat() * 0.25F, false);
                for (int k = 0; k < 4; k++) mc.level.addParticle(ParticleTypes.END_ROD, a.x, a.y, a.z, r.nextGaussian() * 0.04, r.nextGaussian() * 0.04, r.nextGaussian() * 0.04);
            }
            if (f[1] > 0.5 && r.nextFloat() < f[1] * 0.3) {
                Vec3 br = breech(be);
                mc.level.addParticle(ParticleTypes.SMOKE, br.x + r.nextGaussian() * 0.2, br.y + 0.2, br.z + r.nextGaussian() * 0.2, 0.0, 0.03, 0.0);
            }
        }
    }

    private static Vec3 breech(LaserDefenceBlockEntity be) {
        Vec3 piv = be.pivot(), m = be.muzzle();
        return piv.add(piv.subtract(m).scale(0.7));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (LaserDefenceBlockEntity be : LIVE.values()) {
            double[] f = FX.get(be.getBlockPos());
            if (f == null) continue;
            if (f[1] > 0.05) {
                Vec3 br = breech(be);
                VeilBridge.glow(0x4C44000000000000L + be.getBlockPos().asLong(), br.x, br.y, br.z, 1.0F, 0.45F, 0.15F, 4.0F, (float) (2.5 * f[1]), mc.level.getGameTime());
            }
            if (!be.firing()) continue;
            Vec3 a = be.muzzle(), b = be.aim();
            double len = a.distanceTo(b);
            if (len < 1.0) continue;
            Vec3 d = b.subtract(a).scale(1.0 / len);
            Vec3 side = d.cross(cam.subtract(a.add(b).scale(0.5))).normalize();
            ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.08, 0.45F, 0.85F, 1.0F, 0.35F, 0.2F);
            double ch = Math.max(0.0, 1.0 - (f[0] - now) / PULSE_MIN);
            ClientBlastGeo.disc(bb, cam, left, up, a.x, a.y, a.z, 0.15 + 0.45 * ch, 0.55F, 0.9F, 1.0F, (float) (0.2 + 0.6 * ch), 12);
            for (int i = 0; i < PULSES; i++) {
                double age = now - f[3 + i];
                if (age < 0.0 || age > TRAVEL + FADE) continue;
                double head = len * Math.min(1.0, age / TRAVEL), fade = age < TRAVEL ? 1.0 : 1.0 - (age - TRAVEL) / FADE;
                int n = Math.max(2, (int) Math.min(24, head / 6.0));
                for (int s = 0; s < n; s++) {
                    double s0 = head * s / n, s1 = head * (s + 1) / n;
                    double w0 = Math.sin(now * 0.9 + s0 * 0.35 + i) * 0.22 * fade, w1 = Math.sin(now * 0.9 + s1 * 0.35 + i) * 0.22 * fade;
                    Vec3 p0 = a.add(d.scale(s0)).add(side.scale(w0)), p1 = a.add(d.scale(s1)).add(side.scale(w1));
                    ClientBlastGeo.streak(bb, cam, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.75 * fade + 0.1, 0.25F, 0.78F, 1.0F, (float) (0.4 * fade), (float) (0.4 * fade));
                    ClientBlastGeo.streak(bb, cam, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.16 * fade + 0.04, 0.96F, 0.99F, 1.0F, (float) (0.95 * fade), (float) (0.95 * fade));
                }
                if (age < TRAVEL) {
                    Vec3 h = a.add(d.scale(head));
                    ClientBlastGeo.disc(bb, cam, left, up, h.x, h.y, h.z, 0.9, 1.0F, 1.0F, 1.0F, 0.95F, 14);
                    ClientBlastGeo.disc(bb, cam, left, up, h.x, h.y, h.z, 2.2, 0.4F, 0.85F, 1.0F, 0.35F, 14);
                } else {
                    ClientBlastGeo.disc(bb, cam, left, up, b.x, b.y, b.z, 2.5 * fade + 0.5, 1.0F, 0.6F, 0.25F, (float) (0.7 * fade), 16);
                }
                if (age < 1.5) ClientBlastGeo.disc(bb, cam, left, up, a.x, a.y, a.z, 1.4 * (1.5 - age), 0.85F, 0.97F, 1.0F, 0.9F, 14);
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }
}
