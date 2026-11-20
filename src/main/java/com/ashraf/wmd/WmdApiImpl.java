package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongPredicate;

import com.ashraf.wmd.api.WmdApi;
import com.ashraf.wmd.api.event.WmdBlastEvent;
import com.ashraf.wmd.api.event.WmdCraftDownedEvent;
import com.ashraf.wmd.api.event.WmdInterceptEvent;
import com.ashraf.wmd.api.event.WmdLaunchEvent;
import com.ashraf.wmd.api.event.WmdNuclearDetonationEvent;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

public final class WmdApiImpl {

    private WmdApiImpl() {}

    private static long lastFail;

    private static void post(Event e) {
        try { NeoForge.EVENT_BUS.post(e); }
        catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastFail > 60_000L) { lastFail = now; Wmd.LOGGER.warn("[api] a listener of {} threw: {}", e.getClass().getSimpleName(), t.toString()); }
        }
    }

    public static UUID shooter() { return ShipStrike.shooter(); }

    public static List<WmdApi.FlightInfo> flights(ServerLevel level) {
        List<WmdApi.FlightInfo> out = new ArrayList<>();
        for (Flight f : FlightManager.get(level).flights())
            out.add(new WmdApi.FlightInfo(f.id(), f.tier(), f.ox(), f.oz(), f.tx(), f.tz(), f.start() + f.duration(), owner(f), f.launcher(), f.cruise() != null, warhead(f)));
        return out;
    }

    public static boolean restore(ServerLevel level, long fromMs, long toMs, LongPredicate chunkOk, String label) {
        if (level == null || chunkOk == null || !Config.Server.rollbackLog()) return false;
        RollbackJob.start(level, RollbackLog.allRegions(level.getServer(), level.dimension().location().toString()), fromMs, toMs, ck -> true, null, chunkOk, null,

                "api restore: " + (label == null ? "?" : label), false);
        return true;
    }

    static UUID owner(Flight f) {
        try { return f.ownerId() == null || f.ownerId().isEmpty() ? null : UUID.fromString(f.ownerId()); } catch (IllegalArgumentException e) { return null; }
    }

    static String warhead(Flight f) {
        String w = f.build() == null ? null : f.build().warheadType();
        return w == null || w.isEmpty() ? WarheadType.HE.id : w;
    }

    static void launched(ServerLevel level, Flight f) {
        post(new WmdLaunchEvent(level, f.id(), f.tier(), f.ox(), f.oz(), f.tx(), f.tz(), owner(f), warhead(f), f.cruise() != null));
    }

    static void detonated(ServerLevel level, int x, int y, int z, int tier, WarheadType t) {
        String kind = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> s.map(f -> f.getDeclaringClass().getSimpleName())
                .filter(n -> !n.equals("WmdApiImpl") && !n.equals("WarheadFx") && !n.equals("Forcefield") && !n.equals("WarTreaty")).findFirst().orElse(""));
        blast(level, x + 0.5, y, z + 0.5, Missiles.craterRadius(tier) * Math.max(0.3, t.crater), kindOf(kind, t), t.id, ShipStrike.shooter());
    }

    static String kindOf(String cls, WarheadType t) {
        if (t == WarheadType.RAILGUN || t == WarheadType.ORBITAL_ROD || t == WarheadType.ASTEROID) return "orbital";
        return switch (cls) {
            case "FlightManager", "AirMissiles", "AirFrag", "HandMissileEntity", "WmdCommands" -> "missile";
            case "BombEntity" -> "bomb";
            case "SpecialShells", "SuperGunServer", "ArtilleryManager" -> "artillery";
            case "BigDrones" -> "drone";
            case "NuclearReactorBlockEntity", "NavalReactorBlockEntity", "FusionReactorBlockEntity", "AntimatterStoreBlockEntity", "CookOff", "BaySabotage" -> "reactor";
            case "RodOfDeath", "RailgunStrikes", "AsteroidDrop" -> "orbital";
            default -> "other";
        };
    }

    static void blast(ServerLevel level, double x, double y, double z, double radius, String kind, String warhead, UUID shooter) {
        post(new WmdBlastEvent(level, x, y, z, radius, kind, warhead, shooter));
    }

    static void nuclear(ServerLevel level, int x, int z, WarheadType t, boolean accident) {
        post(new WmdNuclearDetonationEvent(level, x, z, t.id, accident ? null : ShipStrike.shooter(), accident));
    }

    static void intercept(ServerLevel level, String what, int tier, UUID defender, UUID attacker) {
        post(new WmdInterceptEvent(level, what, tier, defender, attacker));
    }

    static void craftDowned(ServerLevel level, UUID craft, UUID shooter, int blocks) {
        UUID owner = craft == null ? null : Crafts.owner(level, craft);
        boolean air = craft != null && AirGate.on() && AirPicture.isAircraft(level, craft);
        post(new WmdCraftDownedEvent(level, craft, owner, shooter, blocks, air));
    }
}
