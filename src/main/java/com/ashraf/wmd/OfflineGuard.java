package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class OfflineGuard extends SavedData {

    private static final String NAME = "wmd_offline_bases";

    private record Base(UUID owner, String dim, int x, int y, int z) {}

    private record Zone(String dim, int x, int z, double r2, UUID owner) {}

    private final List<Base> bases = new ArrayList<>();
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Map<UUID, Long> lastOnline = new HashMap<>();
    private final Map<UUID, int[]> blocked = new HashMap<>();

    private static volatile List<Zone> zones = List.of();
    private static final Map<UUID, Long> LOGGED = Statics.server(new HashMap<>());

    static void clear() { zones = List.of(); }

    private static OfflineGuard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OfflineGuard::new, OfflineGuard::load, null), NAME);
    }

    static boolean protects(Level level, BlockPos p) {
        List<Zone> z = zones;
        if (z.isEmpty()) return false;
        String dim = level.dimension().location().toString();
        for (Zone q : z) {
            if (!q.dim.equals(dim)) continue;
            double dx = p.getX() - q.x, dz = p.getZ() - q.z;
            if (dx * dx + dz * dz <= q.r2) { noteBlocked(level, q); return true; }
        }
        return false;
    }

    static Component refuse(ServerLevel level, double x, double z) {
        List<Zone> zs = zones;
        String dim = level.dimension().location().toString();
        for (Zone q : zs) {
            if (!q.dim.equals(dim)) continue;
            double dx = x - q.x, dz = z - q.z;
            if (dx * dx + dz * dz <= q.r2) return Component.translatable("message.wmd.offline_protected");
        }
        return null;
    }

    private static void noteBlocked(Level level, Zone q) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        Long last = LOGGED.get(q.owner);
        if (last != null && now - last < Balance.OFFLINE_LOG_TICKS) return;
        LOGGED.put(q.owner, now);
        OfflineGuard g = get(sl.getServer());
        int[] b = g.blocked.computeIfAbsent(q.owner, k -> new int[3]);
        b[0]++; b[1] = q.x; b[2] = q.z;
        g.setDirty();
        CcBattleLog.add(sl.getServer(), q.owner, CcBattleLog.HIT, "log.wmd.offline_blocked", Integer.toString(q.x), Integer.toString(q.z));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        if (!Config.Server.offlineProtection()) { zones = List.of(); return; }
        OfflineGuard g = get(server);
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) g.lastOnline.put(p.getUUID(), now);
        if (server.getTickCount() % 100 == 0) g.rememberBases(server);
        g.rebuild(server, now);
    }

    private void rememberBases(MinecraftServer server) {
        boolean changed = false;
        for (CcBuildingBlockEntity b : CommandCenter.buildings()) {
            UUID owner = b.getOwner();
            if (owner == null || b.getLevel() == null) continue;
            String dim = b.getLevel().dimension().location().toString();
            BlockPos p = b.getBlockPos();
            boolean known = false;
            for (Base x : bases) if (x.owner.equals(owner) && x.dim.equals(dim) && x.x == p.getX() && x.y == p.getY() && x.z == p.getZ()) { known = true; break; }
            if (!known) { bases.add(new Base(owner, dim, p.getX(), p.getY(), p.getZ())); changed = true; }
            Set<UUID> t = new HashSet<>(CommandCenter.trustedBy(owner));
            if (!t.equals(trusted.get(owner))) { trusted.put(owner, t); changed = true; }
        }
        for (int i = bases.size() - 1; i >= 0; i--) {
            Base x = bases.get(i);
            ServerLevel lv = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(x.dim)) lv = l;
            BlockPos p = new BlockPos(x.x, x.y, x.z);
            if (lv == null || (lv.isLoaded(p) && !(lv.getBlockEntity(p) instanceof CcBuildingBlockEntity))) { bases.remove(i); changed = true; }
        }
        if (changed) setDirty();
    }

    private void rebuild(MinecraftServer server, long now) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) online.add(p.getUUID());
        long grace = Config.Server.offlineGraceMinutes() * 60_000L;
        double r = Config.Server.offlineRadius(), r2 = r * r;
        List<Zone> out = new ArrayList<>();
        for (Base b : bases) {
            Set<UUID> team = new HashSet<>(trusted.getOrDefault(b.owner, Set.of()));
            team.add(b.owner);
            boolean anyOn = false;
            long last = 0L;
            for (UUID m : team) { if (online.contains(m)) { anyOn = true; break; } last = Math.max(last, lastOnline.getOrDefault(m, 0L)); }
            if (!anyOn && now - last >= grace) out.add(new Zone(b.dim, b.x, b.z, r2, b.owner));
        }
        zones = List.copyOf(out);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) return;
        OfflineGuard g = get(sp.getServer());
        g.lastOnline.put(sp.getUUID(), System.currentTimeMillis());
        int[] b = g.blocked.remove(sp.getUUID());
        g.setDirty();
        if (b != null && b[0] > 0)
            sp.sendSystemMessage(Component.translatable("message.wmd.offline_report", b[0], b[1], b[2]));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) return;
        OfflineGuard g = get(sp.getServer());
        g.lastOnline.put(sp.getUUID(), System.currentTimeMillis());
        g.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag bl = new ListTag();
        for (Base b : bases) {
            CompoundTag t = new CompoundTag();
            t.putUUID("o", b.owner); t.putString("d", b.dim); t.putInt("x", b.x); t.putInt("y", b.y); t.putInt("z", b.z);
            bl.add(t);
        }
        tag.put("bases", bl);
        ListTag tl = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> e : trusted.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("o", e.getKey());
            ListTag m = new ListTag();
            for (UUID u : e.getValue()) { CompoundTag c = new CompoundTag(); c.putUUID("u", u); m.add(c); }
            t.put("m", m);
            tl.add(t);
        }
        tag.put("trusted", tl);
        ListTag ol = new ListTag();
        for (Map.Entry<UUID, Long> e : lastOnline.entrySet()) { CompoundTag t = new CompoundTag(); t.putUUID("u", e.getKey()); t.putLong("t", e.getValue()); ol.add(t); }
        tag.put("online", ol);
        ListTag xl = new ListTag();
        for (Map.Entry<UUID, int[]> e : blocked.entrySet()) { CompoundTag t = new CompoundTag(); t.putUUID("u", e.getKey()); t.putIntArray("b", e.getValue()); xl.add(t); }
        tag.put("blocked", xl);
        return tag;
    }

    private static OfflineGuard load(CompoundTag tag, HolderLookup.Provider registries) {
        OfflineGuard g = new OfflineGuard();
        for (Tag x : tag.getList("bases", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            g.bases.add(new Base(t.getUUID("o"), t.getString("d"), t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }
        for (Tag x : tag.getList("trusted", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            Set<UUID> m = new HashSet<>();
            for (Tag y : t.getList("m", Tag.TAG_COMPOUND)) m.add(((CompoundTag) y).getUUID("u"));
            g.trusted.put(t.getUUID("o"), m);
        }
        for (Tag x : tag.getList("online", Tag.TAG_COMPOUND)) { CompoundTag t = (CompoundTag) x; g.lastOnline.put(t.getUUID("u"), t.getLong("t")); }
        for (Tag x : tag.getList("blocked", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            int[] b = t.getIntArray("b");
            if (b.length == 3) g.blocked.put(t.getUUID("u"), b);
        }
        return g;
    }
}
