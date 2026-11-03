package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CommandCenter {

    private CommandCenter() {}

    public static final int WARNING_LINGER_TICKS = 600;
    public static final int MAX_WARNINGS = 16;

    private static final Set<CcBuildingBlockEntity> BUILDINGS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<CcModuleBlockEntity> MODULES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<InterceptorSite> SITES = Collections.newSetFromMap(new WeakHashMap<>());

    static void join(CcBuildingBlockEntity b) { BUILDINGS.add(b); }
    static void leave(CcBuildingBlockEntity b) { BUILDINGS.remove(b); }
    static void join(CcModuleBlockEntity m) { MODULES.add(m); }
    static void leave(CcModuleBlockEntity m) { MODULES.remove(m); }

    static List<CcModuleBlockEntity> modules() { return List.copyOf(MODULES); }

    static List<CcBuildingBlockEntity> buildings() { return List.copyOf(BUILDINGS); }

    public static void siteSeen(InterceptorSite s) {
        if (!SITES.contains(s)) SITES.add(s);
    }

    public static List<CcBuildingBlockEntity> activeBuildings(UUID owner) {
        List<CcBuildingBlockEntity> l = new ArrayList<>();
        if (owner == null) return l;
        for (CcBuildingBlockEntity b : BUILDINGS) if (!b.isRemoved() && owner.equals(b.getOwner()) && b.isActive()) l.add(b);
        return l;
    }

    public static java.util.Set<UUID> ownersTrusting(UUID viewer) {
        java.util.Set<UUID> s = new java.util.HashSet<>();
        if (viewer == null) return s;
        for (CcBuildingBlockEntity b : BUILDINGS) {
            if (b.isRemoved() || b.getOwner() == null || b.getOwner().equals(viewer)) continue;
            if (b.security().trusted().containsKey(viewer)) s.add(b.getOwner());
        }
        return s;
    }

    static List<CcBuildingBlockEntity> activeBuildingsIn(Level level) {
        List<CcBuildingBlockEntity> l = new ArrayList<>();
        for (CcBuildingBlockEntity b : BUILDINGS) if (!b.isRemoved() && b.getLevel() == level && b.getOwner() != null && b.isActive()) l.add(b);
        return l;
    }

    public static java.util.Set<UUID> trustedBy(UUID owner) {
        java.util.Set<UUID> s = new java.util.HashSet<>();
        if (owner == null) return s;
        for (CcBuildingBlockEntity b : BUILDINGS)
            if (!b.isRemoved() && owner.equals(b.getOwner())) s.addAll(b.security().trusted().keySet());
        return s;
    }

    public static boolean active(UUID owner) {
        if (owner == null) return false;
        boolean loaded = false;
        for (CcBuildingBlockEntity b : BUILDINGS) {
            if (b.isRemoved() || !owner.equals(b.getOwner())) continue;
            loaded = true;
            if (b.isActive()) { LAST_ACTIVE.put(owner, true); return true; }
        }

        if (loaded) { LAST_ACTIVE.put(owner, false); return false; }
        return LAST_ACTIVE.getOrDefault(owner, false);
    }

    private static final java.util.Map<UUID, Boolean> LAST_ACTIVE = Statics.server(new java.util.concurrent.ConcurrentHashMap<>());

    static void forget(UUID owner) { if (owner != null) LAST_ACTIVE.remove(owner); }

    public static boolean hasModule(UUID owner, CcModule kind) {
        if (owner == null) return false;
        for (CcBuildingBlockEntity b : BUILDINGS) if (!b.isRemoved() && owner.equals(b.getOwner()) && b.isActive() && b.online(kind)) return true;
        return false;
    }

    public static CcBuildingBlockEntity activeRoomFor(CcModuleBlockEntity m) {
        CcBuildingBlockEntity b = m.building();
        return b != null && b.isActive() && b.hosts(m) ? b : null;
    }

    static CcBuildingBlockEntity nearestBuilding(Level level, BlockPos pos, double radius) {
        CcBuildingBlockEntity best = null;
        double bd = radius * radius;
        for (CcBuildingBlockEntity b : BUILDINGS) {
            if (b.isRemoved() || b.getLevel() != level) continue;
            double d = SableBridge.distSqWithShips(level, b.hallCentre(), pos);
            if (d <= bd) { bd = d; best = b; }
        }
        return best;
    }

    static List<InterceptorSite> allSites() { return new ArrayList<>(SITES); }

    static List<InterceptorSite> sitesOf(UUID owner, int max) {
        List<InterceptorSite> l = new ArrayList<>();
        if (owner == null) return l;
        for (InterceptorSite s : SITES) {
            if (s instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.isRemoved()) continue;
            if (owner.equals(s.getOwner())) l.add(s);
            if (l.size() >= max) break;
        }
        l.sort((a, b) -> Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        return l;
    }

    public record Warning(long id, int ox, int oz, int ix, int iz, long launchGt, long impactGt, boolean sat, int what, int tier, boolean hyper, String seeker, String warhead) {
        public static final int WHAT_MISSILE = 0, WHAT_DRONE = 1, WHAT_BOMB = 2;

        public Warning(long id, int ox, int oz, int ix, int iz, long launchGt, long impactGt, boolean sat) {
            this(id, ox, oz, ix, iz, launchGt, impactGt, sat, WHAT_MISSILE, 0, false, "?", "");
        }

        public static Warning ofFlight(Flight f, long[] imp, boolean sat, boolean intel) {
            MissileBuild b = f.build();
            boolean hyper = Hypersonic.isHyper(f);
            String seeker = intel && b != null ? b.seeker() : "?", warhead = intel && b != null && b.warheadType() != null ? b.warheadType() : "";
            int tier = intel && b != null ? b.warhead() : 0;
            return new Warning(f.id(), (int) Math.round(f.ox()), (int) Math.round(f.oz()), (int) imp[0], (int) imp[1], f.start(), f.endTime(), sat, WHAT_MISSILE, tier, hyper, seeker, warhead);
        }

        public boolean intel() { return !warhead.isEmpty(); }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id); t.putInt("ox", ox); t.putInt("oz", oz); t.putInt("ix", ix); t.putInt("iz", iz);
            t.putLong("l", launchGt); t.putLong("i", impactGt); t.putBoolean("s", sat);
            if (what != 0) t.putInt("w", what);
            if (tier != 0) t.putInt("t", tier);
            if (hyper) t.putBoolean("h", true);
            if (!"?".equals(seeker)) t.putString("sk", seeker);
            if (!warhead.isEmpty()) t.putString("wh", warhead);
            return t;
        }
        public static Warning load(CompoundTag t) {
            return new Warning(t.getLong("id"), t.getInt("ox"), t.getInt("oz"), t.getInt("ix"), t.getInt("iz"), t.getLong("l"), t.getLong("i"), t.getBoolean("s"),
                    t.getInt("w"), t.getInt("t"), t.getBoolean("h"), t.contains("sk") ? t.getString("sk") : "?", t.getString("wh"));
        }
    }

    private static final Map<UUID, List<Warning>> WARNINGS = new HashMap<>();

    public static List<Warning> warnings(UUID owner) {
        List<Warning> l = owner == null ? null : WARNINGS.get(owner);
        return l == null ? List.of() : List.copyOf(l);
    }

    public static void removeWarnings(java.util.function.BiPredicate<UUID, Warning> gone) {
        for (Map.Entry<UUID, List<Warning>> e : WARNINGS.entrySet())
            if (e.getValue().removeIf(w -> gone.test(e.getKey(), w))) pushBoards(e.getKey());
    }

    public static void addWarning(MinecraftServer server, UUID owner, Warning w) {
        List<Warning> l = WARNINGS.computeIfAbsent(owner, k -> new ArrayList<>());
        l.removeIf(o -> o.id() == w.id());
        l.add(w);
        while (l.size() > MAX_WARNINGS) l.remove(0);
        pushBoards(owner);
        CcBattleLog.onWarning(server, owner, w);
        CcDefcon.onWarning(server, owner, w);

        MusicTriggersHook.fire(server.overworld(), AirshipWatch.isWarning(w.id()) ? "wmd:incoming_airship" : "wmd:incoming",
                new BlockPos(w.ix(), 64, w.iz()));
    }

    public static void refreshWarning(UUID owner, Warning w) {
        if (owner == null || w == null) return;
        List<Warning> l = WARNINGS.computeIfAbsent(owner, k -> new ArrayList<>());
        boolean found = false;
        for (int i = 0; i < l.size() && !found; i++) if (l.get(i).id() == w.id()) { l.set(i, w); found = true; }
        if (!found) {
            l.add(w);
            while (l.size() > MAX_WARNINGS) l.remove(0);
        }
        pushBoards(owner);
    }

    private static void pushBoards(UUID owner) {
        List<Warning> l = warnings(owner);
        for (CcModuleBlockEntity m : MODULES)
            if (!m.isRemoved() && m instanceof CcWarningBoardBlockEntity b && owner.equals(b.getOwner())) b.setWarnings(l);
    }

    public static ListTag saveList(List<Warning> l) {
        ListTag t = new ListTag();
        for (Warning w : l) t.add(w.save());
        return t;
    }

    public static List<Warning> loadList(ListTag t) {
        List<Warning> l = new ArrayList<>();
        for (Tag x : t) if (x instanceof CompoundTag c) l.add(Warning.load(c));
        return l;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 13 || WARNINGS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, List<Warning>>> it = WARNINGS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Warning>> e = it.next();
            if (e.getValue().removeIf(w -> now > w.impactGt() + WARNING_LINGER_TICKS)) pushBoards(e.getKey());
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        WARNINGS.clear();
        BUILDINGS.clear();
        MODULES.clear();
        SITES.clear();
    }
}
