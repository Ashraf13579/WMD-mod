package com.ashraf.wmd;

import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientGas {

    private ClientGas() {}

    static final float PUFFS = 14.0F;
    static final double VIEW = 320.0, HEIGHT = 8.0;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { GasCloudPayload.SEEN.clear(); }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || GasCloudPayload.SEEN.isEmpty()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        var gas = WmdParticles.GAS_CLOUD.get();
        for (Iterator<Map.Entry<Integer, Object[]>> it = GasCloudPayload.SEEN.entrySet().iterator(); it.hasNext(); ) {
            Object[] e = it.next().getValue();
            GasCloudPayload c = (GasCloudPayload) e[0];
            if ((long) e[1] < 0) e[1] = now;
            if (now - (long) e[1] > 40) { it.remove(); continue; }
            double cx = c.x(), cy = c.y(), cz = c.z(), r = c.r();
            if (Math.hypot(cam.x - cx, cam.z - cz) > VIEW + r) continue;
            float area = (float) Math.min(1.6, (r * r) / (Balance.SMOKE_RADIUS * (double) Balance.SMOKE_RADIUS) + 0.3);
            int n = ClientConfig.count(PUFFS * area * Math.max(0.15F, c.fade()), rnd);
            for (int k = 0; k < n; k++) {
                double pa = rnd.nextDouble() * Math.PI * 2.0, pr, vx, vz;
                int kind = rnd.nextInt(10);
                if (kind < 4) {
                    pr = r * (0.8 + 0.2 * rnd.nextDouble());
                    double sp = 0.08 + rnd.nextDouble() * 0.16;
                    vx = Math.cos(pa) * sp; vz = Math.sin(pa) * sp;
                } else {
                    pr = r * Math.sqrt(rnd.nextDouble());
                    vx = rnd.nextGaussian() * 0.05; vz = rnd.nextGaussian() * 0.05;
                }
                double h = rnd.nextDouble();
                double x = cx + Math.cos(pa) * pr, z = cz + Math.sin(pa) * pr, y = cy - 0.5 + h * h * h * HEIGHT;
                level.addAlwaysVisibleParticle(gas, true, x, y, z, vx + c.wx() * 0.03, 0.005 + rnd.nextDouble() * 0.015, vz + c.wz() * 0.03);
            }
        }
    }
}
