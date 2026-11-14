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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAam {

    private ClientAam() {}

    static final class Shot {
        final long id; final int kind;
        Vec3 pos, vel, drawn;
        boolean burning;
        long seen;
        Object loop;
        final Deque<Vec3> trail = new ArrayDeque<>();
        Shot(long id, int kind) { this.id = id; this.kind = kind; }
    }

    static final Map<Long, Shot> SHOTS = new ConcurrentHashMap<>();
    static final int TRAIL_KEEP = 24;
    private static final long KEY = 0x7A4D_0000_0000_0000L;
    private static final double DRAW_RANGE = 1200.0;

    public static void update(AamPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        for (AamPayload.Entry e : p.shots()) {
            Shot s = SHOTS.get(e.id());
            Vec3 at = new Vec3(e.x(), e.y(), e.z());
            if (s == null) {
                s = new Shot(e.id(), e.kind());
                s.drawn = at;
                SHOTS.put(e.id(), s);
                launchFx(level, at, new Vec3(e.vx(), e.vy(), e.vz()));
            }
            s.pos = at;
            s.vel = new Vec3(e.vx(), e.vy(), e.vz());
            s.burning = e.burning();
            s.seen = now;
        }
    }

    public static void burst(AamBurstPayload p) {
        Shot s = SHOTS.remove(p.id());
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 c = new Vec3(p.x(), p.y(), p.z());
        RandomSource r = level.random;
        Minecraft mc = Minecraft.getInstance();
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(c);
        if (p.kill()) {
            VeilBridge.flash(c.x, c.y, c.z, 1.0F, 0.72F, 0.38F, 22.0F, 3.0F, 12);
            level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, c.x, c.y, c.z, 0.0, 0.0, 0.0);
            for (int i = 0; i < 40; i++)
                level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, c.x + r.nextGaussian() * 1.2, c.y + r.nextGaussian() * 1.2, c.z + r.nextGaussian() * 1.2,
                        r.nextGaussian() * 0.25, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25);
            for (int i = 0; i < 24; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, c.x + r.nextGaussian() * 1.6, c.y + r.nextGaussian() * 1.6, c.z + r.nextGaussian() * 1.6,
                        r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08);
            if (d > Balance.AAM_BURST_VOLUME * 16.0)
                level.playLocalSound(c.x, c.y, c.z, WmdSounds.AAM_BURST_FAR.get(), SoundSource.HOSTILE, Balance.AAM_FAR_VOLUME, 0.95F + r.nextFloat() * 0.1F, false);
        } else {
            VeilBridge.flash(c.x, c.y, c.z, 1.0F, 0.8F, 0.5F, 8.0F, 1.2F, 6);
            for (int i = 0; i < 12; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, c.x + r.nextGaussian() * 0.6, c.y + r.nextGaussian() * 0.6, c.z + r.nextGaussian() * 0.6,
                        r.nextGaussian() * 0.04, r.nextGaussian() * 0.04, r.nextGaussian() * 0.04);
        }
    }

    public static void decoy(AamDecoyPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        Vec3 at = new Vec3(p.x(), p.y(), p.z()), v = new Vec3(p.vx(), p.vy(), p.vz());
        if (p.kind() == AirMissiles.FLARE) {

            ClientFlares.salvo(level, at, v);
        } else {
            for (int i = 0; i < 60; i++)
                level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, at.x + r.nextGaussian() * 1.5, at.y + r.nextGaussian() * 1.5, at.z + r.nextGaussian() * 1.5,
                        v.x * 0.3 + r.nextGaussian() * 0.12, v.y * 0.3 + r.nextGaussian() * 0.12, v.z * 0.3 + r.nextGaussian() * 0.12);
            for (int i = 0; i < 14; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, at.x + r.nextGaussian() * 2.0, at.y + r.nextGaussian() * 2.0, at.z + r.nextGaussian() * 2.0,
                        r.nextGaussian() * 0.03, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03);
        }
    }

    private static void launchFx(ClientLevel level, Vec3 at, Vec3 v) {
        RandomSource r = level.random;
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(at) > Balance.SND_RELEASE * 16.0)
            level.playLocalSound(at.x, at.y, at.z, WmdSounds.AAM_LAUNCH_FAR.get(), SoundSource.HOSTILE, Balance.AAM_FAR_VOLUME, 1.0F, false);
        Vec3 back = v.lengthSqr() < 1.0E-6 ? Vec3.ZERO : v.normalize().scale(-1.0);

        for (int i = 0; i < 8; i++)
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true,
                    at.x + back.x * 1.5 + r.nextGaussian() * 0.3, at.y + back.y * 1.5 + r.nextGaussian() * 0.3, at.z + back.z * 1.5 + r.nextGaussian() * 0.3,
                    back.x * 0.2 + r.nextGaussian() * 0.05, back.y * 0.2 + r.nextGaussian() * 0.05, back.z * 0.2 + r.nextGaussian() * 0.05);
        WmdSmokeParticle.nextSize = (float) Balance.AAM_LAUNCH_SMOKE_SIZE;
        try {
            for (int i = 0; i < Balance.AAM_LAUNCH_SMOKE; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true,
                        at.x + back.x * 1.5 + r.nextGaussian() * 0.3, at.y + back.y * 1.5 + r.nextGaussian() * 0.3, at.z + back.z * 1.5 + r.nextGaussian() * 0.3,
                        back.x * 0.2 + r.nextGaussian() * 0.05, back.y * 0.2 + r.nextGaussian() * 0.05, back.z * 0.2 + r.nextGaussian() * 0.05);
        } finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    static Vec3 position(Shot s, double t) {
        double dt = Math.max(0.0, Math.min(5.0, t - s.seen));
        return s.pos.add(s.vel.scale(dt));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { if (!SHOTS.isEmpty()) SHOTS.clear(); return; }
        long now = level.getGameTime();
        if (SHOTS.isEmpty()) return;
        RandomSource r = level.random;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        SHOTS.values().removeIf(s -> now - s.seen > 10 || now < s.seen - 40);
        for (Shot s : SHOTS.values()) {
            Vec3 p = position(s, now);
            Vec3 from = s.drawn == null ? p : s.drawn;
            s.drawn = p;
            s.trail.addFirst(p);
            while (s.trail.size() > TRAIL_KEEP) s.trail.removeLast();
            Vec3 back = s.vel.lengthSqr() < 1.0E-6 ? Vec3.ZERO : s.vel.normalize().scale(-1.0);
            Vec3 tail = p.add(back.scale(1.6));
            if (p.distanceTo(cam) > ClientConfig.trailRange()) continue;
            int n = Math.max(1, (int) Math.ceil(from.distanceTo(p) / 1.2));
            for (int i = 0; i < n; i++) {
                Vec3 q = from.add(p.subtract(from).scale(i / (double) n)).add(back.scale(1.6));
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, q.x + r.nextGaussian() * 0.12, q.y + r.nextGaussian() * 0.12, q.z + r.nextGaussian() * 0.12,
                        0.0, 0.0, 0.0);
            }
            if (s.burning) {
                for (int i = 0; i < 3; i++)
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + r.nextGaussian() * 0.1, tail.y + r.nextGaussian() * 0.1, tail.z + r.nextGaussian() * 0.1,
                            back.x * 0.3, back.y * 0.3, back.z * 0.3);
                VeilBridge.glow(KEY ^ s.id, tail.x, tail.y, tail.z, 1.0F, 0.66F, 0.3F, 9.0F, 1.6F, now);
                final Shot ss = s;
                s.loop = LoopSound.keep(s.loop, now, WmdSounds.AAM_FLIGHT_LOOP.get(), Balance.SND_SMALL, 1.0F,
                        () -> ClientAam.position(ss, Minecraft.getInstance().level == null ? ss.seen : Minecraft.getInstance().level.getGameTime()),
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
        double t = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Shot s : SHOTS.values()) {
            Vec3 p = position(s, t);
            if (p.distanceTo(cam) > DRAW_RANGE) continue;
            String mesh = AamItem.mesh(s.kind);
            if (mesh == null) continue;
            MeshModel mm = MeshModel.named(mesh);
            if (!mm.has("part")) continue;
            Vec3 d = s.vel.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, -1) : s.vel.normalize();
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, -1.0F, (float) d.x, (float) d.y, (float) d.z));
            ps.translate(0.0F, -AamMeshes.radiusPx(mesh) / 16.0F, 0.0F);
            int light = s.burning ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
            int[] c = MissileParts.tint(AamItem.paint(s.kind));
            mm.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 255, false);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }
}
