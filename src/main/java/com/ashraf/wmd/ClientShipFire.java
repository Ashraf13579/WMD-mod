package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientShipFire {

    private ClientShipFire() {}

    private static final int STALE = 20, PUFFS = 30;
    private static final double VIEW = 512.0, CRACKLE_R = 64.0;

    private record Fire(ShipFirePayload p, long got) {}

    private static final Map<UUID, Fire> FIRES = Statics.client(new HashMap<>());

    static void add(ShipFirePayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) FIRES.put(p.id(), new Fire(p, level.getGameTime()));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (FIRES.isEmpty() || level == null || mc.isPaused() || mc.gameRenderer == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        RandomSource r = level.random;
        int budget = PUFFS;
        for (Iterator<Fire> it = FIRES.values().iterator(); it.hasNext(); ) {
            Fire f = it.next();
            long age = now - f.got;
            if (age > STALE) { it.remove(); continue; }
            ShipFirePayload p = f.p;
            double x = p.x() + p.vx() * age, y = p.y() + p.vy() * age, z = p.z() + p.vz() * age;
            if (cam.distanceToSqr(x, y, z) > VIEW * VIEW) continue;
            int n = 1 + Math.round(3 * p.heat());
            for (int i = 0; i < n && budget > 0; i++, budget--) {
                double px = x + (r.nextDouble() - 0.5) * 2.0 * p.hx(), py = y + r.nextDouble() * p.hy(), pz = z + (r.nextDouble() - 0.5) * 2.0 * p.hz();
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, px, py, pz, (r.nextDouble() - 0.5) * 0.02, 0.03 + 0.03 * p.heat(), (r.nextDouble() - 0.5) * 0.02);
                if (r.nextFloat() < p.heat()) level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, px, py, pz, p.vx() * 0.5, 0.02, p.vz() * 0.5);
            }
            if (now % 30 == 0 && cam.distanceToSqr(x, y, z) < CRACKLE_R * CRACKLE_R)
                level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 2.0F, 0.6F, false);
        }
    }
}
