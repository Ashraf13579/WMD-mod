package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class PeriscopeAir {

    private PeriscopeAir() {}

    static final int EVERY = 10;

    private static final Map<UUID, Object[]> VIEWERS = Statics.server(new HashMap<>());

    static void watch(ServerPlayer sp, BlockPos controller) {
        if (!AirGate.on()) return;
        VIEWERS.put(sp.getUUID(), new Object[] { controller.immutable(), sp.serverLevel().dimension() });
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (VIEWERS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (!AirGate.on()) { VIEWERS.clear(); return; }
        if (server.getTickCount() % EVERY != 3) return;
        for (Iterator<Map.Entry<UUID, Object[]>> it = VIEWERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Object[]> e = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            BlockPos c = (BlockPos) e.getValue()[0];
            if (sp == null || sp.serverLevel().dimension() != e.getValue()[1]) { it.remove(); continue; }
            ServerLevel level = sp.serverLevel();
            if (!(level.getBlockEntity(c) instanceof PeriscopeBlockEntity be) || !be.canLook()
                    || SableBridge.worldPos(level, c).distanceTo(sp.position()) > Balance.PERI_REACH + 2.0) { it.remove(); continue; }
            Vec3 head = be.head();
            float[] m = new float[PeriscopeAirPayload.MAX * 4];
            int n = 0;
            UUID own = SableBridge.shipIdAt(level, c);
            for (AirPicture.Aircraft a : AirPicture.all(level)) {
                if (n >= PeriscopeAirPayload.MAX || a.id().equals(own) || a.pos().distanceTo(head) > Balance.PERI_AIR_RANGE) continue;
                m[n * 4] = (float) a.pos().x; m[n * 4 + 1] = (float) a.pos().y; m[n * 4 + 2] = (float) a.pos().z;
                m[n * 4 + 3] = ShipFaction.isHostileTo(sp.getUUID(), level, a.id()) ? 1.0F : 0.0F;
                n++;
            }
            PacketDistributor.sendToPlayer(sp, new PeriscopeAirPayload(java.util.Arrays.copyOf(m, n * 4)));
        }
    }
}
