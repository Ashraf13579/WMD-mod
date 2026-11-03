package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientMirror {

    private ClientMirror() {}

    private static final double TOP = 420.0;
    private static final int SHAFTS = 14, SLAM_TICKS = 30, FADE_TICKS = 40;

    private static final Map<Integer, double[]> SHOW = new HashMap<>();

    static double[] pos(Object[] e, double now) {
        MirrorPayloads.Beam p = (MirrorPayloads.Beam) e[0];
        double[] from = (double[]) e[2];
        double k = Math.max(0.0, Math.min(1.0, (now - (long) e[1]) / 20.0));
        if (from == null) return new double[] { p.x(), p.y(), p.z() };
        return new double[] { from[0] + (p.x() - from[0]) * k, from[1] + (p.y() - from[1]) * k, from[2] + (p.z() - from[2]) * k };
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { MirrorPayloads.BEAMS.clear(); SHOW.clear(); }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        SHOW.entrySet().removeIf(en -> !MirrorPayloads.BEAMS.containsKey(en.getKey()) && now - en.getValue()[2] > FADE_TICKS);
        if (MirrorPayloads.BEAMS.isEmpty()) return;
        var rnd = level.random;
        for (Iterator<Map.Entry<Integer, Object[]>> it = MirrorPayloads.BEAMS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Object[]> en = it.next();
            Object[] e = en.getValue();
            if ((long) e[1] < 0) e[1] = now;
            MirrorPayloads.Beam p = (MirrorPayloads.Beam) e[0];
            double[] s = pos(e, now);
            double[] sh = SHOW.computeIfAbsent(en.getKey(), k -> new double[] { -1, Long.MIN_VALUE, Long.MIN_VALUE, 0, 0, 0 });
            if (p.state() == 2 && sh[0] != 2) sh[1] = now;
            if (p.state() != 2 && sh[0] == 2) sh[2] = now;
            sh[0] = p.state(); sh[3] = s[0]; sh[4] = s[1]; sh[5] = s[2];
            if (now - (long) e[1] > 60) { if (sh[0] == 2) sh[2] = now; sh[0] = -1; it.remove(); continue; }
            if (p.state() == 3 && now % 20 == 0)
                level.playLocalSound(s[0], s[1], s[2], SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 8.0F, 0.6F + 0.12F * (float) ((now / 20) % 8), false);
            if (p.state() == 2 && now - (long) sh[1] == SLAM_TICKS) {
                level.playLocalSound(s[0], s[1], s[2], SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0F, 0.6F, false);
                VeilBridge.flash(s[0], s[1] + 3.0, s[2], 1.0F, 0.95F, 0.8F, 90.0F, 9.0F, 30);
                T4Fx.shakeNear(new Vec3(s[0], s[1], s[2]), 260.0, 1.2F);
                for (int i = 0; i < 40; i++) level.addParticle(ParticleTypes.FLASH, s[0] + rnd.nextGaussian() * 2, s[1] + 1, s[2] + rnd.nextGaussian() * 2, 0, 0, 0);
                for (int i = 0; i < 60; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, s[0] + Math.cos(a) * 3, s[1] + 0.5, s[2] + Math.sin(a) * 3, Math.cos(a) * 0.6, 0.02, Math.sin(a) * 0.6);
                }
            }
            if (p.state() != 2 || now - (long) sh[1] < SLAM_TICKS) continue;
            double burnt = now - sh[1], pool = Balance.MIRROR_SPOT_R * Math.min(1.0, 0.35 + burnt / 1200.0);
            for (int i = 0; i < 10; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * pool;
                double x = s[0] + Math.cos(a) * r, z = s[2] + Math.sin(a) * r;
                if (i < 4) level.addParticle(ParticleTypes.END_ROD, x, s[1] + 0.3, z, 0.0, 0.1 + rnd.nextDouble() * 0.15, 0.0);
                if (i < 3) level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, s[1] + 1.0, z, 0.0, 0.12, 0.0);
                if (i < 2) level.addParticle(ParticleTypes.LAVA, x, s[1] + 0.3, z, 0.0, 0.0, 0.0);
                if (i < 3) level.addParticle(ParticleTypes.FLAME, x, s[1] + 0.2, z, 0.0, 0.04, 0.0);
            }
            VeilBridge.haze(s[0], s[1] + 10.0, s[2], Balance.MIRROR_SPOT_R * 2.5, 500.0, 0.9F);
            VeilBridge.bloomNear(s[0], s[1], s[2], 400.0, 0.7F);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (MirrorPayloads.BEAMS.isEmpty() && SHOW.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<Integer, double[]> en : SHOW.entrySet()) {
            double[] sh = en.getValue();
            Object[] e = MirrorPayloads.BEAMS.get(en.getKey());
            double x, y, z;
            if (e != null && (long) e[1] >= 0) { double[] s = pos(e, now); x = s[0]; y = s[1]; z = s[2]; }
            else { x = sh[3]; y = sh[4]; z = sh[5]; }
            if (DhBridge.ready && Math.hypot(cam.x - x, cam.z - z) > ClientBlastGeo.FAR_VIEW) continue;
            int state = (int) sh[0];
            double R = Balance.MIRROR_SPOT_R;
            if (state == 1 && now - sh[2] > FADE_TICKS) {
                ClientBlastGeo.streak(bb, cam, x, y + TOP, z, x, y + 0.2, z, 0.5, 1.0F, 0.85F, 0.5F, 0.0F, 0.35F);
                ClientBlastGeo.disc(bb, cam, left, up, x, y + 0.4, z, 1.5, 1.0F, 0.85F, 0.5F, 0.35F, 12);
                any = true;
                continue;
            }
            if (state == 3) {
                double u = Math.min(1.0, (now % 100) / 100.0), pulse = 0.6 + 0.4 * Math.sin(now * 0.6);
                ClientBlastGeo.disc(bb, cam, left, up, x, y + TOP * 0.7, z, 18.0 + 30.0 * pulse, 1.0F, 0.95F, 0.8F, 0.9F, 24);
                ClientBlastGeo.disc(bb, cam, left, up, x, y + TOP * 0.7, z, 60.0, 1.0F, 0.85F, 0.55F, 0.35F, 24);
                if (((long) now / 3) % 2 == 0) ClientBlastGeo.streak(bb, cam, x, y + TOP * 0.7, z, x, y + 0.2, z, 0.6, 1.0F, 0.85F, 0.35F, 0.2F, 0.7F);
                ClientBlastGeo.flatDisc(bb, cam, x, y + 0.3, z, R * (1.4 - 0.4 * u), 1.0F, 0.75F, 0.25F, (float) (0.5 * pulse), 24);
                any = true;
                continue;
            }

            double slam = state == 2 ? Math.min(1.0, (now - sh[1]) / SLAM_TICKS) : 1.0;
            double fade = state == 2 ? 1.0 : Math.max(0.0, 1.0 - (now - sh[2]) / FADE_TICKS);
            if (fade <= 0.0) continue;
            double front = y + TOP * (1.0 - slam);
            float flick = 0.85F + 0.15F * (float) Math.sin(now * 0.9 + en.getKey());
            for (int k = 0; k < SHAFTS; k++) {
                double a = Math.PI * 2 * k / SHAFTS + now * 0.004, top = R * 6.0, bot = R * 0.35;
                double shimmer = 0.5 + 0.5 * Math.sin(now * 0.13 + k * 1.7);
                double tx = x + Math.cos(a) * top, tz = z + Math.sin(a) * top, bx = x + Math.cos(a) * bot, bz = z + Math.sin(a) * bot;
                double f = (front - y) / TOP, ex = bx + (tx - bx) * f, ez = bz + (tz - bz) * f;
                ClientBlastGeo.streak(bb, cam, tx, y + TOP, tz, ex, front + 0.2, ez, R * 0.45, 1.0F, 0.88F, 0.62F,
                        (float) (0.04 * fade), (float) ((0.12 + 0.12 * shimmer) * fade));
            }
            ClientBlastGeo.streak(bb, cam, x, y + TOP, z, x, front + 0.2, z, R * 2.2, 1.0F, 0.72F, 0.32F, (float) (0.05 * fade), (float) (0.32 * flick * fade));
            ClientBlastGeo.streak(bb, cam, x, y + TOP, z, x, front + 0.2, z, R * 0.5, 1.0F, 0.98F, 0.9F, (float) (0.25 * fade), (float) (0.98 * fade));
            for (int k = 0; k < 40; k++) {
                double h = (k * 0.6180339887) % 1.0, fy = 1.0 - ((now * 0.004 + h) % 1.0), my = y + TOP * fy;
                if (my > front) continue;
                double rr = (R * 0.35 + (R * 6.0 - R * 0.35) * fy) * ((k * 0.37) % 1.0), a = k * 2.399 + now * 0.004;
                ClientBlastGeo.disc(bb, cam, left, up, x + Math.cos(a) * rr, my, z + Math.sin(a) * rr, 0.35 + 0.25 * fy, 1.0F, 0.95F, 0.8F, (float) (0.6 * fade), 6);
            }
            if (slam >= 1.0) {
                double burnt = state == 2 ? now - sh[1] : 1200.0, pool = R * Math.min(1.6, 0.5 + burnt / 900.0);
                ClientBlastGeo.flatDisc(bb, cam, x, y + 0.25, z, pool, 1.0F, 0.42F, 0.08F, (float) (0.75 * fade), 28);
                ClientBlastGeo.flatDisc(bb, cam, x, y + 0.3, z, pool * 0.55, 1.0F, 0.72F, 0.2F, (float) (0.85 * fade), 24);
                ClientBlastGeo.disc(bb, cam, left, up, x, y + 0.8, z, R * 0.7, 1.0F, 1.0F, 0.95F, (float) (0.95 * flick * fade), 18);
                ClientBlastGeo.disc(bb, cam, left, up, x, y + 1.5, z, R * 2.6, 1.0F, 0.7F, 0.3F, (float) (0.3 * fade), 18);
                VeilBridge.glow(0x4D49525200000000L + en.getKey(), x, y + 2.0, z, 1.0F, 0.6F, 0.25F, (float) (R * 7.0), (float) (8.0 * flick * fade), level.getGameTime());
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }
}
