package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class MapFeed {

    private MapFeed() {}

    public record Hit(String dim, String type, int tier, boolean refined, double x, double y, double z, double r, long realTime, String owner) {}

    public record Zone(String key, String dim, int kind, double x, double y, double z, double r, int secondsLeft) {}

    public record Border(String dim, double cx, double cz, double size) {}

    public record Machine(String dim, double x, double y, double z, int kind, String type, String owner, boolean decoy, boolean camo) {}

    public record FlightLine(String dim, long id, int tier, String warhead, String owner, double[] done, double[] left, double cx, double cy, double cz,
                             boolean targetKnown, int secondsLeft, boolean cruise) {}

    public record Ship(String dim, String id, String name, double x, double y, double z, int blocks, double size) {}

    public record Track(String dim, String id, int kind, String label, double x, double y, double z) {}
    public static final int T_AIRCRAFT = 0, T_AAM = 1, T_TORPEDO = 2, T_SHIP = 3;

    public record Snapshot(boolean history, boolean zones, boolean border, boolean admin, boolean war, boolean showOwners,
                           List<Hit> hits, List<Zone> zoneList, List<Border> borders, List<Machine> machines, List<FlightLine> flights, List<Ship> ships,
                           List<Track> tracks) {}

    private static final Map<ResourceKey<Level>, LongOpenHashSet> LOADED = new HashMap<>();
    private static final int SWEEP_PER_TICK = 160;
    private static Sweep sweep;
    private static long lastSweepEnd, lastSync;
    private static int sweepLevel;

    private static final class Sweep {
        final ServerLevel level;
        final String dim;
        final long[] chunks;
        final LongOpenHashSet snapshot;
        final Map<String, MapData.Machine> found = new HashMap<>();
        int idx;

        Sweep(ServerLevel level, long[] chunks) {
            this.level = level;
            this.dim = level.dimension().location().toString();
            this.chunks = chunks;
            this.snapshot = new LongOpenHashSet(chunks);
        }
    }

    static void clearStatics() { LOADED.clear(); sweep = null; lastSweepEnd = 0L; lastSync = 0L; sweepLevel = 0; }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        MapBridge.register();
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel l) || !MapBridge.tracking()) return;
        LOADED.computeIfAbsent(l.dimension(), k -> new LongOpenHashSet()).add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        LongOpenHashSet set = LOADED.get(l.dimension());
        if (set != null) set.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (!MapBridge.tracking()) return;
        MinecraftServer server = event.getServer();
        long t = server.getTickCount();
        boolean machines = Config.Server.blueMapAdminLayer() || Config.Server.blueMapWarLayer();
        if (machines) sweepStep(server, t); else sweep = null;
        int interval = Math.max(20, Config.Server.blueMapUpdateSeconds() * 20);
        if (t - lastSync < interval) return;
        lastSync = t;
        if (!MapBridge.connected()) return;
        MapBridge.sync(server, snapshot(server));
    }

    static void hit(ServerLevel level, String type, int tier, boolean refined, double x, double y, double z, double r, UUID owner) {
        if (!MapBridge.tracking() || !Config.Server.blueMapHistory() || r < 1.0) return;
        MinecraftServer server = level.getServer();
        if (owner != null && owner.getMostSignificantBits() == 0L && owner.getLeastSignificantBits() == 0L) owner = null;
        String name = owner == null ? null : nameOf(server, owner);
        MapData.get(server).addHit(new MapData.Hit(level.dimension().location().toString(), type, tier, refined, x, y, z, r, System.currentTimeMillis(), owner, name),
                Config.Server.blueMapHistorySize());
    }

    static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        return server.getProfileCache() == null ? null : server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }

    private static void sweepStep(MinecraftServer server, long t) {
        if (sweep == null) {
            if (t - lastSweepEnd < Math.max(20, Config.Server.blueMapUpdateSeconds() * 20)) return;
            List<ServerLevel> levels = new ArrayList<>();
            for (ServerLevel l : server.getAllLevels()) if (LOADED.containsKey(l.dimension()) && !LOADED.get(l.dimension()).isEmpty()) levels.add(l);
            if (levels.isEmpty()) return;
            ServerLevel l = levels.get(Math.floorMod(sweepLevel++, levels.size()));
            sweep = new Sweep(l, LOADED.get(l.dimension()).toLongArray());
        }
        Sweep s = sweep;
        for (int n = 0; n < SWEEP_PER_TICK && s.idx < s.chunks.length; n++) {
            long c = s.chunks[s.idx++];
            LevelChunk ch = s.level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (ch != null) scanChunk(s, ch);
        }
        if (s.idx >= s.chunks.length) {
            finishSweep(server, s);
            sweep = null;
            lastSweepEnd = t;
        }
    }

    private static void scanChunk(Sweep s, LevelChunk ch) {
        for (BlockEntity be : ch.getBlockEntities().values()) {
            if (be.isRemoved() || !(be instanceof Owned o) || o.getOwner() == null) continue;
            BlockPos p = be.getBlockPos();
            if (SableBridge.isOnShip(s.level, p)) continue;
            String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()) == null ? "machine" : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getPath();
            MapData.Machine m = new MapData.Machine(s.dim, p.getX(), p.getY(), p.getZ(), Recon.kind(be), type, o.getOwner(), o.getOwnerName(),
                    Camo.hiddenFromRecon(be), be instanceof DecoyBlockEntity);
            s.found.put(m.key(), m);
        }
    }

    private static void finishSweep(MinecraftServer server, Sweep s) {
        MapData d = MapData.get(server);
        boolean changed = false;
        LongOpenHashSet nowLoaded = LOADED.get(s.level.dimension());
        List<String> gone = new ArrayList<>();
        for (MapData.Machine m : d.machines().values()) {
            if (!m.dim().equals(s.dim)) continue;
            long ck = ChunkPos.asLong(m.x() >> 4, m.z() >> 4);
            if (s.snapshot.contains(ck) && nowLoaded != null && nowLoaded.contains(ck) && !s.found.containsKey(m.key())) gone.add(m.key());
        }
        for (String k : gone) { d.machines().remove(k); changed = true; }
        for (Map.Entry<String, MapData.Machine> e : s.found.entrySet()) {
            MapData.Machine old = d.machines().put(e.getKey(), e.getValue());
            if (!e.getValue().equals(old)) changed = true;
        }
        if (changed) d.setDirty();
    }

    static Snapshot snapshot(MinecraftServer server) {
        boolean hist = Config.Server.blueMapHistory(), zones = Config.Server.blueMapZones(), border = Config.Server.blueMapBorder();
        boolean admin = Config.Server.blueMapAdminLayer(), war = Config.Server.blueMapWarLayer(), owners = Config.Server.blueMapShowOwners();
        MapData d = MapData.get(server);
        List<Hit> hits = new ArrayList<>();
        if (hist) for (MapData.Hit h : d.history())
            hits.add(new Hit(h.dim(), h.type(), h.tier(), h.refined(), h.x(), h.y(), h.z(), h.r(), h.realTime(), owners ? h.ownerName() : null));
        List<Zone> zl = new ArrayList<>();
        List<Border> bl = new ArrayList<>();
        List<FlightLine> fl = new ArrayList<>();
        List<Ship> sl = new ArrayList<>();
        List<Track> tl = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            String dim = l.dimension().location().toString();
            long now = l.getGameTime();
            FlightManager fm = null;
            if (zones || admin || war) fm = FlightManager.get(l);
            if (zones && fm != null) for (WarheadFx.Task k : fm.tasks) {
                int kind = k.kind;
                if (!zoneKind(kind) || k.ticks <= 0) continue;
                double r = kind == WarheadFx.BOMBLETS ? Missiles.craterRadius(Math.max(1, k.tier)) * 1.5 : k.r;
                if (r < 2.0) continue;
                zl.add(new Zone(kind + ":" + dim + ":" + (int) k.x + ":" + (int) k.z + ":" + k.total, dim, kind, k.x, k.y, k.z, r, k.ticks / 20));
            }
            if (border) {
                WorldBorder wb = l.getWorldBorder();
                if (wb.getSize() < 5.9E7) bl.add(new Border(dim, wb.getCenterX(), wb.getCenterZ(), wb.getSize()));
            }
            if ((admin || war) && fm != null) for (Flight f : fm.flights()) {
                MissileBuild b = f.build();
                if (b == null || b.isInterceptor() || Satellites.isSatellite(b)) continue;
                Vec3 c = f.position(now);
                if (c == null) continue;
                UUID ow = null;
                try { ow = f.ownerId() == null || f.ownerId().isEmpty() ? null : UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) { }
                String name = ow == null ? (f.ownerId() == null ? "" : f.ownerId()) : nameOf(server, ow);
                int left = (int) Math.max(0L, (f.start() + f.duration() - now) / 20L);
                double t0 = f.start(), t1 = f.start() + f.duration();
                fl.add(new FlightLine(dim, f.id(), f.tier(), b.warheadType(), name == null ? "?" : name, sample(f, t0, now, 12),
                        f.targetKnown() ? sample(f, now, t1, 12) : new double[0], c.x, c.y, c.z, f.targetKnown(), left, f.isCruise()));
            }
            if (admin) airSea(l, dim, tl);
            if (war || admin) for (SableBridge.ShipInfo si : SableBridge.allShips(l)) {
                if (!si.isLarge()) continue;
                sl.add(new Ship(dim, si.id().toString(), si.name(), si.worldCenter().x, si.worldCenter().y, si.worldCenter().z, si.blockCount(), si.maxSize()));
            }
        }
        List<Machine> ml = new ArrayList<>();
        if (admin || war) for (MapData.Machine m : d.machines().values())
            ml.add(new Machine(m.dim(), m.x() + 0.5, m.y() + 0.5, m.z() + 0.5, m.kind(), m.type(), m.ownerName() == null || m.ownerName().isEmpty() ? m.owner().toString().substring(0, 8) : m.ownerName(),
                    m.decoy(), m.camo()));
        return new Snapshot(hist, zones, border, admin, war, owners, hits, zl, bl, ml, fl, sl, tl);
    }

    private static void airSea(ServerLevel l, String dim, List<Track> out) {
        if (AirGate.on()) {
            for (AirPicture.Aircraft a : AirPicture.all(l))
                out.add(new Track(dim, "a" + a.id(), T_AIRCRAFT, "Aircraft " + (a.name() == null || a.name().isEmpty() ? "" : a.name() + " ") + "(" + a.blocks() + " blocks, "
                        + Math.round(a.speedBps()) + " b/s)", a.pos().x, a.pos().y, a.pos().z));
            for (AirMissiles.Shot s : AirMissiles.inFlight(l))
                if (s.pos != null) out.add(new Track(dim, "m" + s.id, T_AAM, "Air-to-air missile (" + AirMissiles.NAMES[s.kind] + ")", s.pos.x, s.pos.y, s.pos.z));
        }
        for (net.minecraft.world.entity.Entity e : l.getAllEntities())
            if (e instanceof TorpedoEntity t) out.add(new Track(dim, "t" + t.getUUID(), T_TORPEDO, "Torpedo", t.getX(), t.getY(), t.getZ()));
        if (!SableBridge.active()) return;
        for (SableBridge.ShipInfo si : SableBridge.allShips(l)) {
            Vec3 c = si.worldCenter();
            if (c == null || c.y > l.getSeaLevel() + 12 || !NavalLayer.visibleAboveWater(l, si) || (AirGate.on() && AirPicture.isAircraft(l, si.id()))) continue;
            int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (int) Math.floor(c.x), (int) Math.floor(c.z));
            if (!l.getFluidState(BlockPos.containing(c.x, top - 1, c.z)).is(net.minecraft.tags.FluidTags.WATER)) continue;
            out.add(new Track(dim, "s" + si.id(), T_SHIP, "Ship at sea " + (si.name() == null || si.name().isEmpty() || si.name().startsWith("(") ? "" : si.name() + " ")
                    + "(" + si.blockCount() + " blocks)", c.x, c.y, c.z));
            if (out.size() > 512) return;
        }
    }

    private static double[] sample(Flight f, double from, double to, int n) {
        double[] out = new double[(n + 1) * 3];
        for (int i = 0; i <= n; i++) {
            Vec3 p = f.position(from + (to - from) * i / n);
            if (p == null) p = Vec3.ZERO;
            out[i * 3] = p.x; out[i * 3 + 1] = p.y; out[i * 3 + 2] = p.z;
        }
        return out;
    }

    static boolean zoneKind(int k) {
        return k == WarheadFx.IGNITE || k == WarheadFx.FUEL || k == WarheadFx.BOMBLETS || k == WarheadFx.EMP || k == WarheadFx.CHAFF || k == WarheadFx.SHOCK
                || k == WarheadFx.ROD_FALL || k == WarheadFx.HEMP_FIELD || k == WarheadFx.SEISMIC_QUAKE;
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("bluemap").requires(TestCmds::test)
                        .executes(c -> status(c.getSource()))
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("sync").executes(c -> sync(c.getSource())))
                        .then(Commands.literal("clear").then(Commands.argument("what", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("history", "markers"), b))
                                .executes(c -> clear(c.getSource(), StringArgumentType.getString(c, "what"))))))));
    }

    private static int status(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        StringBuilder sb = new StringBuilder("BlueMap: ");
        sb.append(MapBridge.loaded() ? "installed" : "NOT installed (WMD does nothing here)");
        if (MapBridge.loaded()) {
            sb.append(", tracking ").append(MapBridge.tracking() ? "on" : "OFF (config enableBlueMap = false, or an error: see the log)");
            sb.append(", map server ").append(MapBridge.connected() ? "connected" : "not connected");
            sb.append("\nlayers: A history ").append(Config.Server.blueMapHistory()).append(" / zones ").append(Config.Server.blueMapZones()).append(" / border ").append(Config.Server.blueMapBorder())
                    .append(", B admin ").append(Config.Server.blueMapAdminLayer()).append(", C war ").append(Config.Server.blueMapWarLayer());
            if (MapBridge.tracking()) {
                MapData d = MapData.get(server);
                int chunks = 0;
                for (LongOpenHashSet s : LOADED.values()) chunks += s.size();
                sb.append("\nremembered: ").append(d.history().size()).append(" craters, ").append(d.machines().size()).append(" machines, ").append(chunks).append(" loaded chunks tracked\nmarkers: ")
                        .append(MapBridge.status());
            }
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int sync(CommandSourceStack src) {
        if (!MapBridge.tracking()) { src.sendFailure(Component.literal("BlueMap is not installed / tracking is off.")); return 0; }
        if (!MapBridge.connected()) { src.sendFailure(Component.literal("BlueMap's map server is not up (yet).")); return 0; }
        lastSync = src.getServer().getTickCount();
        MapBridge.sync(src.getServer(), snapshot(src.getServer()));
        src.sendSuccess(() -> Component.literal("Map markers refreshed: " + MapBridge.status()), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, String what) {
        if (!MapBridge.tracking()) { src.sendFailure(Component.literal("BlueMap is not installed / tracking is off.")); return 0; }
        if (what.equals("history")) {
            MapData.get(src.getServer()).clearHistory();
            src.sendSuccess(() -> Component.literal("Impact history cleared (markers disappear at the next refresh)."), true);
            return 1;
        }
        if (what.equals("markers")) {
            MapBridge.clearAll();
            src.sendSuccess(() -> Component.literal("All WMD marker sets removed from the map (they come back at the next refresh)."), true);
            return 1;
        }
        src.sendFailure(Component.literal("clear history | markers"));
        return 0;
    }
}
