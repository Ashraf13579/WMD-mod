package com.ashraf.wmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class MusicTriggersHook {

    private MusicTriggersHook() {}

    static final int IMPACT_SOON_TICKS = 400;

    static final List<String> WAR_EVENTS = List.of("war_declared", "war_started", "war_peace", "war_surrender", "war_draw", "call_to_arms");
    static final boolean WAR_LIVE = false;

    static final List<String> BOSS_EVENTS = List.of("boss_intro", "boss_stage_1", "boss_stage_2", "boss_stage_3", "boss_enrage", "boss_purge", "boss_defeated");

    private static final List<String> FLAGS = List.of("wmd:asteroid_inbound", "wmd:tsar_inbound", "wmd:railgun_charging", "wmd:defcon_1", "wmd:war", "wmd:boss_fight");

    private static boolean looked;
    private static Method fire, fireFor, register, setFlag;

    private static final Set<Long> SOON_SENT = Statics.server(new HashSet<>());

    private static final Map<String, Long> FLAG_UNTIL = Statics.server(new HashMap<>());
    private static final Set<String> FLAGS_ON = Statics.server(new HashSet<>());

    static boolean active() {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("musictriggers")) {
                try {
                    Class<?> api = Class.forName("com.ashraf.musictriggers.api.MusicTriggersApi");
                    fire = api.getMethod("fireEvent", ServerLevel.class, String.class, BlockPos.class);
                    try {
                        fireFor = api.getMethod("fireEventFor", ServerLevel.class, String.class, BlockPos.class, Collection.class);
                        register = api.getMethod("registerEvent", String.class);
                        setFlag = api.getMethod("setFlag", String.class, boolean.class);
                    } catch (NoSuchMethodException old) {
                        Wmd.LOGGER.warn("Music Triggers is older than 18 Nov: only the basic events (update it for team events, flags and the full Event list)");
                    }
                } catch (ReflectiveOperationException | LinkageError e) {
                    Wmd.LOGGER.warn("Music Triggers is installed but its API was not found; no music events from WMD", e);
                }
            }
        }
        return fire != null;
    }

    private static void broken(String what, Exception e) {
        Wmd.LOGGER.warn("Music Triggers call {} failed; WMD stops sending music events", what, e);
        fire = null;
        fireFor = null;
        register = null;
        setFlag = null;
    }

    static void fire(ServerLevel level, String name, BlockPos pos) {
        if (!active() || level == null) return;
        try {
            fire.invoke(null, level, name, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken(name, e);
        }
    }

    static void fireFor(ServerLevel level, String name, BlockPos pos, Collection<UUID> players) {
        if (!active() || fireFor == null || level == null || players == null || players.isEmpty()) return;
        try {
            fireFor.invoke(null, level, name, pos, players);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken(name, e);
        }
    }

    static void flag(ServerLevel level, String name, boolean on, long timeoutTicks) {
        if (!active() || setFlag == null || level == null) return;
        if (on) FLAG_UNTIL.put(name, timeoutTicks > 0 ? level.getServer().overworld().getGameTime() + timeoutTicks : Long.MAX_VALUE);
        else FLAG_UNTIL.remove(name);
        if (on ? !FLAGS_ON.add(name) : !FLAGS_ON.remove(name)) return;
        try {
            setFlag.invoke(null, name, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken(name, e);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!active() || register == null) return;
        Set<String> names = allEvents();
        try {
            for (String n : names) register.invoke(null, n);
            for (String f : FLAGS) setFlag.invoke(null, f, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken("registerEvent", e);
            return;
        }
        FLAGS_ON.clear();
        FLAG_UNTIL.clear();
        Wmd.LOGGER.info("Music Triggers: {} WMD music events + {} flags registered", names.size(), FLAGS.size() - (WAR_LIVE ? 0 : 1));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0 || !active() || setFlag == null) return;
        ServerLevel ow = server.overworld();
        long now = ow.getGameTime();
        for (Map.Entry<String, Long> e : new ArrayList<>(FLAG_UNTIL.entrySet())) if (now >= e.getValue()) flag(ow, e.getKey(), false, 0);
        boolean d1 = false;
        for (CcBuildingBlockEntity b : CommandCenter.buildings()) {
            if (!b.isRemoved() && b.defcon() == 1 && b.getOwner() != null && CommandCenter.activeBuildings(b.getOwner()).contains(b)) { d1 = true; break; }
        }
        flag(ow, "wmd:defcon_1", d1, 0);
    }

    static Set<String> allEvents() {
        Set<String> s = new TreeSet<>();
        List<String> warheads = new ArrayList<>();
        for (WarheadType t : WarheadType.values()) warheads.add(t.id);
        for (String what : List.of("launch", "impact_soon", "impact", "intercepted")) {
            s.add("wmd:" + what);
            for (String w : warheads) s.add("wmd:" + what + "_" + w);
        }
        s.addAll(List.of("wmd:satellite_launch", "wmd:airburst",
                "wmd:railgun_charge", "wmd:railgun_fire", "wmd:railgun_impact", "wmd:rod_release", "wmd:rod_impact",
                "wmd:asteroid_incoming", "wmd:asteroid_impact_soon", "wmd:asteroid_split", "wmd:asteroid_impact",
                "wmd:tsar_incoming", "wmd:tsar_burst_soon", "wmd:tsar_burst",
                "wmd:artillery_impact", "wmd:artillery_impact_mortar", "wmd:artillery_impact_howitzer", "wmd:artillery_impact_mlrs",
                "wmd:artillery_impact_supergun",
                "wmd:incoming", "wmd:incoming_airship", "wmd:lockdown", "wmd:counter_strike_armed",
                "wmd:reactor_alarm", "wmd:reactor_steam", "wmd:reactor_imminent", "wmd:reactor_meltdown", "wmd:being_imaged",
                "wmd:rwr_lock", "wmd:rwr_missile", "wmd:aam_fire", "wmd:agm_fire", "wmd:eject", "wmd:bomb_release", "wmd:bomb_impact",
                "wmd:ship_crash", "wmd:torpedo_launch", "wmd:torpedo_hit", "wmd:depth_charge", "wmd:anti_torpedo", "wmd:naval_gun_fire",
                "wmd:ship_hit", "wmd:sub_hit"));
        for (int i = 1; i <= 5; i++) s.add("wmd:defcon_" + i);
        for (BombType b : BombType.values()) {
            s.add("wmd:bomb_release_" + b.id);
            s.add("wmd:bomb_impact_" + b.id);
        }
        for (TorpedoKind k : TorpedoKind.values()) {
            s.add("wmd:torpedo_launch_" + k.id);
            s.add("wmd:torpedo_hit_" + k.id);
        }
        s.addAll(teamEvents());
        if (WAR_LIVE) for (String w : WAR_EVENTS) s.add("wmd:" + w);
        for (String b : BOSS_EVENTS) s.add("wmd:" + b);
        return s;
    }

    private static Set<String> teamEvents() {
        Set<String> out = new LinkedHashSet<>();
        try (InputStream in = MusicTriggersHook.class.getResourceAsStream("/assets/wmd/lang/en_us.json")) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : o.keySet()) if (k.startsWith("log.wmd.") || k.startsWith(ALARM_KEY)) out.add(teamName(k));
        } catch (Exception e) {
            Wmd.LOGGER.warn("Music Triggers: could not list the battle-log events", e);
        }
        return out;
    }

    private static final String ALARM_KEY = "message.wmd.cc.alarm.";

    private static String teamName(String logKey) {
        String k = logKey.startsWith("log.wmd.") ? logKey.substring(8) : logKey.startsWith(ALARM_KEY) ? "alarm_" + logKey.substring(ALARM_KEY.length()) : logKey;
        return "wmd:team_" + k.replace('.', '_');
    }

    static void team(MinecraftServer server, UUID owner, String logKey) {
        if (!active() || fireFor == null || server == null || owner == null || logKey == null) return;
        Set<UUID> who = new LinkedHashSet<>();
        who.add(owner);
        for (ServerPlayer p : CcDefcon.team(server, owner)) who.add(p.getUUID());
        fireFor(server.overworld(), teamName(logKey), null, who);
    }

    static void crew(ServerLevel level, String name, UUID ship, BlockPos pos) {
        if (!active() || fireFor == null || ship == null) return;
        Collection<UUID> who = ShipFaction.occupants(level, ship).players().keySet();
        if (!who.isEmpty()) fireFor(level, name, pos, new ArrayList<>(who));
    }

    static void kind(ServerLevel level, String what, String kind, BlockPos pos) {
        if (!active()) return;
        fire(level, "wmd:" + what, pos);
        if (kind != null && !kind.isEmpty()) fire(level, "wmd:" + what + "_" + kind, pos);
    }

    static void flight(ServerLevel level, String what, Flight f, BlockPos pos) {
        if (!active() || f == null) return;
        kind(level, what, WarheadType.of(f.build().warheadType()).id, pos);
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
        kind(level, "artillery_impact", gun, BlockPos.containing(s.tx(), s.ty(), s.tz()));
    }

    static void boss(ServerLevel level, String what, BlockPos pos, Collection<UUID> players) {
        if (!active() || !BOSS_EVENTS.contains(what)) return;
        if (players == null || players.isEmpty()) fire(level, "wmd:" + what, pos);
        else fireFor(level, "wmd:" + what, pos, players);
    }

    static void war(ServerLevel level, String what, BlockPos pos, Collection<UUID> players) {
        if (!active() || !WAR_EVENTS.contains(what)) return;
        if (players == null) fire(level, "wmd:" + what, pos);
        else fireFor(level, "wmd:" + what, pos, players);
    }
}
