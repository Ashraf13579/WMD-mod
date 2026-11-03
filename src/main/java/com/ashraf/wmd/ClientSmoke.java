package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSmoke {

    private ClientSmoke() {}

    static final int TUBES = 8, FLIGHT = 24, STOP_EARLY = 300;
    static final float PUFFS = 14.0F;
    static final double VIEW = 320.0, PEAK = 9.0, TURRET_Y = 2.2;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { SmokePayload.clear(); }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || SmokePayload.view().isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        RandomSource rnd = level.random;
        for (SmokePayload.Entry e : SmokePayload.view()) {
            double cx = e.x(), cy = e.y(), cz = e.z();
            if (Math.hypot(cam.x - cx, cam.z - cz) > VIEW + Balance.SMOKE_RADIUS) continue;
            long age = now - e.start();
            long life = Balance.SMOKE_SECONDS * 20L;
            if (age < 0 || age >= life) continue;
            double full = Balance.SMOKE_RADIUS, r = full * Math.min(1.0, age / (Balance.SMOKE_GROW_SECONDS * 20.0));

            for (int i = 0; i < TUBES; i++) {
                double a = Math.PI * 2.0 * i / TUBES + e.id() * 0.7, land = full * (0.3 + 0.45 * ((i * 5 + e.id()) % TUBES) / (TUBES - 1.0));
                long fired = i * 2L;
                if (age < fired) continue;
                double t = Math.min(1.0, (age - fired) / (double) FLIGHT);
                double x = cx + Math.cos(a) * land * t, z = cz + Math.sin(a) * land * t, y = cy + TURRET_Y * (1.0 - t) + PEAK * 4.0 * t * (1.0 - t);
                if (t < 1.0) {
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, x, y, z, 0.0, 0.02, 0.0);
                    level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, x, y, z, 0.0, 0.0, 0.0);
                    if (age == fired) level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, cx, cy + TURRET_Y, cz, Math.cos(a) * 0.2, 0.15, Math.sin(a) * 0.2);
                } else if (age < life - STOP_EARLY && rnd.nextInt(2) == 0)
                    level.addAlwaysVisibleParticle(WmdParticles.LAUNCH_SMOKE.get(), true, x + rnd.nextGaussian() * 0.5, y + 0.3, z + rnd.nextGaussian() * 0.5,
                            rnd.nextGaussian() * 0.12, 0.04, rnd.nextGaussian() * 0.12);
            }
            if (r < 1.0 || age > life - STOP_EARLY) continue;

            var bank = WmdParticles.LAUNCH_SMOKE.get();
            int n = ClientConfig.count(PUFFS * (0.3F + 0.7F * (float) (r / full)), rnd);
            for (int k = 0; k < n; k++) {
                double pr, pa = rnd.nextDouble() * Math.PI * 2.0, vx, vz;
                int kind = rnd.nextInt(10);
                if (kind < 4) {
                    int i = rnd.nextInt(TUBES);
                    double a = Math.PI * 2.0 * i / TUBES + e.id() * 0.7, land = full * (0.3 + 0.45 * ((i * 5 + e.id()) % TUBES) / (TUBES - 1.0));
                    if (land > r + 4.0) { land = r * rnd.nextDouble(); }
                    pr = land;
                    pa = a;
                    double out = rnd.nextDouble() * Math.PI * 2.0, sp = 0.15 + rnd.nextDouble() * 0.3;
                    vx = Math.cos(out) * sp;
                    vz = Math.sin(out) * sp;
                } else if (kind < 7) {
                    pr = r * (0.85 + 0.15 * rnd.nextDouble());
                    double sp = 0.1 + rnd.nextDouble() * 0.2;
                    vx = Math.cos(pa) * sp;
                    vz = Math.sin(pa) * sp;
                } else {
                    pr = r * Math.sqrt(rnd.nextDouble());
                    vx = rnd.nextGaussian() * 0.05;
                    vz = rnd.nextGaussian() * 0.05;
                }
                double h = rnd.nextDouble();
                double x = cx + Math.cos(pa) * pr, z = cz + Math.sin(pa) * pr, y = cy + 0.3 + h * h * h * Balance.SMOKE_HEIGHT;
                level.addAlwaysVisibleParticle(bank, true, x, y, z, vx, 0.01 + rnd.nextDouble() * 0.03, vz);
            }
        }
    }
}
