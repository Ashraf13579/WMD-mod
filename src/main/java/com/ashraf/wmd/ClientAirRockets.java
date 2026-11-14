package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAirRockets {

    private ClientAirRockets() {}

    static final class Shot {
        final long id;
        Vec3 pos, vel, drawn;
        boolean burning, lead;
        long seen;
        Object loop;
        final Deque<Vec3> trail = new ArrayDeque<>();
        Shot(long id) { this.id = id; }
    }

    static final Map<Long, Shot> SHOTS = new ConcurrentHashMap<>();
    static final int TRAIL_KEEP = 16;
    private static final long KEY = 0x7A52_0000_0000_0000L;
    private static final double DRAW_RANGE = 640.0;

    private static final float RX = 1.95F / 16.0F, RY = 9.98F / 16.0F, RZ = 10.15F / 16.0F;

    public static void update(AirRocketPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        for (AirRocketPayload.Entry e : p.shots()) {
            boolean neu = !SHOTS.containsKey(e.id());
            Shot s = SHOTS.computeIfAbsent(e.id(), Shot::new);
            s.pos = new Vec3(e.x(), e.y(), e.z());
            s.vel = new Vec3(e.vx(), e.vy(), e.vz());
            s.burning = e.burning();
            s.lead = e.lead();
            s.seen = now;
            if (neu && e.fresh()) launchFx(level, s.pos, s.vel);
        }
    }

    private static void launchFx(ClientLevel level, Vec3 at, Vec3 v) {
        RandomSource r = level.random;
        Vec3 back = v.lengthSqr() < 1.0E-6 ? Vec3.ZERO : v.normalize().scale(-1.0);
        VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.75F, 0.4F, 6.0F, 1.6F, 3);
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(at) > ClientConfig.trailRange()) return;

        for (int i = 0; i < 4; i++)
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true,
                    at.x + back.x * (1.0 + i * 0.25) + r.nextGaussian() * 0.15, at.y + back.y * (1.0 + i * 0.25) + r.nextGaussian() * 0.15,
                    at.z + back.z * (1.0 + i * 0.25) + r.nextGaussian() * 0.15, back.x * 0.15 + r.nextGaussian() * 0.03, back.y * 0.15 + r.nextGaussian() * 0.03,
                    back.z * 0.15 + r.nextGaussian() * 0.03);
        WmdSmokeParticle.nextSize = (float) Balance.RPOD_LAUNCH_SMOKE_SIZE;
        try {
            for (int i = 0; i < Balance.RPOD_LAUNCH_SMOKE; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, at.x + back.x * 1.2 + r.nextGaussian() * 0.15, at.y + back.y * 1.2 + r.nextGaussian() * 0.15,
                        at.z + back.z * 1.2 + r.nextGaussian() * 0.15, back.x * 0.15, back.y * 0.15, back.z * 0.15);
        } finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    static Vec3 position(Shot s, double t) {
        double dt = Math.max(0.0, Math.min(3.0, t - s.seen));
        return s.pos.add(s.vel.scale(dt));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { if (!SHOTS.isEmpty()) SHOTS.clear(); return; }
        if (SHOTS.isEmpty()) return;
        long now = level.getGameTime();
        RandomSource r = level.random;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        SHOTS.values().removeIf(s -> now - s.seen > 3 || now < s.seen - 40);
        for (Shot s : SHOTS.values()) {
            Vec3 p = position(s, now);
            Vec3 from = s.drawn == null ? p : s.drawn;
            s.drawn = p;
            s.trail.addFirst(p);
            while (s.trail.size() > TRAIL_KEEP) s.trail.removeLast();
            Vec3 back = s.vel.lengthSqr() < 1.0E-6 ? Vec3.ZERO : s.vel.normalize().scale(-1.0);
            Vec3 tail = p.add(back.scale(0.8));
            if (p.distanceTo(cam) > ClientConfig.trailRange()) continue;
            int n = Math.max(1, (int) Math.ceil(from.distanceTo(p) / (s.burning ? 1.0 : 2.0)));
            WmdSmokeParticle.nextSize = (float) Balance.RPOD_TRAIL_SIZE;
            try {
                for (int i = 0; i < n; i++) {
                    Vec3 q = from.add(p.subtract(from).scale(i / (double) n)).add(back.scale(0.8));
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, q.x + r.nextGaussian() * 0.08, q.y + r.nextGaussian() * 0.08, q.z + r.nextGaussian() * 0.08,
                            0.0, 0.0, 0.0);
                }
            } finally { WmdSmokeParticle.nextSize = 1.0F; }
            if (s.burning) {
                for (int i = 0; i < 2; i++)
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + r.nextGaussian() * 0.06, tail.y + r.nextGaussian() * 0.06, tail.z + r.nextGaussian() * 0.06,
                            back.x * 0.25, back.y * 0.25, back.z * 0.25);
                VeilBridge.glow(KEY ^ s.id, tail.x, tail.y, tail.z, 1.0F, 0.66F, 0.3F, 6.0F, 1.2F, now);
            }
            if (s.lead && s.burning) {
                final Shot ss = s;
                s.loop = LoopSound.keep(s.loop, now, WmdSounds.RP_FLIGHT_LOOP.get(), Balance.SND_SMALL, 1.0F,
                        () -> ClientAirRockets.position(ss, Minecraft.getInstance().level == null ? ss.seen : Minecraft.getInstance().level.getGameTime()),
                        () -> ss.burning && SHOTS.containsKey(ss.id));
            }
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SHOTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        MeshModel mm = MeshModel.named("rocket_pod");
        if (!mm.has("rocket0")) return;
        double t = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Shot s : SHOTS.values()) {
            Vec3 p = position(s, t);
            if (p.distanceTo(cam) > DRAW_RANGE) continue;
            Vec3 d = s.vel.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, -1) : s.vel.normalize();
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, -1.0F, (float) d.x, (float) d.y, (float) d.z));
            ps.translate(-RX, -RY, -RZ);
            int light = s.burning ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
            mm.draw("rocket0", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }
}
