package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WardenCine {

    private WardenCine() {}

    public static final int INTRO = 0, OVERDRIVE = 1, MELTDOWN = 2, DEFEAT = 3;

    private record Hold(long until, Vec3 at) {}

    private static final Map<UUID, Hold> FROZEN = Statics.server(new HashMap<>());

    static void play(WardenEntity w, ServerLevel sl, WardenState d, int kind, int ticks) {
        long now = sl.getGameTime(), end = now + ticks;
        w.cineKind = kind;
        w.cineStart = now;
        w.cineUntil = end;
        WardenCinePayload p = new WardenCinePayload(kind, w.getId(), w.getX(), w.getY(), w.getZ(), w.getYRot(), d.ox + 0.5, d.oz + 0.5,
                d.oy + WardenBunker.F + 1, now, end);
        double r2 = Balance.WARDEN_CINE_R * Balance.WARDEN_CINE_R;
        for (ServerPlayer pl : sl.players()) {
            if (pl.isSpectator() || !pl.isAlive() || pl.distanceToSqr(w) > r2) continue;
            FROZEN.put(pl.getUUID(), new Hold(end, pl.position()));
            pl.setDeltaMovement(Vec3.ZERO);
            PacketDistributor.sendToPlayer(pl, p);
        }
    }

    static boolean frozen(ServerPlayer p) {
        Hold h = FROZEN.get(p.getUUID());
        return h != null && p.serverLevel().getGameTime() < h.until();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Pre event) {
        if (FROZEN.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Hold>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hold> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || p.serverLevel().getGameTime() >= e.getValue().until()) { it.remove(); continue; }
            Vec3 at = e.getValue().at();
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0F;
            if (p.position().distanceToSqr(at) > 0.0025) p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        }
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { FROZEN.remove(event.getEntity().getUUID()); }
}
