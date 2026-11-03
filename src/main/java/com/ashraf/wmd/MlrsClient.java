package com.ashraf.wmd;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class MlrsClient {

    private MlrsClient() {}

    static final int BURN_TICKS = 50;
    private static final double FX_RANGE = 320.0;
    private static float jolt;

    static void launch(ClientLevel level, ArtShell s, Vec3 cam) {
        Vec3 m = new Vec3(s.ox(), s.oy(), s.oz());
        double d = m.distanceTo(cam);
        RandomSource rnd = level.random;
        LaunchSounds.m270(m.x, m.y, m.z, s.seq() <= 1);
        if (d < 64.0) jolt = Math.max(jolt, (float) (1.0 - d / 64.0) * 1.4F);
        VeilBridge.flash(m.x, m.y, m.z, 1.0F, 0.7F, 0.35F, 16.0F, 3.0F, 0.5F, 6);
        if (d > FX_RANGE) return;
        Vec3 dir = s.velocity(s.start()).normalize();
        for (int i = 0; i < 14; i++) {
            double k = rnd.nextDouble() * 2.0;
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, m.x + dir.x * k, m.y + dir.y * k, m.z + dir.z * k,
                    dir.x * 0.4 + rnd.nextGaussian() * 0.05, dir.y * 0.4 + rnd.nextGaussian() * 0.05, dir.z * 0.4 + rnd.nextGaussian() * 0.05);
        }
        Vec3 back = m.subtract(dir.scale(7.0));
        for (int i = 0; i < 10; i++)
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, back.x + rnd.nextGaussian() * 0.6, back.y + rnd.nextGaussian() * 0.4, back.z + rnd.nextGaussian() * 0.6,
                    -dir.x * 0.25 + rnd.nextGaussian() * 0.05, -dir.y * 0.1, -dir.z * 0.25 + rnd.nextGaussian() * 0.05);
        double gy = ClientBlastGeo.ground(level, back.x, back.z, m.y - 3.0);
        for (int i = 0; i < ClientConfig.count(16, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, back.x + rnd.nextGaussian() * 1.5, gy + 0.5 + rnd.nextDouble() * 2.0, back.z + rnd.nextGaussian() * 1.5,
                    Math.cos(a) * 0.12 - dir.x * 0.15, 0.02, Math.sin(a) * 0.12 - dir.z * 0.15);
        }
        for (int i = 0; i < ClientConfig.count(8, rnd); i++)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, back.x, gy + 0.2, back.z, rnd.nextGaussian() * 0.15, 0.01, rnd.nextGaussian() * 0.15);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        jolt *= 0.8F;
        if (level == null || ArtilleryView.SHELLS.isEmpty()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (ArtShell s : ArtilleryView.SHELLS.values()) {
            if (s.gun() != Artillery.ROCKET) continue;
            long age = now - s.start();
            if (age < 1 || age > BURN_TICKS || now > s.endTime()) continue;
            Vec3 p = s.position(now), v = s.velocity(now);
            Vec3 dir = v.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : v.normalize();
            Vec3 tail = p.subtract(dir.scale(Mlrs.ROCKET_LEN_PX / 32.0));
            VeilBridge.glow(0x524B_0000_0000L ^ s.id(), tail.x, tail.y, tail.z, 1.0F, 0.6F, 0.25F, 10.0F, 2.0F * (1.0F - age / (float) BURN_TICKS) + 0.3F, now);
            if (p.distanceTo(cam) > Math.min(FX_RANGE * 2, ClientConfig.trailRange())) continue;
            int n = p.distanceTo(cam) < 128 ? 4 : 2;
            for (int i = 0; i < n; i++) {
                double k = i / (double) n;
                double x = tail.x - v.x * k, y = tail.y - v.y * k, z = tail.z - v.z * k;
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, x, y, z, rnd.nextGaussian() * 0.01, 0.005, rnd.nextGaussian() * 0.01);
                if (i == 0) level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, x, y, z, -dir.x * 0.2, -dir.y * 0.2, -dir.z * 0.2);
            }
        }
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
    }

    static boolean drawRocket(ArtShell s, double t, Vec3 cam, PoseStack ps, MultiBufferSource buffers, ClientLevel level, double near) {
        Vec3 p = s.position(t);
        if (p.distanceTo(cam) > near) return false;
        Vec3 v = s.velocity(t);
        if (v.lengthSqr() < 1.0E-8) return false;
        MeshModel mesh = MeshModel.named(Mlrs.rocketId(s.type()));
        if (!mesh.has("part")) return false;
        Vector3f dir = new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
        int light = p.y > level.getMaxBuildHeight() || t - s.start() < BURN_TICKS ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
        ps.pushPose();
        ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        ps.mulPose(noseAlong(dir));
        ps.translate(0.0, -Mlrs.ROCKET_LEN_PX / 16.0 * 0.5, 0.0);
        mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        return true;
    }

    private static Quaternionf noseAlong(Vector3f dir) {
        Vector3f up = new Vector3f(0, 1, 0);
        float dot = Mth.clamp(up.dot(dir), -1.0F, 1.0F);
        if (dot > 0.9999F) return new Quaternionf();
        if (dot < -0.9999F) return new Quaternionf().rotateX((float) Math.PI);
        Vector3f axis = new Vector3f(up).cross(dir).normalize();
        return new Quaternionf().rotateAxis((float) Math.acos(dot), axis);
    }
}
