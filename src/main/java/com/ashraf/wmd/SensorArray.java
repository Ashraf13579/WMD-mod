package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class SensorArray {

    private SensorArray() {}

    public record Threat(String kind, String from, long end) {}

    public record Contact(String owner, int slot, int modules, int health, int gx, int gz, int dist) {}

    private static final Map<Integer, List<Threat>> THREATS = new HashMap<>();
    private static final Map<UUID, Set<Integer>> SEEN = new HashMap<>();

    static void clearStatics() { THREATS.clear(); SEEN.clear(); }

    static Station.Module module(MinecraftServer server, int slot) {
        for (Station.Module m : Station.modules(server, slot)) if (Station.SENSOR.equals(m.type()) && m.built()) return m;
        return null;
    }

    static boolean ready(ServerLevel orbit, Station.Module m, long now) {
        if (m == null || !m.built() || Station.wrecked(orbit.getServer(), m)) return false;
        return !(orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) || be.deploy() <= 0L || now >= be.deploy() + Balance.SENSOR_DEPLOY_TICKS;
    }

    static boolean working(MinecraftServer server, int slot) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        return orbit != null && ready(orbit, module(server, slot), orbit.getGameTime()) && StationLife.powered(slot);
    }

    static int state(MinecraftServer server, int slot) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module m = module(server, slot);
        if (orbit == null || m == null) return 0;
        if (Station.wrecked(server, m)) return 1;
        if (!ready(orbit, m, orbit.getGameTime())) return 2;
        return StationLife.powered(slot) ? 3 : 1;
    }

    public static boolean incoming(MinecraftServer server, int slot, String kind, String from, long end) {
        if (!working(server, slot)) return false;
        THREATS.computeIfAbsent(slot, k -> new ArrayList<>()).add(new Threat(kind, from, end));
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long secs = orbit == null ? 0 : Math.max(0L, (end - orbit.getGameTime()) / 20);
        Component msg = Component.translatable("message.wmd.sensor.incoming", Component.translatable("sensor.wmd.threat." + kind), from, secs).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        ServerPlayer op = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (op != null) op.sendSystemMessage(msg);
        if (orbit != null) for (ServerPlayer p : orbit.players()) if (p != op && Orbit.slotAt(p.getX(), p.getZ()) == slot) p.sendSystemMessage(msg);
        if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.ALARM, "log.wmd.sensor.incoming", "#sensor.wmd.threat." + kind, from, Long.toString(secs));
        return true;
    }

    static List<Threat> threats(int slot, long now) {
        List<Threat> l = THREATS.get(slot);
        if (l == null) return List.of();
        l.removeIf(t -> t.end() <= now);
        return List.copyOf(l);
    }

    static boolean test(ServerPlayer p, int seconds) {
        int slot = Orbit.slotOf(p.server, p.getUUID());
        ServerLevel orbit = p.server.getLevel(Orbit.ORBIT);
        return orbit != null && incoming(p.server, slot, "test", p.getGameProfile().getName(), orbit.getGameTime() + seconds * 20L);
    }

    static void tick(ServerLevel orbit, int slot, List<Station.Module> all, long now) {
        Station.Module m = null;
        for (Station.Module x : all) if (Station.SENSOR.equals(x.type()) && x.built()) m = x;
        if (m == null) { THREATS.remove(slot); return; }
        MinecraftServer server = orbit.getServer();
        boolean work = ready(orbit, m, now) && StationLife.powered(slot);
        List<Threat> th = work ? threats(slot, now) : List.of();
        if (!work) THREATS.remove(slot);
        if (orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setMode(!work ? 0 : th.isEmpty() ? 1 : 2);
        if (!work) return;

        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        if (owner != null) {
            Set<Integer> seen = SEEN.computeIfAbsent(owner, k -> new HashSet<>());
            for (Contact c : contacts(server, slot))
                if (seen.add(c.slot())) CcBattleLog.add(server, owner, CcBattleLog.SAT, "log.wmd.sensor.station", c.owner(), Integer.toString(c.slot()), Integer.toString(c.gx()), Integer.toString(c.gz()));
        }

        if (th.isEmpty()) return;
        Threat first = th.get(0);
        for (Threat t : th) if (t.end() < first.end()) first = t;
        long secs = Math.max(0L, (first.end() - now) / 20);
        Component bar = Component.translatable("message.wmd.sensor.bar", Component.translatable("sensor.wmd.threat." + first.kind()), first.from(), secs).withStyle(ChatFormatting.RED);
        for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == slot) p.displayClientMessage(bar, true);
        if ((now / 20) % 2 == 0) orbit.playSound(null, Station.controller(m), WmdSounds.SA_CONTACT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    static List<Contact> contacts(MinecraftServer server, int slot) {
        List<Contact> out = new ArrayList<>();
        boolean works = working(server, slot);
        Station.Data d = Station.Data.get(server);
        for (Map.Entry<Integer, List<Station.Module>> e : d.slots.entrySet()) {
            int s = e.getKey();
            if (s == slot || Station.core(server, s) == null) continue;
            double dd = Rendezvous.dist(server, slot, s);
            if (!works && dd > Balance.RDV_CLOSE) continue;
            int mods = 0, hp = 0, max = 0;
            for (Station.Module m : e.getValue()) {
                if (!m.built()) continue;
                mods++;
                hp += Math.max(0, Station.health(server, m));
                max += Station.maxHealth(m.type());
            }
            UUID o = Orbit.OrbitSlots.get(server).ownerOf(s);
            Orbit.Ground g = Orbit.ground(server, s);
            out.add(new Contact(o == null ? "?" : CcIntel.name(server, o), s, mods, max <= 0 ? 0 : Math.round(100.0F * hp / max), g.x(), g.z(),
                    dd == Double.MAX_VALUE ? -1 : (int) Math.round(dd)));
        }
        out.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        return out;
    }

    static void onLaunch(ServerLevel level, Flight f, Set<UUID> warned) {
        MinecraftServer server = level.getServer();
        Station.Data d = Station.Data.get(server);
        for (int slot : d.slots.keySet()) {
            UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
            if (owner == null || warned.contains(owner) || !working(server, slot)) continue;
            Orbit.Ground g = Orbit.ground(server, slot);
            if (g.dim() != level.dimension() || Math.abs(f.ox() - g.x()) > Balance.SENSOR_GROUND_R || Math.abs(f.oz() - g.z()) > Balance.SENSOR_GROUND_R) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (f.isOwnedBy(owner, p != null ? p.getGameProfile().getName() : null)) continue;
            warned.add(owner);
            int x = (int) Math.floor(f.ox()), z = (int) Math.floor(f.oz()), secs = Math.max(0, f.duration() / 20);
            CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_IN, "log.wmd.sensor.launch", Integer.toString(x), Integer.toString(z));
            if (p != null) p.sendSystemMessage(Component.translatable(Recon.climbsToOrbit(f) ? "message.wmd.sensor.launch_orbit" : "message.wmd.sensor.launch", x, z, secs).withStyle(ChatFormatting.GOLD));
        }
    }
}
