package com.ashraf.wmd;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcJammer {

    private CcJammer() {}

    private static final Set<CcEwRackBlockEntity> RACKS = Collections.newSetFromMap(new WeakHashMap<>());

    static void join(CcEwRackBlockEntity r) { RACKS.add(r); }
    static void leave(CcEwRackBlockEntity r) { RACKS.remove(r); }

    public static boolean rackOn(UUID owner) {
        if (owner == null) return false;
        for (CcEwRackBlockEntity r : RACKS) if (!r.isRemoved() && owner.equals(r.getOwner()) && r.isOnline() && r.isEnabled()) return true;
        return false;
    }

    public static boolean jammed(ServerLevel level, Flight f) {
        return f != null && jammer(level, f) != null;
    }

    public static GroundStationBlockEntity jammer(ServerLevel level, Flight f) {
        GroundStationBlockEntity st = GroundStationBlockEntity.jammerNear(level, Uplink.owner(level.getServer(), f), f.tx() + 0.5, f.tz() + 0.5, Balance.CC_JAM_RADIUS);
        if (st != null && JAM_LOGGED.add(f.id())) {
            if (JAM_LOGGED.size() > 512) JAM_LOGGED.clear();
            JAM_LOGGED.add(f.id());
            CcBattleLog.onJam(level, f, st);
        }
        return st;
    }

    private static final Set<Long> JAM_LOGGED = new java.util.HashSet<>();

    public static boolean jammedAt(Level level, UUID attacker, double x, double z) {
        return GroundStationBlockEntity.jammerNear(level, attacker, x, z, Balance.CC_JAM_RADIUS) != null;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { RACKS.clear(); JAM_LOGGED.clear(); }
}
