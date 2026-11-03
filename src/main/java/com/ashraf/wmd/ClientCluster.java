package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCluster {

    private ClientCluster() {}

    private static final int CARPET_TICKS = 400;

    private record Faller(Vec3 from, Vec3 to, long start, int dur, boolean casing, double sway) {}
    private record Footprint(Vec3 at, double dx, double dz, double len, double wid, long start) {}

    private static final List<Faller> FALLERS = new ArrayList<>();
    private static final List<Footprint> CARPETS = new ArrayList<>();
    private static final List<Object[]> LATER = new ArrayList<>();
    private static float jolt;

    public static void handle(double x, double y, double z, int tier, int stage) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (stage >= WarheadFx.ST_CLUSTER_OPEN) open(level, x, y, z, tier, Math.toRadians(stage - WarheadFx.ST_CLUSTER_OPEN));
        else if (stage == WarheadFx.ST_BOMBLET) bomblet(level, x, y, z);
    }

    private static void later(long tick, Runnable r) { LATER.add(new Object[] { tick, r }); }

    private static void play(ClientLevel level, SoundEvent ev, double x, double y, double z, float vol, float pitch) {
        WmdSound.play(ev, x, y, z, vol, pitch);
    }

    private static void open(ClientLevel level, double x, double y, double z, int tier, double heading) {
        RandomSource rnd = level.random;
        long now = level.getGameTime();
        Vec3 o = new Vec3(x, y + WarheadFx.CLUSTER_OPEN_UP, z);
        double dx = Math.cos(heading), dz = Math.sin(heading), base = Missiles.craterRadius(tier), len = base * 3.0, wid = base * 0.9;

        VeilBridge.flash(o.x, o.y, o.z, 1.0F, 0.9F, 0.7F, 30.0F, 4.0F, 0.6F, 8);

        play(level, SoundEvents.FIREWORK_ROCKET_BLAST, o.x, o.y, o.z, 25.0F, 0.55F);
        play(level, SoundEvents.GENERIC_EXPLODE.value(), o.x, o.y, o.z, 20.0F, 1.7F);
        for (int i = 0; i < 40; i++) level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, o.x, o.y, o.z, rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.25);
        for (int i = 0; i < 20; i++) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, o.x, o.y, o.z, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3);

        for (int s = -1; s <= 1; s += 2) {
            Vec3 to = new Vec3(x - dz * s * (wid * 0.9 + 6) + dx * len * 0.2, y, z + dx * s * (wid * 0.9 + 6) + dz * len * 0.2);
            FALLERS.add(new Faller(o, to, now, WarheadFx.CLUSTER_RAIN + 20, true, 0.0));
        }

        int n = 60;
        for (int i = 0; i < n; i++) {
            double u = (rnd.nextDouble() - 0.5) * len, v = (rnd.nextDouble() - 0.5) * wid;
            Vec3 to = new Vec3(x + dx * u - dz * v, y, z + dz * u + dx * v);
            FALLERS.add(new Faller(o.add(rnd.nextGaussian(), rnd.nextGaussian() * 0.5, rnd.nextGaussian()), to, now, WarheadFx.CLUSTER_RAIN - 2 + rnd.nextInt(5), false, rnd.nextDouble() * 6.28));
        }
        CARPETS.add(new Footprint(new Vec3(x, y, z), dx, dz, len, wid, now + WarheadFx.CLUSTER_RAIN));
    }

    private static void bomblet(ClientLevel level, double x, double y, double z) {
        RandomSource rnd = level.random;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(new Vec3(x, y, z));
        VeilBridge.flash(x, y + 1.5, z, 1.0F, 0.6F, 0.25F, 14.0F, 3.0F, 0.4F, 6);

        play(level, WmdSounds.BOMBLET_BANG.get(), x, y, z, 25.0F, 0.9F + rnd.nextFloat() * 0.3F);
        if (d < 200) VeilBridge.quasar("he_sparks_s", x, y + 1.0, z);
        if (d < 80) jolt = Math.max(jolt, (float) (1.0 - d / 80.0) * 1.2F);
        if (d > 400) return;
        level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, x, y + 1.0, z, 0, 0, 0);
        for (int i = 0; i < ClientConfig.count(10, rnd); i++)
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y + 0.5, z, rnd.nextGaussian() * 0.18, 0.1 + rnd.nextDouble() * 0.25, rnd.nextGaussian() * 0.18);
        for (int i = 0; i < 3; i++) level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x, y + 0.5, z, 0, 0, 0);
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
        if (!ground.isAir()) for (int i = 0; i < ClientConfig.count(14, rnd); i++)
            level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, x, y + 0.3, z, rnd.nextGaussian() * 0.35, 0.4 + rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.35);
        for (int i = 0; i < ClientConfig.count(12, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, y + 0.2, z, Math.cos(a) * 0.25, 0.01, Math.sin(a) * 0.25);
        }
        for (int i = 0; i < ClientConfig.count(4, rnd); i++)
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, x + rnd.nextGaussian() * 0.6, y + 1.0, z + rnd.nextGaussian() * 0.6, 0, 0.08, 0);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { FALLERS.clear(); CARPETS.clear(); LATER.clear(); jolt = 0; return; }
        long now = level.getGameTime();
        jolt *= 0.8F;
        for (int i = LATER.size() - 1; i >= 0; i--) if ((long) LATER.get(i)[0] <= now) ((Runnable) LATER.remove(i)[1]).run();
        RandomSource rnd = level.random;
        for (int i = FALLERS.size() - 1; i >= 0; i--) {
            Faller f = FALLERS.get(i);
            double t = (now - f.start) / (double) f.dur;
            if (t >= 1.0) { FALLERS.remove(i); continue; }

            double h = 1.0 - Math.pow(1.0 - t, 2.0), vt = Math.pow(t, 1.7);
            double sway = f.casing ? 0.0 : Math.sin(now * 0.5 + f.sway) * 0.8 * (1.0 - t);
            double x = f.from.x + (f.to.x - f.from.x) * h + sway, y = f.from.y + (f.to.y - f.from.y) * vt, z = f.from.z + (f.to.z - f.from.z) * h + sway;
            if (f.casing) {
                level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, x, y, z, 0, 0.02, 0);
                if ((now & 1) == 0) level.addAlwaysVisibleParticle(ParticleTypes.CRIT, true, x, y, z, rnd.nextGaussian() * 0.05, 0, rnd.nextGaussian() * 0.05);
            } else {
                level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, x, y, z, 0, 0.01, 0);
                if (((now + i) & 3) == 0) level.addAlwaysVisibleParticle(ParticleTypes.CRIT, true, x, y, z, 0, -0.05, 0);
            }
        }
        for (int i = CARPETS.size() - 1; i >= 0; i--) {
            Footprint c = CARPETS.get(i);
            long age = now - c.start;
            if (age > CARPET_TICKS) { CARPETS.remove(i); continue; }
            if (age < 0 || (now & 1) != 0) continue;
            double k = 1.0 - age / (double) CARPET_TICKS;
            for (int j = 0; j < ClientConfig.count((float) (6 * k), rnd); j++) {
                double u = (rnd.nextDouble() - 0.5) * c.len, v = (rnd.nextDouble() - 0.5) * c.wid;
                double x = c.at.x + c.dx * u - c.dz * v, z = c.at.z + c.dz * u + c.dx * v;
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, c.at.y + 0.3 + rnd.nextDouble() * 1.5, z, rnd.nextGaussian() * 0.01, 0.005, rnd.nextGaussian() * 0.01);
            }
            if (rnd.nextFloat() < 0.5 * k) {
                double u = (rnd.nextDouble() - 0.5) * c.len, v = (rnd.nextDouble() - 0.5) * c.wid;
                level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, c.at.x + c.dx * u - c.dz * v, c.at.y + 0.3, c.at.z + c.dz * u + c.dx * v, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
    }
}
