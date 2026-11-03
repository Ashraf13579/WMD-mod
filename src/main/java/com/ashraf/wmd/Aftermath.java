package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Aftermath {

    private Aftermath() {}

    static final int DUST = 0, FIRE = 1, BIG = 2, COLLAPSE = 3, GLOW = 4, FROST = 5, NUCLEAR = 6;

    private record Zone(ServerLevel level, AftermathPayload p, long end, Set<UUID> told) {}

    private static final List<Zone> ZONES = Statics.server(new ArrayList<>());

    static int[] profile(WarheadType t) {
        return switch (t) {
            case HE, PENETRATOR, BREACHER, DAM_BREAKER, UPHEAVAL -> new int[] { FIRE, 60 };
            case GRAVITY -> new int[] { DUST, 60 };
            case CRYO -> new int[] { FROST, 60 };
            case HEAVY_BLAST, BUNKER -> new int[] { FIRE, 90 };
            case INCENDIARY, THERMOBARIC -> new int[] { FIRE, 120 };
            case NUKE, THERMO, TSAR -> new int[] { NUCLEAR, 240 };
            case PLASMA, ANTIMATTER -> new int[] { GLOW, 240 };
            case RAILGUN, ORBITAL_ROD, ASTEROID -> new int[] { BIG, 300 };
            case ROD -> new int[] { FIRE, 180 };
            case CLUSTER, CARPET -> new int[] { FIRE, 30 };
            case CONCUSSION, DAISY_CUTTER -> new int[] { DUST, 30 };
            default -> null;
        };
    }

    static void start(ServerLevel level, Crater crater, WarheadType t) {
        if (crater.radius < 1.5) return;
        int[] pr = profile(t);
        if (pr != null) add(level, crater.cx + 0.5, crater.cy, crater.cz + 0.5, (float) crater.radius, pr[0], pr[1]);
    }

    static void frost(ServerLevel level, double x, double y, double z, float radius) {
        add(level, x, y, z, radius, FROST, profile(WarheadType.CRYO)[1]);
    }

    static void dust(ServerLevel level, double x, double y, double z) {
        add(level, x, y, z, 2.0F, DUST, Balance.C4_DUST_SECONDS);
    }

    static void collapseDust(ServerLevel level, double x, double y, double z, float radius) {
        long now = level.getGameTime();
        for (Zone zn : ZONES) {
            if (zn.level != level || zn.p.kind() != COLLAPSE || now - zn.p.start() > Balance.COLLAPSE_DUST_MERGE_TICKS) continue;
            double dx = zn.p.x() - x, dz = zn.p.z() - z;
            if (dx * dx + dz * dz < Balance.COLLAPSE_DUST_MERGE * Balance.COLLAPSE_DUST_MERGE) return;
        }
        add(level, x, y, z, Math.min(radius, (float) Balance.COLLAPSE_DUST_MAX_R), COLLAPSE, Balance.COLLAPSE_DUST_SECONDS);
    }

    private static void add(ServerLevel level, double x, double y, double z, float radius, int kind, int seconds) {
        if (!Config.Server.aftermath()) return;
        int max = Math.max(4, LoadGuard.scale(Balance.AFTERMATH_MAX, 0.25));
        while (ZONES.size() >= max) {
            int old = 0;
            for (int i = 0; i < ZONES.size(); i++) if (ZONES.get(i).p.kind() == kind) { old = i; break; }
            ZONES.remove(old);
        }
        long now = level.getGameTime(), dur = seconds * 20L;
        AftermathPayload p = new AftermathPayload(x, y, z, radius, (byte) kind, now, (int) dur);
        Zone zone = new Zone(level, p, now + dur, new HashSet<>());
        ZONES.add(zone);
        tell(zone);
    }

    private static void tell(Zone z) {
        double r2 = Balance.AFTERMATH_TELL_R * Balance.AFTERMATH_TELL_R;
        for (ServerPlayer pl : z.level.players()) {
            if (z.told.contains(pl.getUUID())) continue;
            double dx = pl.getX() - z.p.x(), dz = pl.getZ() - z.p.z();
            if (dx * dx + dz * dz > r2) continue;
            z.told.add(pl.getUUID());
            PacketDistributor.sendToPlayer(pl, z.p);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (ZONES.isEmpty() || !(event.getLevel() instanceof ServerLevel sl) || sl.getGameTime() % 20 != 0) return;
        long now = sl.getGameTime();
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            if (z.level != sl) continue;
            if (now >= z.end) { it.remove(); continue; }
            tell(z);
        }
    }
}
