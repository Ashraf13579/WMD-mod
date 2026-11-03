package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class SuperGunClient {

    private SuperGunClient() {}

    private static final double FX_RANGE = 384.0;
    private static float jolt;

    static void muzzle(ClientLevel level, ArtShell s, Vec3 cam) {
        Vec3 m = new Vec3(s.ox(), s.oy(), s.oz());
        double d = m.distanceTo(cam);
        RandomSource rnd = level.random;

        LaunchSounds.superGun(m.x, m.y, m.z, d, Balance.SUPER_GUN_HEARD / 16.0F);
        if (d > 600.0) WmdSound.play(WmdSounds.NUKE_ROLL.get(), m.x, m.y, m.z, Balance.SUPER_GUN_HEARD / 16.0F, 1.3F);
        if (d < Balance.SUPER_GUN_SHAKE) jolt = Math.max(jolt, (float) (1.0 - d / Balance.SUPER_GUN_SHAKE) * 5.0F);
        VeilBridge.flash(m.x, m.y, m.z, 1.0F, 0.72F, 0.38F, 64.0F, 6.0F, 0.6F, 8);
        if (d > FX_RANGE) return;
        Vec3 dir = s.velocity(s.start()).normalize();
        for (int i = 0; i < 40; i++) {
            double k = rnd.nextDouble() * 12.0;
            double sp = 0.6 + rnd.nextDouble() * 0.8;
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, m.x + dir.x * k, m.y + dir.y * k, m.z + dir.z * k,
                    dir.x * sp + rnd.nextGaussian() * 0.08, dir.y * sp + rnd.nextGaussian() * 0.08, dir.z * sp + rnd.nextGaussian() * 0.08);
        }
        for (int i = 0; i < ClientConfig.count(36, rnd); i++) {
            double k = rnd.nextDouble() * 18.0;
            level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, m.x + dir.x * k, m.y + dir.y * k, m.z + dir.z * k,
                    dir.x * 0.25 + rnd.nextGaussian() * 0.06, 0.02 + rnd.nextDouble() * 0.03, dir.z * 0.25 + rnd.nextGaussian() * 0.06);
        }
        double gy = ClientBlastGeo.ground(level, m.x, m.z, m.y - 14.0);
        for (int i = 0; i < ClientConfig.count(48, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2.0 + rnd.nextDouble() * 6.0;
            level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, m.x + Math.cos(a) * r, gy + 0.4, m.z + Math.sin(a) * r,
                    Math.cos(a) * 0.45, 0.01, Math.sin(a) * 0.45);
        }
        for (int i = 0; i < ClientConfig.count(24, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, m.x, gy + 0.3, m.z, Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35);
        }
    }

    static void drawShell(PoseStack ps, MultiBufferSource buffers, int light) { drawShell(ps, buffers, light, Artillery.HE); }

    static void drawShell(PoseStack ps, MultiBufferSource buffers, int light, int type) {
        MeshModel mesh = MeshModel.named(type == Artillery.SUPER_NUKE ? "shell_super_nuke" : SuperGun.SHELL);
        if (!mesh.has("part")) return;
        ps.translate(0.0, -SuperGun.SHELL_PX / 16.0 * 0.5, 0.0);
        mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        jolt *= 0.86F;
        if (level == null) { jolt = 0.0F; return; }
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (ArtShell s : ArtilleryView.shells()) {
            if (s.gun() != Artillery.SUPER_GUN || now < s.start() + 2 || now > s.endTime()) continue;
            Vec3 p = s.position(now);
            if (p.distanceTo(cam) > FX_RANGE) continue;
            Vec3 back = s.velocity(now).normalize().scale(-SuperGun.SHELL_PX / 16.0 * 0.5);
            if (now - s.start() < 120)
                level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, p.x + back.x, p.y + back.y, p.z + back.z, 0, 0, 0);
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, p.x + back.x, p.y + back.y, p.z + back.z, 0, 0.01, 0);
        }
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * jolt);
    }
}
