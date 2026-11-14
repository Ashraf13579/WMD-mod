package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class CcBattleLog extends SavedData {

    private static final String NAME = "wmd_cc_battlelog";
    public static final int RING = 64;
    public static final int LAUNCH_OUT = 0, LAUNCH_IN = 1, INTERCEPT = 2, LOST = 3, HIT = 4, SAT = 5, JAM = 6, ALARM = 7, DEFCON = 8, BDA = 9, COUNTER = 10;
    static final int MAX_ARGS = 6;

    public static final int AIRSHIP = 11;

    public static final int DRONE = 12;

    public record Event(long timeMs, int kind, String key, List<String> args) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("t", timeMs); t.putInt("k", kind); t.putString("key", key);
            ListTag a = new ListTag();
            for (String s : args) a.add(StringTag.valueOf(s));
            t.put("a", a);
            return t;
        }
        static Event load(CompoundTag t) {
            List<String> a = new ArrayList<>();
            for (Tag x : t.getList("a", Tag.TAG_STRING)) a.add(x.getAsString());
            return new Event(t.getLong("t"), t.getInt("k"), t.getString("key"), List.copyOf(a));
        }
        void write(FriendlyByteBuf buf) {
            buf.writeLong(timeMs); buf.writeVarInt(kind); buf.writeUtf(key, 128);
            int n = Math.min(MAX_ARGS, args.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeUtf(args.get(i), 128);
        }
        static Event read(FriendlyByteBuf buf) {
            long t = buf.readLong();
            int k = buf.readVarInt();
            String key = buf.readUtf(128);
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_ARGS) throw new IllegalArgumentException("battle log: too many args " + n);
            List<String> a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) a.add(buf.readUtf(128));
            return new Event(t, k, key, List.copyOf(a));
        }

        public Component text() { return CcBattleLog.text(key, args); }
    }

    public static Component text(String key, List<String> args) {
        Object[] a = new Object[args.size()];
        for (int i = 0; i < a.length; i++) {
            String s = args.get(i);
            a[i] = s.startsWith("#") && s.length() > 1 ? Component.translatable(s.substring(1)) : s;
        }
        return Component.translatable(key, a);
    }

    private final Map<UUID, List<Event>> rings = new HashMap<>();

    public static CcBattleLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CcBattleLog::new, CcBattleLog::load, null), NAME);
    }

    public static List<Event> events(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return List.of();
        List<Event> l = get(server).rings.get(owner);
        return l == null ? List.of() : List.copyOf(l);
    }

    public static void add(MinecraftServer server, UUID owner, int kind, String key, String... args) {
        if (server == null || owner == null) return;
        CcBattleLog d = get(server);
        List<Event> l = d.rings.computeIfAbsent(owner, k -> new ArrayList<>());
        List<String> a = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_ARGS, args.length); i++) a.add(args[i] == null ? "" : args[i].length() > 120 ? args[i].substring(0, 120) : args[i]);
        l.add(new Event(System.currentTimeMillis(), kind, key, List.copyOf(a)));
        while (l.size() > RING) l.remove(0);
        d.setDirty();
    }

    static void clear(MinecraftServer server, UUID owner) {
        CcBattleLog d = get(server);
        if (d.rings.remove(owner) != null) d.setDirty();
    }

    static UUID ownerOf(MinecraftServer server, Flight f) {
        if (f == null) return null;
        try { if (!f.ownerId().isEmpty()) return UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        ServerPlayer p = server.getPlayerList().getPlayerByName(f.launcher());
        return p != null ? p.getUUID() : null;
    }

    private static String warhead(Flight f) { return f.build() == null || f.build().warheadType().isEmpty() ? "?" : "#warhead.wmd." + f.build().warheadType(); }

    private static String i(double v) { return Long.toString(Math.round(v)); }

    public static void onLaunch(ServerLevel level, Flight f) {
        if (f == null || f.build() == null || f.build().isInterceptor()) return;
        UUID o = ownerOf(level.getServer(), f);
        if (o == null) return;
        if (Satellites.toOrbit(f.build()) || Asat.isAsat(f.build())) add(level.getServer(), o, LAUNCH_OUT, "log.wmd.launch_sat", i(f.ox()), i(f.oz()));
        else add(level.getServer(), o, LAUNCH_OUT, f.isCruise() ? "log.wmd.cruise_out" : "log.wmd.launch_out", Integer.toString(f.tier()), warhead(f), Integer.toString(f.tx()), Integer.toString(f.tz()),
                OrbitMath.mmss(f.duration() * 50L));
    }

    static void onWarning(MinecraftServer server, UUID owner, CommandCenter.Warning w) {
        if (CcContactWatch.isNaval(w.id())) return;
        if (AirshipWatch.isWarning(w.id())) return;
        if (DroneWarnings.isDrone(w.id())) { add(server, owner, LAUNCH_IN, "log.wmd.drone_in", "T" + w.tier(), Integer.toString(w.ox()), Integer.toString(w.oz())); return; }
        if (DroneWarnings.isBomb(w.id())) { add(server, owner, LAUNCH_IN, "log.wmd.bomb_in", Integer.toString(w.ix()), Integer.toString(w.iz())); return; }
        if (SuperGun.isWarning(w.id())) {
            add(server, owner, LAUNCH_IN, "log.wmd.super_gun_in", Integer.toString(w.ox()), Integer.toString(w.oz()), Integer.toString(w.ix()), Integer.toString(w.iz()));
            return;
        }
        if (w.sat()) add(server, owner, LAUNCH_IN, "log.wmd.launch_in_sat", Integer.toString(w.ox()), Integer.toString(w.oz()));
        else add(server, owner, LAUNCH_IN, cruiseFlight(server, w.id()) ? "log.wmd.cruise_in" : "log.wmd.launch_in", Integer.toString(w.ox()), Integer.toString(w.oz()), Integer.toString(w.ix()), Integer.toString(w.iz()));
        if (w.hyper() || w.intel()) add(server, owner, LAUNCH_IN, "log.wmd.launch_intel", (w.hyper() ? "HYPERSONIC " : "") + (w.intel() ? w.warhead() + " T" + w.tier() : ""),
                w.intel() ? (w.seeker().isEmpty() ? "-" : w.seeker()) : "?");
    }

    private static boolean cruiseFlight(MinecraftServer server, long id) {
        if (id <= 0L) return false;
        for (ServerLevel l : server.getAllLevels()) {
            Flight f = FlightManager.get(l).find(id);
            if (f != null) return f.isCruise();
        }
        return false;
    }

    public static void onImpact(ServerLevel level, Flight f, int gy) {
        if (f == null || f.build() == null || f.build().isInterceptor()) return;
        UUID o = ownerOf(level.getServer(), f);
        if (o != null) add(level.getServer(), o, HIT, f.isCruise() ? "log.wmd.cruise_hit" : "log.wmd.hit", Integer.toString(f.tier()), warhead(f), Integer.toString(f.tx()), Integer.toString(gy), Integer.toString(f.tz()));
    }

    public static void onKilled(ServerLevel level, Flight f) {
        if (f == null || f.build() == null) return;
        UUID o = ownerOf(level.getServer(), f);
        if (o != null) add(level.getServer(), o, LOST, f.isCruise() ? "log.wmd.cruise_lost" : "log.wmd.lost", Integer.toString(f.tier()), warhead(f), Integer.toString(f.tx()), Integer.toString(f.tz()));
    }

    public static void onIntercept(ServerLevel level, Vec3 from, Flight f) {
        if (f == null || from == null) return;
        InterceptorSite best = null;
        double bd = 24.0 * 24.0;
        for (InterceptorSite s : CommandCenter.allSites()) {
            if (!(s instanceof net.minecraft.world.level.block.entity.BlockEntity be) || be.getLevel() != level || be.isRemoved()) continue;
            double d = SableBridge.worldPos(level, s.getBlockPos()).distanceToSqr(from);
            if (d <= bd) { bd = d; best = s; }
        }
        if (best == null || best.getOwner() == null) return;
        add(level.getServer(), best.getOwner(), INTERCEPT, f.isCruise() ? "log.wmd.cruise_intercept" : "log.wmd.intercept", f.launcher(), Integer.toString(f.tier()), i(from.x), i(from.z));
    }

    public static void onSatEngage(MinecraftServer server, UUID owner, int satId, Flight f, boolean kill) {
        if (owner != null && f != null) add(server, owner, INTERCEPT, kill ? "log.wmd.intsat_kill" : "log.wmd.intsat_miss", Integer.toString(satId), f.launcher());
    }

    public static void onAsat(MinecraftServer server, OrbitMath.Sat s, boolean hit, String shooterName, ServerPlayer shooter) {
        String type = "#" + typeKey(s.type());
        if (shooter != null) add(server, shooter.getUUID(), SAT, hit ? "log.wmd.asat_hit" : "log.wmd.asat_miss", type, Integer.toString(s.id()), s.ownerName());
        add(server, s.owner(), SAT, hit ? "log.wmd.asat_lost" : "log.wmd.asat_survived", type, Integer.toString(s.id()), shooterName == null ? "?" : shooterName);
    }

    public static void onHack(MinecraftServer server, OrbitMath.Sat s, UUID hacker, String hackerName) {
        String type = "#" + typeKey(s.type());
        add(server, hacker, SAT, "log.wmd.hacked", type, Integer.toString(s.id()), s.ownerName());
        add(server, s.owner(), SAT, "log.wmd.stolen", type, Integer.toString(s.id()), hackerName);
    }

    static void onJam(ServerLevel level, Flight f, GroundStationBlockEntity st) {
        UUID att = ownerOf(level.getServer(), f);
        if (st.getOwner() != null) add(level.getServer(), st.getOwner(), JAM, "log.wmd.jam_us", f.launcher(), Integer.toString(f.tx()), Integer.toString(f.tz()));
        if (att != null) add(level.getServer(), att, JAM, "log.wmd.jammed", Integer.toString(f.tx()), Integer.toString(f.tz()), st.getOwnerName());
    }

    static void onAlarm(MinecraftServer server, UUID owner, CcAlarms.Event e) {
        add(server, owner, ALARM, "message.wmd.cc.alarm." + e.kindName(), e.actor().isEmpty() ? "#message.wmd.cc.alarm.someone" : e.actor(), "#" + e.machine(),
                Integer.toString(e.x()), Integer.toString(e.y()), Integer.toString(e.z()));
    }

    static String typeKey(String type) { return "sat.wmd.type." + type; }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag all = new ListTag();
        for (Map.Entry<UUID, List<Event>> me : rings.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", me.getKey());
            ListTag l = new ListTag();
            for (Event e : me.getValue()) l.add(e.save());
            t.put("events", l);
            all.add(t);
        }
        tag.put("rings", all);
        return tag;
    }

    public static CcBattleLog load(CompoundTag tag, HolderLookup.Provider registries) {
        CcBattleLog d = new CcBattleLog();
        for (Tag raw : tag.getList("rings", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("owner")) continue;
            List<Event> l = new ArrayList<>();
            for (Tag e : t.getList("events", Tag.TAG_COMPOUND)) l.add(Event.load((CompoundTag) e));
            while (l.size() > RING) l.remove(0);
            d.rings.put(t.getUUID("owner"), l);
        }
        return d;
    }
}
