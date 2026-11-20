package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Cloak {

    private Cloak() {}

    private record Source(BlockPos pos, UUID ship, Owned machine, Vec3 centre, long until) {}

    public record Hide(Owned machine, UUID ship) {}

    public record Ghost(UUID id, boolean player, Vec3 pos, Vec3 vel, int blocks, String name, Owned machine) {}

    private static final Map<ResourceKey<Level>, Map<Long, Source>> SOURCES = Statics.server(new HashMap<>());
    private static final Map<ResourceKey<Level>, Map<UUID, Hide>> SHIPS = Statics.server(new HashMap<>());
    private static final Map<ResourceKey<Level>, Map<UUID, Hide>> PLAYERS = Statics.server(new HashMap<>());

    private static final Map<ResourceKey<Level>, Map<UUID, Map<UUID, Long>>> REVEAL = Statics.server(new HashMap<>());

    private static final Map<UUID, List<Set<UUID>>> SENT = Statics.server(new HashMap<>());

    private record Personal(Owned owner, long until) {}
    private static final Map<ResourceKey<Level>, Map<UUID, Personal>> PERSONAL = Statics.server(new HashMap<>());

    public static void personal(ServerPlayer p, Owned owner, long now) {
        PERSONAL.computeIfAbsent(p.serverLevel().dimension(), k -> new HashMap<>()).put(p.getUUID(), new Personal(owner, now + Balance.CLOAK_HOLD_TICKS));
    }

    public static void engaged(ServerLevel sl, BlockPos pos, UUID ship, Owned machine, Vec3 centre, long now) {
        if (!AirGate.on() || sl == null || pos == null || machine == null) return;
        Map<Long, Source> m = SOURCES.computeIfAbsent(sl.dimension(), k -> new HashMap<>());
        if (m.size() > 1024 && !m.containsKey(pos.asLong())) return;
        m.put(pos.asLong(), new Source(pos.immutable(), ship, machine, centre, now + Balance.CLOAK_HOLD_TICKS));
    }

    public static void dropped(ServerLevel sl, BlockPos pos) {
        if (sl == null || pos == null) return;
        Map<Long, Source> m = SOURCES.get(sl.dimension());
        if (m != null) m.remove(pos.asLong());
    }

    public static boolean hidden(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null || !AirGate.on()) return false;
        Map<UUID, Hide> m = SHIPS.get(sl.dimension());
        return m != null && m.containsKey(ship);
    }

    public static boolean hidden(Level level, UUID ship) { return level instanceof ServerLevel sl && hidden(sl, ship); }

    public static boolean playerHidden(ServerLevel sl, Entity e) {
        if (sl == null || e == null || !(e instanceof Player) || !AirGate.on()) return false;
        Map<UUID, Hide> m = PLAYERS.get(sl.dimension());
        return m != null && m.containsKey(e.getUUID());
    }

    public static boolean revealedTo(ServerLevel sl, UUID target, UUID owner) {
        if (sl == null || target == null || owner == null) return false;
        Map<UUID, Map<UUID, Long>> m = REVEAL.get(sl.dimension());
        Map<UUID, Long> r = m == null ? null : m.get(target);
        Long until = r == null ? null : r.get(owner);
        return until != null && until >= sl.getGameTime();
    }

    public static boolean shieldsFrom(ServerLevel sl, Entity e, Owned o) {
        return playerHidden(sl, e) && !(o != null && revealedTo(sl, e.getUUID(), o.getOwner()));
    }

    public static void reveal(ServerLevel sl, UUID target, UUID owner, long now) {
        if (sl == null || target == null || owner == null) return;
        Map<UUID, Map<UUID, Long>> m = REVEAL.computeIfAbsent(sl.dimension(), k -> new HashMap<>());
        if (m.size() > 1024 && !m.containsKey(target)) return;
        m.computeIfAbsent(target, k -> new HashMap<>()).put(owner, now + Balance.QR_REVEAL_TICKS);
    }

    public static List<Ghost> ghosts(ServerLevel sl) {
        List<Ghost> out = new ArrayList<>();
        if (sl == null || !AirGate.on()) return out;
        Map<UUID, Hide> ships = SHIPS.get(sl.dimension());
        if (ships != null) for (Map.Entry<UUID, Hide> e : ships.entrySet()) {
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, e.getKey());
            if (si != null) out.add(new Ghost(si.id(), false, si.worldCenter(), si.linearVelocity().scale(0.05), si.blockCount(), si.name(), e.getValue().machine()));
        }
        Map<UUID, Hide> players = PLAYERS.get(sl.dimension());
        if (players != null) for (Map.Entry<UUID, Hide> e : players.entrySet()) {
            if (!(sl.getPlayerByUUID(e.getKey()) instanceof ServerPlayer sp)) continue;
            out.add(new Ghost(sp.getUUID(), true, sp.position().add(0.0, 0.9, 0.0), sp.getDeltaMovement(), 1, sp.getGameProfile().getName(), e.getValue().machine()));
        }
        return out;
    }

    public static int count(ServerLevel sl) {
        Map<UUID, Hide> a = SHIPS.get(sl.dimension()), b = PLAYERS.get(sl.dimension());
        return (a == null ? 0 : a.size()) + (b == null ? 0 : b.size());
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        Map<Long, Source> src = SOURCES.get(sl.dimension());
        if (src != null) src.values().removeIf(s -> s.until() < now || (s.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.isRemoved()));
        boolean any = src != null && !src.isEmpty() && AirGate.on();
        Map<UUID, Hide> ships = new HashMap<>(), players = new HashMap<>();
        if (any) rebuild(sl, src, ships, players);
        Map<UUID, Personal> pers = PERSONAL.get(sl.dimension());
        if (pers != null) {
            pers.values().removeIf(q -> q.until() < now);
            if (pers.isEmpty()) PERSONAL.remove(sl.dimension());
            else if (AirGate.on()) for (Map.Entry<UUID, Personal> e : pers.entrySet()) players.putIfAbsent(e.getKey(), new Hide(e.getValue().owner(), null));
        }
        if (ships.isEmpty()) SHIPS.remove(sl.dimension()); else SHIPS.put(sl.dimension(), ships);
        if (players.isEmpty()) PLAYERS.remove(sl.dimension()); else PLAYERS.put(sl.dimension(), players);
        Map<UUID, Map<UUID, Long>> rv = REVEAL.get(sl.dimension());
        if (rv != null && now % 20 == 0) {
            for (Iterator<Map<UUID, Long>> it = rv.values().iterator(); it.hasNext(); ) { Map<UUID, Long> r = it.next(); r.values().removeIf(t -> t < now); if (r.isEmpty()) it.remove(); }
            if (rv.isEmpty()) REVEAL.remove(sl.dimension());
        }
        if (now % Balance.CLOAK_SYNC_TICKS == 0) sync(sl, ships, players);
    }

    private static void rebuild(ServerLevel sl, Map<Long, Source> src, Map<UUID, Hide> ships, Map<UUID, Hide> players) {
        Map<UUID, Integer> cover = new HashMap<>();
        Map<UUID, Source> first = new HashMap<>();
        List<Source> ground = new ArrayList<>();
        for (Source s : src.values()) {
            if (s.ship() == null) { ground.add(s); continue; }
            cover.merge(s.ship(), Balance.CLOAK_COVER_BLOCKS, Integer::sum);
            first.putIfAbsent(s.ship(), s);
        }
        for (Map.Entry<UUID, Integer> e : cover.entrySet()) {
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, e.getKey());
            if (si != null && si.blockCount() <= e.getValue()) ships.put(si.id(), new Hide(first.get(e.getKey()).machine(), si.id()));
        }
        double r = Balance.CLOAK_FIELD_R, r2 = r * r;
        for (Source g : ground) {
            for (SableBridge.ShipInfo si : SableBridge.shipsIntersecting(sl, new AABB(g.centre(), g.centre()).inflate(r))) {
                if (si.worldCenter().distanceToSqr(g.centre()) <= r2) ships.putIfAbsent(si.id(), new Hide(g.machine(), null));
            }
        }
        for (ServerPlayer sp : sl.players()) {
            if (sp.isSpectator()) continue;
            UUID under = SableBridge.shipUnder(sp);
            Hide h = under == null ? null : ships.get(under);
            if (h == null) for (Map.Entry<UUID, Hide> e : ships.entrySet()) {
                SableBridge.ShipInfo si = SableBridge.shipInfo(sl, e.getKey());
                if (si != null && si.worldBounds().inflate(Balance.CLOAK_CRAFT_PAD).contains(sp.position())) { h = e.getValue(); break; }
            }
            if (h == null) for (Source g : ground) if (sp.position().distanceToSqr(g.centre()) <= r2) { h = new Hide(g.machine(), null); break; }
            if (h != null) players.put(sp.getUUID(), h);
        }
    }

    static boolean seesThrough(ServerLevel sl, Hide h, ServerPlayer viewer) {
        if (h.machine() == null) return false;
        if (DefenceTargets.trusted(sl, h.machine(), viewer)) return true;
        return h.ship() != null && h.ship().equals(SableBridge.shipUnder(viewer));
    }

    private static void sync(ServerLevel sl, Map<UUID, Hide> ships, Map<UUID, Hide> players) {
        for (ServerPlayer viewer : sl.players()) {
            Set<UUID> s = new HashSet<>(), p = new HashSet<>();
            for (Map.Entry<UUID, Hide> e : ships.entrySet()) if (!seesThrough(sl, e.getValue(), viewer)) s.add(e.getKey());
            for (Map.Entry<UUID, Hide> e : players.entrySet()) if (!e.getKey().equals(viewer.getUUID()) && !seesThrough(sl, e.getValue(), viewer)) p.add(e.getKey());
            List<Set<UUID>> last = SENT.get(viewer.getUUID());
            if (last != null && last.get(0).equals(s) && last.get(1).equals(p)) continue;
            if (last == null && s.isEmpty() && p.isEmpty()) continue;
            if (SENT.size() > 512) SENT.clear();
            SENT.put(viewer.getUUID(), List.of(s, p));
            PacketDistributor.sendToPlayer(viewer, new CloakPayload(new ArrayList<>(s), new ArrayList<>(p)));
        }
    }

    @SubscribeEvent
    static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onPlayerDim(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
