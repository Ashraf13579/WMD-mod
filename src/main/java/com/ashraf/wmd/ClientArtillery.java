package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientArtillery {

    private ClientArtillery() {}

    private static final int WHISTLE_TICKS = 70;
    private static final double FX_RANGE = 320.0;
    private static final double FLARE_FALL = 0.06;

    private record Cloud(Vec3 at, double r, long start, int life) {}
    private record Flare(Vec3 at, long start, int life, double ground, long key) {}

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final List<Flare> FLARES = new ArrayList<>();
    private static final Set<Long> WHISTLING = new HashSet<>();

    private static final Set<Long> OWN = Statics.client(new HashSet<>());
    private static float jolt;
    private static long flareKeys = 0x4152_5400_0000_0000L;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { ArtilleryView.clear(); CLOUDS.clear(); FLARES.clear(); WHISTLING.clear(); jolt = 0; return; }
        long now = level.getGameTime();
        jolt *= 0.8F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (ArtShell s; (s = ArtilleryView.FIRED.poll()) != null; ) if (now - s.start() < 10) muzzle(level, s, cam);
        barrelSmoke(level, cam, now);
        for (ArtImpactPayload p; (p = ArtilleryView.IMPACTS.poll()) != null; ) impact(level, p, cam, now);
        for (Iterator<ArtShell> it = ArtilleryView.SHELLS.values().iterator(); it.hasNext(); ) {
            ArtShell s = it.next();
            if (now > s.endTime() + 40) { it.remove(); WHISTLING.remove(s.id()); OWN.remove(s.id()); continue; }
            if (now < s.start() || now > s.endTime()) continue;
            Vec3 p = s.position(now);
            double d = p.distanceTo(cam);
            if (d < Math.min(FX_RANGE, ClientConfig.trailRange()) && now > s.start() + 1 && !OWN.contains(s.id())) {
                Vec3 v = s.velocity(now);
                int n = d < 96 ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    double k = i / (double) n;
                    level.addAlwaysVisibleParticle(i == 0 ? WmdParticles.VAPOUR.get() : ParticleTypes.SMOKE, true, p.x - v.x * k, p.y - v.y * k, p.z - v.z * k, 0, 0.005, 0);
                }
            }
            if (s.endTime() - now <= WHISTLE_TICKS && s.type() != Artillery.ILLUM && !WHISTLING.contains(s.id())
                    && new Vec3(s.tx(), s.ty(), s.tz()).distanceTo(cam) < 200.0) {
                WHISTLING.add(s.id());
                mc.getSoundManager().play(new Whistle(s));
            }
        }
        if (WHISTLING.size() > 64) WHISTLING.retainAll(ArtilleryView.SHELLS.keySet());

        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            long age = now - c.start();
            if (age > c.life()) { it.remove(); continue; }
            if ((now & 3) != 0 || c.at().distanceTo(cam) > FX_RANGE) continue;
            double k = age < c.life() * 0.75 ? 1.0 : 1.0 - (age - c.life() * 0.75) / (c.life() * 0.25);
            int n = ClientConfig.count((float) (3.0 * k * (c.r() / 8.0)), rnd);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = c.r() * Math.sqrt(rnd.nextDouble());
                double x = c.at().x + Math.cos(a) * rr, z = c.at().z + Math.sin(a) * rr;
                double y = ClientBlastGeo.ground(level, x, z, c.at().y) + 0.3 + rnd.nextDouble() * 1.5;
                level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, x, y, z, rnd.nextGaussian() * 0.02, 0.004, rnd.nextGaussian() * 0.02);
            }
        }

        for (Iterator<Flare> it = FLARES.iterator(); it.hasNext(); ) {
            Flare f = it.next();
            long age = now - f.start();
            double y = f.at().y - age * FLARE_FALL;
            if (age > f.life() || y <= f.ground() + 1.0) { it.remove(); continue; }
            double fade = age > f.life() - 60 ? (f.life() - age) / 60.0 : 1.0;
            double sway = Math.sin(age * 0.05 + f.key()) * 1.2;
            double x = f.at().x + sway, z = f.at().z + Math.cos(age * 0.04 + f.key()) * 1.0;
            VeilBridge.glow(f.key(), x, y - 0.5, z, 1.0F, 0.86F, 0.62F, 40.0F, (float) (2.6 * fade), 0.25F, now);
            if (new Vec3(x, y, z).distanceTo(cam) > FX_RANGE * 2) continue;
            if ((now & 1) == 0) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, x, y - 0.4, z, rnd.nextGaussian() * 0.03, -0.05, rnd.nextGaussian() * 0.03);
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x, y - 0.2, z, 0, 0, 0);
            if ((now % 3) == 0) level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, x, y + 0.3, z, 0, 0.02, 0);
            if (!VeilBridge.active() && (now % 5) == 0)
                for (int i = 0; i < 3; i++) level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y - 0.3, z, rnd.nextGaussian() * 0.02, -0.02, rnd.nextGaussian() * 0.02);
        }
    }

    private static void muzzle(ClientLevel level, ArtShell s, Vec3 cam) {
        if (s.gun() == Artillery.ROCKET) { MlrsClient.launch(level, s, cam); return; }
        if (s.gun() == Artillery.SUPER_GUN) { SuperGunClient.muzzle(level, s, cam); return; }
        Vec3 m = new Vec3(s.ox(), s.oy(), s.oz());
        if (ClientGunner.ownShot(m)) { if (OWN.size() < 64) OWN.add(s.id()); return; }
        double d = m.distanceTo(cam);
        boolean how = s.gun() == Artillery.HOWITZER;
        LaunchSounds.gun(how, m.x, m.y, m.z);
        float reach = how ? 96.0F : 48.0F;
        if (d < reach) jolt = Math.max(jolt, (float) (1.0 - d / reach) * (how ? 2.2F : 1.0F));
        VeilBridge.flash(m.x, m.y, m.z, 1.0F, 0.75F, 0.4F, how ? 22.0F : 10.0F, how ? 3.5F : 2.0F, 0.5F, 5);
        if (d > FX_RANGE) return;
        RandomSource rnd = level.random;
        Vec3 dir = s.velocity(s.start()).normalize();
        for (int i = 0; i < (how ? 10 : 5); i++) {
            double k = rnd.nextDouble() * (how ? 3.0 : 1.2);
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, m.x + dir.x * k, m.y + dir.y * k, m.z + dir.z * k,
                    dir.x * 0.3 + rnd.nextGaussian() * 0.05, dir.y * 0.3 + rnd.nextGaussian() * 0.05, dir.z * 0.3 + rnd.nextGaussian() * 0.05);
        }
        for (int i = 0; i < ClientConfig.count(how ? 10 : 4, rnd); i++)
            level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, m.x + dir.x * rnd.nextDouble() * 2, m.y + dir.y * rnd.nextDouble() * 2, m.z + dir.z * rnd.nextDouble() * 2,
                    dir.x * 0.12 + rnd.nextGaussian() * 0.04, 0.02, dir.z * 0.12 + rnd.nextGaussian() * 0.04);
        double gy = ClientBlastGeo.ground(level, m.x, m.z, m.y - 1.5);
        for (int i = 0; i < ClientConfig.count(how ? 10 : 4, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, m.x, gy + 0.2, m.z, Math.cos(a) * 0.2, 0.01, Math.sin(a) * 0.2);
        }

        net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(m.x, gy - 0.5, m.z);
        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(under);
        if (!ground.isAir()) {
            var chips = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < ClientConfig.count(how ? 24 : 10, rnd); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, v = 0.15 + rnd.nextDouble() * (how ? 0.25 : 0.12);
                level.addParticle(chips, m.x + Math.cos(a) * 1.5, gy + 0.1, m.z + Math.sin(a) * 1.5, Math.cos(a) * v, 0.12 + rnd.nextDouble() * 0.15, Math.sin(a) * v);
            }
            if (how && d < SHAKE_R) {
                var dust = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST, ground);
                for (int i = 0; i < ClientConfig.count(16, rnd); i++)
                    level.addParticle(dust, m.x + (rnd.nextDouble() - 0.5) * 16, gy + 2.0 + rnd.nextDouble() * 4.0, m.z + (rnd.nextDouble() - 0.5) * 16, 0.0, 0.0, 0.0);
            }
        }
        if (SMOKING.size() < 16) SMOKING.add(new Smoke(m.add(dir.scale(how ? 2.5 : 1.0)), dir, level.getGameTime(), how));
    }

    private record Smoke(Vec3 at, Vec3 dir, long t, boolean how) {}

    private static final java.util.List<Smoke> SMOKING = Statics.client(new java.util.ArrayList<>());
    private static final int BARREL_SMOKE = 100;
    private static final double SHAKE_R = 32.0;

    private static void barrelSmoke(ClientLevel level, Vec3 cam, long now) {
        if (SMOKING.isEmpty()) return;
        RandomSource rnd = level.random;
        for (java.util.Iterator<Smoke> it = SMOKING.iterator(); it.hasNext(); ) {
            Smoke s = it.next();
            long age = now - s.t();
            if (age > BARREL_SMOKE || age < 0) { it.remove(); continue; }
            if (s.at().distanceToSqr(cam) > FX_RANGE * FX_RANGE || rnd.nextDouble() > (1.0 - age / (double) BARREL_SMOKE) * (s.how() ? 0.8 : 0.5)) continue;
            level.addAlwaysVisibleParticle(WmdParticles.VAPOUR.get(), true, s.at().x + rnd.nextGaussian() * 0.15, s.at().y + rnd.nextGaussian() * 0.15, s.at().z + rnd.nextGaussian() * 0.15,
                    s.dir().x * 0.02, 0.04 + rnd.nextDouble() * 0.03, s.dir().z * 0.02);
        }
    }

    private static void impact(ClientLevel level, ArtImpactPayload p, Vec3 cam, long now) {
        if (p.gun() == Artillery.SUPER_GUN) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        if (OWN.contains(p.id())) {
            ArtShell own = ArtilleryView.SHELLS.get(p.id());
            ClientGunner.logMiss("heavy", own != null ? new Vec3(own.ox(), own.oy(), own.oz()) : at, at, level.getGameTime());
        }
        double d = at.distanceTo(cam);
        RandomSource rnd = level.random;
        boolean how = p.gun() == Artillery.HOWITZER;
        if (p.gun() == Artillery.ROCKET) how = true;
        if (p.stage() == ArtImpactPayload.SHOT_DOWN) {
            WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), at.x, at.y, at.z, 3.0F, 1.5F);
            VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.7F, 0.3F, 8.0F, 2.0F, 4);
            if (d < FX_RANGE) for (int i = 0; i < 12; i++) level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1);
            return;
        }
        switch (p.kind()) {
            case Artillery.PIERCE, Artillery.INCENDIARY -> WmdSound.play(WmdSounds.HE_DEBRIS.get(), at.x, at.y, at.z, 2.0F, 0.9F);
            case Artillery.SENSOR, Artillery.CAMERA, Artillery.JAMMING, Artillery.LEAFLET, Artillery.FLAK -> {
                WmdSound.play(WmdSounds.DECOY_POP.get(), at.x, at.y, at.z, 5.0F, p.kind() == Artillery.FLAK ? 0.7F : 1.0F);
                VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.9F, 0.7F, 10.0F, 2.0F, 0.4F, 5);
                if (d < FX_RANGE * 2) for (int i = 0; i < 16; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.08, rnd.nextGaussian() * 0.15);
            }
            case Artillery.HE, Artillery.GUIDED, Artillery.LASER, Artillery.ROCKET_ASSIST, Artillery.GUNSHIP_105 -> {
                float size = p.kind() == Artillery.GUNSHIP_105 ? (float) Balance.GUNSHIP_HE_FX : how ? 1.5F : 1.0F;
                VeilBridge.flash(at.x, at.y + 1.5, at.z, 1.0F, 0.6F, 0.25F, 16.0F * size, 3.5F, 0.5F, 7);

                WmdSound.play(d < 200 ? WmdSounds.HE_NEAR.get() : WmdSounds.HE_FAR.get(), at.x, at.y, at.z, how ? 25.0F : 18.0F, (how ? 1.15F : 1.4F) + rnd.nextFloat() * 0.1F);
                if (d < 120) WmdSound.play(WmdSounds.HE_DEBRIS.get(), at.x, at.y, at.z, 2.0F, 1.2F);
                if (d < 220) VeilBridge.quasar("he_sparks_s", at.x, at.y + 1.0, at.z);
                float reach = how ? 110.0F : 70.0F;
                if (d < reach) jolt = Math.max(jolt, (float) (1.0 - d / reach) * 1.6F * size);
                if (d > FX_RANGE) return;
                level.addAlwaysVisibleParticle(how ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, true, at.x, at.y + 1.0, at.z, 0, 0, 0);
                for (int i = 0; i < ClientConfig.count(12 * size, rnd); i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, at.x, at.y + 0.5, at.z, rnd.nextGaussian() * 0.2, 0.1 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.2);
                BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
                if (!ground.isAir()) for (int i = 0; i < ClientConfig.count(22 * size, rnd); i++)
                    level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, at.x, at.y + 0.3, at.z,
                            rnd.nextGaussian() * 0.4, 0.5 + rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.4);
                for (int i = 0; i < ClientConfig.count(16 * size, rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, at.x, at.y + 0.2, at.z, Math.cos(a) * 0.3 * size, 0.01, Math.sin(a) * 0.3 * size);
                }
                for (int i = 0; i < ClientConfig.count(5 * size, rnd); i++)
                    level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, at.x + rnd.nextGaussian(), at.y + 1.0, at.z + rnd.nextGaussian(), rnd.nextGaussian() * 0.03, 0.06, rnd.nextGaussian() * 0.03);
            }
            case Artillery.SMOKE -> {
                WmdSound.play(WmdSounds.DECOY_POP.get(), at.x, at.y, at.z, 3.0F, 0.8F);
                WmdSound.play(WmdSounds.CHAFF_HISS.get(), at.x, at.y, at.z, 2.0F, 0.9F);
                CLOUDS.add(new Cloud(at, how ? 14.0 : 8.0, now, Balance.ART_SMOKE_SECONDS * 20));
                if (d < FX_RANGE) for (int i = 0; i < ClientConfig.count(12, rnd); i++)
                    level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, at.x, at.y + 0.5, at.z, rnd.nextGaussian() * 0.15, 0.03, rnd.nextGaussian() * 0.15);
            }
            case Artillery.ILLUM -> {
                WmdSound.play(WmdSounds.DECOY_POP.get(), at.x, at.y, at.z, 5.0F, 1.1F);
                WmdSound.play(WmdSounds.DECOY_FIZZ.get(), at.x, at.y, at.z, 4.0F, 0.8F);
                VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.95F, 0.8F, 30.0F, 3.0F, 0.4F, 10);
                double g = ClientBlastGeo.ground(level, at.x, at.z, at.y - Artillery.ILLUM_BURST);
                FLARES.add(new Flare(at, now, Balance.ART_ILLUM_SECONDS * 20, g, ++flareKeys));
                if (d < FX_RANGE * 2) for (int i = 0; i < 16; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.06, rnd.nextGaussian() * 0.12);
            }
            default -> {
                WmdSound.play(SoundEvents.FIREWORK_ROCKET_BLAST, at.x, at.y, at.z, 6.0F, 0.6F);
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), at.x, at.y, at.z, 4.0F, 1.8F);
                VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.9F, 0.7F, 14.0F, 2.5F, 0.5F, 6);
                if (d < FX_RANGE) {
                    for (int i = 0; i < 24; i++) level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.2);
                    for (int i = 0; i < 14; i++) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.25, -0.1 - rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.25);
                }
            }
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ArtilleryView.SHELLS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double near = Math.min(ClientFlights.nearLimit(), 512.0);
        boolean drew = false;
        for (ArtShell s : ArtilleryView.SHELLS.values()) {
            if (t < s.start() + 1 || t > s.endTime()) continue;
            if (s.gun() == Artillery.ROCKET) { drew |= MlrsClient.drawRocket(s, t, cam, ps, buffers, level, near); continue; }
            Vec3 p = s.position(t);
            if (p.distanceTo(cam) > near) continue;
            Vec3 v = s.velocity(t);
            if (v.lengthSqr() < 1.0E-8) continue;
            Vector3f dir = new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(noseAlong(dir));
            int light = p.y > level.getMaxBuildHeight() ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
            if (s.gun() == Artillery.SUPER_GUN) { SuperGunClient.drawShell(ps, buffers, light, s.type()); ps.popPose(); drew = true; continue; }
            String name = s.type() == Artillery.GUNSHIP_105 ? Artillery.shellId(Artillery.HOWITZER, Artillery.HE) : Artillery.shellId(s.gun(), s.type());
            MeshModel mesh = MeshModel.named(name);
            if (mesh.has("part")) {
                float k = ShellItemRenderer.TRUE_SCALE;
                ps.scale(k, k, k);
                ps.translate(0.0, -ShellItemRenderer.lengthPx(s.gun()) / 16.0 * 0.5, 0.0);
                mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            } else {
                float k = s.gun() == Artillery.HOWITZER ? 0.7F : 0.5F;
                ps.scale(k, k, k);
                mc.getItemRenderer().renderStatic(new ItemStack(Artillery.shellItem(s.gun(), s.type())), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, level, 0);
            }
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    private static Quaternionf noseAlong(Vector3f dir) {
        Vector3f up = new Vector3f(0, 1, 0);
        float dot = Mth.clamp(up.dot(dir), -1.0F, 1.0F);
        if (dot > 0.9999F) return new Quaternionf();
        if (dot < -0.9999F) return new Quaternionf().rotateX((float) Math.PI);
        Vector3f axis = new Vector3f(up).cross(dir).normalize();
        return new Quaternionf().rotateAxis((float) Math.acos(dot), axis);
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
    }

    private static final class Whistle extends AbstractTickableSoundInstance {
        private final ArtShell s;
        private final boolean recorded;

        Whistle(ArtShell s) {
            super(LaunchSounds.on() ? WmdSounds.SHELL_WHISTLE.get() : SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.s = s;
            this.recorded = LaunchSounds.on();
            this.looping = !recorded;
            this.delay = 0;
            this.volume = recorded ? (s.gun() == Artillery.HOWITZER ? 10.0F : 7.0F) : 0.05F;
            this.pitch = recorded ? (s.gun() == Artillery.HOWITZER ? 0.92F : s.gun() == Artillery.ROCKET ? 1.0F : 1.08F) : 1.8F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            Vec3 p = s.position(s.endTime() - WHISTLE_TICKS);
            this.x = p.x; this.y = p.y; this.z = p.z;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !ArtilleryView.SHELLS.containsKey(s.id())) { stop(); return; }
            double t = mc.level.getGameTime();
            if (t >= s.endTime()) { stop(); return; }
            Vec3 p = s.position(t);
            x = p.x; y = p.y; z = p.z;
            if (recorded) return;
            float k = (float) Mth.clamp(1.0 - (s.endTime() - t) / WHISTLE_TICKS, 0.0, 1.0);
            pitch = 1.8F - 0.9F * k;
            volume = (s.gun() == Artillery.HOWITZER ? 0.9F : 0.6F) * (0.15F + 0.85F * k);
        }
    }

    static List<ArtShell> visible(double rx, double rz, double range, boolean seeking, java.util.UUID me, long now) {
        List<ArtShell> out = new ArrayList<>();
        for (ArtShell s : ArtilleryView.SHELLS.values()) {
            if (now < s.start() || now > s.endTime()) continue;
            boolean own = me != null && me.equals(s.owner());
            Vec3 p = s.position(now);
            if (own || (seeking && Math.hypot(p.x - rx, p.z - rz) <= range)) out.add(s);
        }
        return out;
    }
}
