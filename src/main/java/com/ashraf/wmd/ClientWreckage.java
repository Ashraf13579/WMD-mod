package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWreckage {

    private ClientWreckage() {}

    private static final class Piece { double x, y, z, vx, vy, vz; int age; }

    private static final List<Piece> PIECES = Statics.client(new ArrayList<>());

    static void add(WreckPayload p) {
        float[] v = p.v();
        for (int i = 0; i + 2 < v.length && PIECES.size() < Balance.WRECK_MAX; i += 3) {
            Piece q = new Piece();
            q.x = p.x(); q.y = p.y(); q.z = p.z(); q.vx = v[i]; q.vy = v[i + 1]; q.vz = v[i + 2];
            PIECES.add(q);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (PIECES.isEmpty() || level == null || mc.isPaused()) return;
        RandomSource r = level.random;
        for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
            Piece p = it.next();
            p.vy -= Balance.WRECK_GRAVITY;
            p.vx *= Balance.WRECK_DRAG; p.vy *= Balance.WRECK_DRAG; p.vz *= Balance.WRECK_DRAG;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            if (++p.age > Balance.WRECK_LIFE || p.y < level.getMinBuildHeight() || p.y <= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z))) {
                it.remove();
                continue;
            }
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
            if ((p.age & 1) == 0) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.x, p.y, p.z, 0.0, 0.01, 0.0);
        }
    }
}
