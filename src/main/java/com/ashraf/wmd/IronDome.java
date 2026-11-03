package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class IronDome {

    private IronDome() {}

    public static final int MARKERS = 8;

    public static final int M_OK = 0, M_NO_CIWS = 1, M_NO_POWER = 2, M_NO_AMMO = 3, M_NO_SEEK = 4, M_UNLOADED = 5, M_UNSET = 6;

    public record Target(boolean shell, long id) {}

    public static int[] marker(int i, int cx, int cz, int r) {
        double a = Math.PI * 2.0 * i / MARKERS;
        return new int[] { cx + (int) Math.round(Math.sin(a) * r), cz - (int) Math.round(Math.cos(a) * r) };
    }

    public static int clampRadius(int r) { return Math.max(Balance.DOME_RADIUS_MIN, Math.min(Balance.DOME_RADIUS_MAX, r)); }

    private static final Set<CcIronDomeBlockEntity> CONSOLES = Collections.newSetFromMap(new WeakHashMap<>());

    static void join(CcIronDomeBlockEntity c) { CONSOLES.add(c); }
    static void leave(CcIronDomeBlockEntity c) { CONSOLES.remove(c); }

    static Dome domeOf(ServerLevel level, CiwsBlockEntity ciws) {
        for (CcIronDomeBlockEntity c : CONSOLES) {
            if (c.isRemoved()) continue;
            Dome d = c.dome();
            if (d.active && d.level == level && d.has(ciws)) return d;
        }
        return null;
    }

    public static boolean downFor(UUID owner) {
        if (owner == null) return false;
        for (CcIronDomeBlockEntity c : CONSOLES)
            if (!c.isRemoved() && owner.equals(c.getOwner()) && c.hasCentre() && c.isOnline() && !c.dome().active) return true;
        return false;
    }

    static final class Dome {
        ServerLevel level;
        UUID owner;
        String ownerName = "";
        int cx, cz, r;
        boolean active;
        final int[] state = new int[MARKERS];
        final CiwsBlockEntity[] guns = new CiwsBlockEntity[MARKERS];

        private long assignedAt = Long.MIN_VALUE;
        private final Map<CiwsBlockEntity, Target> assign = new HashMap<>();
        private final Map<Target, Set<CiwsBlockEntity>> tried = new HashMap<>();

        Dome() { java.util.Arrays.fill(state, M_UNSET); }

        boolean has(CiwsBlockEntity c) {
            for (CiwsBlockEntity g : guns) if (g == c) return true;
            return false;
        }

        List<CiwsBlockEntity> members() {
            List<CiwsBlockEntity> l = new ArrayList<>();
            for (CiwsBlockEntity g : guns) if (g != null && !g.isRemoved() && g.getLevel() == level) l.add(g);
            return l;
        }

        boolean check(ServerLevel lv, UUID own, String ownName, boolean set, boolean online, int x, int z, int radius) {
            boolean geo = level != null && (lv != level || x != cx || z != cz || radius != r);
            level = lv;
            owner = own;
            ownerName = ownName == null ? "" : ownName;
            cx = x;
            cz = z;
            r = radius;
            boolean was = active;
            if (!set || own == null) {
                java.util.Arrays.fill(state, M_UNSET);
                java.util.Arrays.fill(guns, null);
                active = false;
                return was != active;
            }
            List<CiwsBlockEntity> mine = new ArrayList<>();
            for (InterceptorSite s : CommandCenter.allSites())
                if (s instanceof CiwsBlockEntity c && !c.isRemoved() && c.getLevel() == lv && own.equals(c.getOwner())) mine.add(c);
            boolean all = true;
            for (int i = 0; i < MARKERS; i++) {
                int[] m = marker(i, x, z, radius);
                if (!lv.hasChunkAt(new BlockPos(m[0], lv.getMinBuildHeight(), m[1]))) {
                    guns[i] = null;
                    if (geo || state[i] == M_UNSET) state[i] = M_UNLOADED;
                    if (state[i] != M_OK) all = false;
                    continue;
                }
                CiwsBlockEntity best = null;
                double bd = (Balance.DOME_MARKER_TOL + 0.5) * (Balance.DOME_MARKER_TOL + 0.5);
                for (CiwsBlockEntity c : mine) {
                    Vec3 cw = SableBridge.worldPos(lv, c.getBlockPos());
                    double dx = cw.x - 0.5 - m[0], dz = cw.z - 0.5 - m[1], d = dx * dx + dz * dz;
                    if (d <= bd) { bd = d; best = c; }
                }
                guns[i] = best;
                state[i] = best == null ? M_NO_CIWS : best.energy() < Balance.CRAM_BURST_FE ? M_NO_POWER : best.loaded() <= 0 ? M_NO_AMMO
                        : best.linkState() != TrackNet.RADAR_SEEKING && best.linkState() != TrackNet.LOCKED ? M_NO_SEEK : M_OK;
                if (state[i] != M_OK) all = false;
            }
            active = online && all;
            if (!active) { assign.clear(); tried.clear(); }
            return was != active;
        }

        Target targetOf(CiwsBlockEntity c, long now) {
            if (assignedAt != now) assign(now);
            return assign.get(c);
        }

        void missed(CiwsBlockEntity c, Target t) {
            tried.computeIfAbsent(t, k -> new HashSet<>()).add(c);
            assign.remove(c);
        }

        void done(Target t) {
            assign.values().removeIf(t::equals);
            tried.remove(t);
        }

        Vec3 position(Target t, double time) {
            if (level == null) return null;
            if (t.shell()) {
                for (ArtShell s : ArtilleryManager.get(level).shells()) if (s.id() == t.id()) return s.position(time);
                return null;
            }
            Flight f = FlightManager.get(level).find(t.id());
            return f == null ? null : f.position(time);
        }

        private void assign(long now) {
            assignedAt = now;
            assign.clear();
            List<CiwsBlockEntity> free = new ArrayList<>();
            for (CiwsBlockEntity g : members()) if (g.loaded() > 0 && g.energy() >= Balance.CRAM_BURST_FE) free.add(g);
            if (free.isEmpty() || level == null) { tried.clear(); return; }
            List<Object[]> targets = new ArrayList<>();

            FlightManager mgr = FlightManager.get(level);
            Set<Long> ids = new HashSet<>();
            for (CiwsBlockEntity g : members()) ids.addAll(g.trackedIds());
            for (long id : ids) {
                Flight f = mgr.find(id);
                if (f == null) continue;
                targets.add(new Object[] { new Target(false, id), f.position(now), f.endTime() - now });
            }

            for (ArtShell s : ArtilleryManager.get(level).shells()) {
                if (owner.equals(s.owner()) || !s.interceptable() || now < s.start() + 5) continue;
                targets.add(new Object[] { new Target(true, s.id()), s.position(now), s.endTime() - now });
            }
            Set<Target> live = new HashSet<>();
            for (Object[] o : targets) live.add((Target) o[0]);
            tried.keySet().retainAll(live);
            targets.sort((a, b) -> Long.compare((Long) a[2], (Long) b[2]));
            for (Object[] o : targets) {
                if (free.isEmpty()) break;
                Target t = (Target) o[0];
                Vec3 p = (Vec3) o[1];
                double range = t.shell() ? Balance.CRAM_RANGE : Balance.CIWS_RANGE;
                Set<CiwsBlockEntity> no = tried.getOrDefault(t, Set.of());
                CiwsBlockEntity best = null;
                double bd = range * range;
                for (CiwsBlockEntity g : free) {
                    if (no.contains(g)) continue;
                    Vec3 gp = SableBridge.worldPos(level, g.getBlockPos());
                    double d = gp.distanceToSqr(p);
                    if (d >= bd) continue;
                    if (Math.toDegrees(Math.atan2(p.y - gp.y, Math.sqrt(Math.max(0.0, d - (p.y - gp.y) * (p.y - gp.y))))) <= 2.0) continue;
                    bd = d;
                    best = g;
                }
                if (best == null) continue;
                assign.put(best, t);
                free.remove(best);
            }
        }
    }

    static void announceDown(MinecraftServer server, UUID owner, int cx, int cz, int downMarkers) {
        if (server == null || owner == null) return;
        CcBattleLog.add(server, owner, CcBattleLog.ALARM, "log.wmd.dome_down", Integer.toString(cx), Integer.toString(cz), Integer.toString(downMarkers));
        if (CcDefcon.level(owner) <= 4) CcDefcon.banner(server, owner, CcDefcon.B_INCOMING, true, "hud.wmd.cc.alert.dome_down", Integer.toString(cx), Integer.toString(cz));
        Wmd.LOGGER.info("Iron Dome of {} at {} {} is DOWN ({} marker(s) not ready)", owner, cx, cz, downMarkers);
    }

    static void announceUp(MinecraftServer server, UUID owner, int cx, int cz, int r) {
        if (server == null || owner == null) return;
        CcBattleLog.add(server, owner, CcBattleLog.INTERCEPT, "log.wmd.dome_up", Integer.toString(cx), Integer.toString(cz), Integer.toString(r));
        Wmd.LOGGER.info("Iron Dome of {} at {} {} radius {} is ON", owner, cx, cz, r);
    }

    private static final Set<UUID> SENT = new HashSet<>();

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        if (CONSOLES.isEmpty() && SENT.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<DomeMarkersPayload.Entry> l = new ArrayList<>();
            for (CcIronDomeBlockEntity c : CONSOLES) {
                if (c.isRemoved() || !c.hasCentre() || !c.isOnline() || c.getLevel() != p.level() || c.getOwner() == null) continue;
                if (!p.getUUID().equals(c.getOwner()) && !c.security().trusted().containsKey(p.getUUID())) continue;
                Dome d = c.dome();
                int ok = 0;
                for (int i = 0; i < MARKERS; i++) if (d.state[i] == M_OK) ok |= 1 << i;
                l.add(new DomeMarkersPayload.Entry(c.centreX(), c.centreZ(), c.radius(), d.active, ok));
                if (l.size() >= DomeMarkersPayload.MAX) break;
            }
            if (l.isEmpty() && !SENT.contains(p.getUUID())) continue;
            if (l.isEmpty()) SENT.remove(p.getUUID()); else SENT.add(p.getUUID());
            PacketDistributor.sendToPlayer(p, new DomeMarkersPayload(List.copyOf(l)));
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        CONSOLES.clear();
        SENT.clear();
        CounterBatteryRadarBlockEntity.clearStatic();
    }
}
