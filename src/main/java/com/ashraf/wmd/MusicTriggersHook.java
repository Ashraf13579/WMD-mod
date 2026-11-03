package com.ashraf.wmd;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

final class MusicTriggersHook {

    private MusicTriggersHook() {}

    static final int IMPACT_SOON_TICKS = 400;

    private static boolean looked;
    private static Method fire;

    private static final Set<Long> SOON_SENT = Statics.server(new HashSet<>());

    static boolean active() {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("musictriggers")) {
                try {
                    fire = Class.forName("com.ashraf.musictriggers.api.MusicTriggersApi")
                            .getMethod("fireEvent", ServerLevel.class, String.class, BlockPos.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    Wmd.LOGGER.warn("Music Triggers is installed but its API was not found; no music events from WMD", e);
                }
            }
        }
        return fire != null;
    }

    static void fire(ServerLevel level, String name, BlockPos pos) {
        if (!active() || level == null) return;
        try {
            fire.invoke(null, level, name, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wmd.LOGGER.warn("Music Triggers event {} failed; WMD stops sending music events", name, e);
            fire = null;
        }
    }

    static void flight(ServerLevel level, String what, Flight f, BlockPos pos) {
        if (!active() || f == null) return;
        fire(level, "wmd:" + what, pos);
        fire(level, "wmd:" + what + "_" + WarheadType.of(f.build().warheadType()).id, pos);
    }

    static void launch(ServerLevel level, Flight f) {
        if (!active() || f == null) return;
        BlockPos at = BlockPos.containing(f.ox(), f.oy(), f.oz());
        if (Satellites.toOrbit(f.build())) {
            fire(level, "wmd:launch", at);
            fire(level, "wmd:satellite_launch", at);
        } else {
            flight(level, "launch", f, at);
        }
    }

    static void tickFlights(ServerLevel level, List<Flight> flights, long now) {
        if (!active()) return;
        for (Flight f : flights) {
            if (Satellites.toOrbit(f.build()) || Asat.isAsat(f.build())) continue;
            long left = f.endTime() - now;
            if (left > 0 && left <= IMPACT_SOON_TICKS && SOON_SENT.add(f.id())) {
                flight(level, "impact_soon", f, new BlockPos(f.tx(), (int) Math.floor(f.aimY()), f.tz()));
            }
        }
        if (now % 200 == 0 && !SOON_SENT.isEmpty()) {
            Set<Long> alive = new HashSet<>();
            for (Flight f : flights) alive.add(f.id());
            SOON_SENT.retainAll(alive);
        }
    }

    static void artillery(ServerLevel level, ArtShell s) {
        if (!active() || s == null) return;
        String gun = switch (s.gun()) {
            case Artillery.MORTAR -> "mortar";
            case Artillery.HOWITZER -> "howitzer";
            case Artillery.ROCKET -> "mlrs";
            case Artillery.SUPER_GUN -> "supergun";
            default -> "gun";
        };
        BlockPos at = BlockPos.containing(s.tx(), s.ty(), s.tz());
        fire(level, "wmd:artillery_impact", at);
        fire(level, "wmd:artillery_impact_" + gun, at);
    }
}
