package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class Station {

    private Station() {}

    public static final String CORE = "core", HABITAT = "habitat", WING = "wing", SENSOR = "sensor", LASER = "laser";
    public static final String ROD = "rod";
    public static final String MIRROR = "mirror";
    public static final String BAY = "bay";
    public static final String TURRET = "turret";

    static boolean tube(String type) { return HABITAT.equals(type) || ROD.equals(type) || MIRROR.equals(type) || BAY.equals(type); }
    static int len(String type) { return ROD.equals(type) ? ROD_LEN : MIRROR.equals(type) ? MIRROR_LEN : BAY.equals(type) ? BAY_LEN : HAB_LEN; }

    static boolean closedEnd(String type) { return ROD.equals(type) || MIRROR.equals(type) || BAY.equals(type); }

    static int half(String type) { return BAY.equals(type) ? 6 : 4; }

    static boolean boom(String type) { return WING.equals(type) || SENSOR.equals(type) || LASER.equals(type); }

    static int[] boomBox(String type) { return LASER.equals(type) ? new int[] { 0, 10, -1, 5, -3, 3 } : new int[] { 0, 4, 1, 3, -1, 1 }; }

    static void offerCamera(net.minecraft.server.level.ServerPlayer p, Module m) {
        if (!p.level().dimension().equals(Orbit.ORBIT)) return;
        BlockPos c = controller(m);
        String cmd = String.format(java.util.Locale.ROOT, "/wmdcam %d %d %d 0", c.getX(), c.getY(), c.getZ());
        p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.wmd.station.camera_offer").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.translatable("message.wmd.station.camera_hover")))));
    }

    static BlockPos middle(Module m) {
        if (TURRET.equals(m.type())) return world(m, 0, 2, 0);
        if (boom(m.type())) { int[] b = boomBox(m.type()); return world(m, (b[0] + b[1]) / 2, (b[2] + b[3]) / 2, 0); }
        return tube(m.type()) ? world(m, len(m.type()) / 2, half(m.type()), 0) : world(m, 0, 4, 0);
    }
    static final Direction[] PORT_DIR = { Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH };

    static final int SIDE0 = 10, DOCK = 13;

    static final int NADIR = 14;
    static final int[] SIDE_X = { 4, 11, 4, 11 }, SIDE_Z = { 1, 1, -1, -1 };

    static int[] spots(String type) {
        return switch (type) {
            case HABITAT -> new int[] { 0 };
            case BAY -> new int[] { 2 };
            case WING -> new int[] { 3, 1 };
            case ROD -> new int[] { 4 };
            case LASER -> new int[] { SIDE0 };
            case SENSOR -> new int[] { SIDE0 + 1 };
            case MIRROR -> new int[] { SIDE0 + 2 };
            case TURRET -> new int[] { NADIR };
            default -> new int[0];
        };
    }

    static Direction spotDir(int p) {
        if (p == NADIR) return Direction.EAST;
        if (p >= 0 && p < 4) return PORT_DIR[p];
        if (p >= 4 && p < 8) return PORT_DIR[p - 4];
        return p == SIDE0 || p == SIDE0 + 1 ? Direction.SOUTH : Direction.NORTH;
    }

    static Module habitatOn(MinecraftServer server, int slot, int k) {
        Module core = core(server, slot);
        if (core == null) return null;
        for (Module o : modules(server, slot)) if (o.host() == core.id() && o.port() == k && HABITAT.equals(o.type())) return o;
        return null;
    }

    static int spotState(MinecraftServer server, int slot, int p) {
        Module core = core(server, slot);
        if (core == null) return 2;
        if (p == NADIR) {
            for (Module o : modules(server, slot)) if (o.host() == core.id() && o.port() == NADIR) return 1;
            return 0;
        }
        if (p < 8) {
            if (p >= 4) { Module h = habitatOn(server, slot, p - 4); if (h == null || !h.built()) return 2; }
            return freePort(server, slot, p) == p ? 0 : 1;
        }
        Module hab = habitatOn(server, slot, 0);
        if (hab == null || !hab.built()) return 2;
        int hp = 2 + (p - SIDE0);
        for (Module o : modules(server, slot)) if (o.host() == hab.id() && o.port() == hp) return 1;
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (p == DOCK && Cargo.podAt(orbit, slot, DOCK)) return 1;
        return 0;
    }

    static Module spotFrame(MinecraftServer server, int slot, int p, String type) {
        Module core = core(server, slot);
        return core == null ? null : onPort(server, slot, core, -1, type, p, 0L);
    }

    static String spotWhy(MinecraftServer server, int slot, String type) {
        int[] sp = spots(type);
        if (sp.length == 0) return "it does not dock on a port";
        boolean host = false;
        for (int p : sp) { int st = spotState(server, slot, p); if (st == 0) return null; if (st == 1) host = true; }
        return host ? "its spot is taken" : "dock the habitat first (it carries this module)";
    }
    static final int HAB_LEN = 15, ROD_LEN = 21, MIRROR_LEN = 11, BAY_LEN = 25;

    record Module(int id, String type, BlockPos origin, Direction facing, int host, int port, long arrive) {
        boolean built() { return arrive == 0L; }
        Module latched() { return new Module(id, type, origin, facing, host, port, 0L); }
    }

    static final class Data extends SavedData {
        static final String NAME = "wmd_stations";
        final Map<Integer, List<Module>> slots = new HashMap<>();
        final Map<Integer, Long> energy = new HashMap<>();
        final Map<Integer, Float> air = new HashMap<>();
        final Map<Integer, Integer> nextPort = new HashMap<>();
        final Map<Integer, Integer> health = new HashMap<>();
        final Map<Integer, long[]> reboost = new HashMap<>();
        final Map<Integer, Integer> batteries = new HashMap<>();
        final Map<Integer, Integer> laserAuto = new HashMap<>();
        final Map<Integer, Integer> asatPick = new HashMap<>();
        final Map<Integer, Integer> boardPick = new HashMap<>();
        final Map<Integer, Integer> rods = new HashMap<>();
        final Map<Integer, Integer> rodArmed = new HashMap<>();
        final Map<Integer, Integer> mirrorUp = new HashMap<>();
        final Map<Integer, int[]> bay = new HashMap<>();
        final Map<Integer, long[]> bayT = new HashMap<>();
        long asteroidIn;
        long[] pass;
        int next;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        List<Module> of(int slot) { return slots.computeIfAbsent(slot, k -> new ArrayList<>()); }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag s = new CompoundTag();
            for (Map.Entry<Integer, List<Module>> e : slots.entrySet()) {
                ListTag l = new ListTag();
                for (Module m : e.getValue()) {
                    CompoundTag t = new CompoundTag();
                    t.putInt("id", m.id()); t.putString("type", m.type()); t.putLong("origin", m.origin().asLong());
                    t.putString("facing", m.facing().getSerializedName()); t.putInt("host", m.host()); t.putInt("port", m.port()); t.putLong("arrive", m.arrive());
                    l.add(t);
                }
                s.put(Integer.toString(e.getKey()), l);
            }
            tag.put("slots", s);
            tag.putInt("next", next);
            CompoundTag e = new CompoundTag();
            for (Map.Entry<Integer, Long> x : energy.entrySet()) e.putLong(Integer.toString(x.getKey()), x.getValue());
            tag.put("energy", e);
            CompoundTag a = new CompoundTag();
            for (Map.Entry<Integer, Float> x : air.entrySet()) a.putFloat(Integer.toString(x.getKey()), x.getValue());
            tag.put("air", a);
            CompoundTag np = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : nextPort.entrySet()) np.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("next_port", np);
            CompoundTag hp = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : health.entrySet()) hp.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("health", hp);
            CompoundTag rb = new CompoundTag();
            for (Map.Entry<Integer, long[]> x : reboost.entrySet()) rb.putLongArray(Integer.toString(x.getKey()), x.getValue());
            tag.put("reboost", rb);
            CompoundTag la = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : laserAuto.entrySet()) la.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("laser_auto", la);
            CompoundTag ap = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : asatPick.entrySet()) ap.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("asat_pick", ap);
            CompoundTag bp = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : boardPick.entrySet()) bp.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("board_pick", bp);
            CompoundTag rd = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : rods.entrySet()) rd.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("rods", rd);
            CompoundTag bat = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : batteries.entrySet()) bat.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("batteries", bat);
            CompoundTag ra = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : rodArmed.entrySet()) ra.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("rod_armed", ra);
            CompoundTag mu = new CompoundTag();
            for (Map.Entry<Integer, Integer> x : mirrorUp.entrySet()) mu.putInt(Integer.toString(x.getKey()), x.getValue());
            tag.put("mirror_up", mu);
            CompoundTag by = new CompoundTag();
            for (Map.Entry<Integer, int[]> x : bay.entrySet()) by.putIntArray(Integer.toString(x.getKey()), x.getValue());
            tag.put("bay", by);
            CompoundTag bt = new CompoundTag();
            for (Map.Entry<Integer, long[]> x : bayT.entrySet()) bt.putLongArray(Integer.toString(x.getKey()), x.getValue());
            tag.put("bay_t", bt);
            tag.putLong("asteroid_in", asteroidIn);
            if (pass != null) tag.putLongArray("pass", pass);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            CompoundTag s = tag.getCompound("slots");
            for (String k : s.getAllKeys()) {
                int slot;
                try { slot = Integer.parseInt(k); } catch (NumberFormatException e) { continue; }
                ListTag l = s.getList(k, Tag.TAG_COMPOUND);
                List<Module> list = d.of(slot);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag t = l.getCompound(i);
                    Direction f = Direction.byName(t.getString("facing"));
                    list.add(new Module(t.getInt("id"), t.getString("type"), BlockPos.of(t.getLong("origin")), f == null ? Direction.EAST : f, t.getInt("host"), t.getInt("port"), t.getLong("arrive")));
                }
            }
            d.next = tag.getInt("next");
            CompoundTag e = tag.getCompound("energy");
            for (String k : e.getAllKeys()) try { d.energy.put(Integer.parseInt(k), e.getLong(k)); } catch (NumberFormatException ignored) {}
            CompoundTag a = tag.getCompound("air");
            for (String k : a.getAllKeys()) try { d.air.put(Integer.parseInt(k), a.getFloat(k)); } catch (NumberFormatException ignored) {}
            CompoundTag np = tag.getCompound("next_port");
            for (String k : np.getAllKeys()) try { d.nextPort.put(Integer.parseInt(k), np.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag hp = tag.getCompound("health");
            for (String k : hp.getAllKeys()) try { d.health.put(Integer.parseInt(k), hp.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag rb = tag.getCompound("reboost");
            for (String k : rb.getAllKeys()) try { long[] v = rb.getLongArray(k); if (v.length == 6) d.reboost.put(Integer.parseInt(k), v); } catch (NumberFormatException ignored) {}
            CompoundTag la = tag.getCompound("laser_auto");
            for (String k : la.getAllKeys()) try { d.laserAuto.put(Integer.parseInt(k), la.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag ap = tag.getCompound("asat_pick");
            for (String k : ap.getAllKeys()) try { d.asatPick.put(Integer.parseInt(k), ap.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag bp = tag.getCompound("board_pick");
            for (String k : bp.getAllKeys()) try { d.boardPick.put(Integer.parseInt(k), bp.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag rd = tag.getCompound("rods");
            for (String k : rd.getAllKeys()) try { d.rods.put(Integer.parseInt(k), rd.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag bat = tag.getCompound("batteries");
            for (String k : bat.getAllKeys()) try { d.batteries.put(Integer.parseInt(k), bat.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag ra = tag.getCompound("rod_armed");
            for (String k : ra.getAllKeys()) try { d.rodArmed.put(Integer.parseInt(k), ra.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag mu = tag.getCompound("mirror_up");
            for (String k : mu.getAllKeys()) try { d.mirrorUp.put(Integer.parseInt(k), mu.getInt(k)); } catch (NumberFormatException ignored) {}
            CompoundTag by = tag.getCompound("bay");
            for (String k : by.getAllKeys()) try { d.bay.put(Integer.parseInt(k), AsteroidBay.fit(by.getIntArray(k))); } catch (NumberFormatException ignored) {}
            CompoundTag bt = tag.getCompound("bay_t");
            for (String k : bt.getAllKeys()) try { d.bayT.put(Integer.parseInt(k), AsteroidRedirect.fitT(bt.getLongArray(k))); } catch (NumberFormatException ignored) {}
            d.asteroidIn = tag.getLong("asteroid_in");
            if (tag.contains("pass")) d.pass = AsteroidRedirect.fitPass(tag.getLongArray("pass"));
            for (List<Module> l : d.slots.values()) for (Module m : l) d.next = Math.max(d.next, m.id() + 1);
            return d;
        }
    }

    public static List<Module> modules(MinecraftServer server, int slot) { return Data.get(server).of(slot); }

    static Module byId(MinecraftServer server, int id) {
        for (List<Module> l : Data.get(server).slots.values()) for (Module m : l) if (m.id() == id) return m;
        return null;
    }

    static int slotOf(MinecraftServer server, Module m) {
        for (Map.Entry<Integer, List<Module>> e : Data.get(server).slots.entrySet()) if (e.getValue().contains(m)) return e.getKey();
        return -1;
    }

    static Module core(MinecraftServer server, int slot) {
        for (Module m : modules(server, slot)) if (CORE.equals(m.type())) return m;
        return null;
    }

    private static boolean octa(int a, int b) { return Math.abs(a) <= 4 && Math.abs(b) <= 4 && Math.abs(a) + Math.abs(b) <= 6; }

    private static boolean coreSolid(int x, int y, int z) {
        if (y < 0 || y > 8 || !octa(x, z)) return false;
        return y != 8 || (Math.abs(x) + Math.abs(z) <= 4 && Math.abs(x) <= 3 && Math.abs(z) <= 3);
    }

    static BlockState coreCell(int x, int y, int z) {
        if (!coreSolid(x, y, z) || !shell(Station::coreSolid, x, y, z)) return null;
        boolean flat = Math.abs(x) == 4 || Math.abs(z) == 4;
        int h = Math.abs(x) == 4 ? z : x;
        if (y == 8) {
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) return null;
            return Math.abs(x) <= 2 && Math.abs(z) <= 2 ? Wmd.STATION_LIGHT.get().defaultBlockState() : Wmd.STATION_HULL.get().defaultBlockState();
        }
        if (y == 0) return Wmd.STATION_DECK.get().defaultBlockState();
        if (flat && Math.abs(h) <= 1 && y <= 3) return null;
        if (flat && Math.abs(h) <= 2 && y <= 4) return Wmd.STATION_BAND.get().defaultBlockState();
        if (!flat && (y == 4 || y == 5)) return Wmd.STATION_WINDOW.get().defaultBlockState();
        if (y == 6) return Wmd.STATION_HULL_SEAM.get().defaultBlockState();
        return Wmd.STATION_HULL.get().defaultBlockState();
    }

    private static boolean habSolid(int x, int y, int z) { return x >= 0 && x < HAB_LEN && y >= 0 && y <= 8 && octa(y - 4, z); }

    private static boolean rodSolid(int x, int y, int z) { return x >= 0 && x < ROD_LEN && y >= 0 && y <= 8 && octa(y - 4, z); }

    private static boolean mirrorSolid(int x, int y, int z) { return x >= 0 && x < MIRROR_LEN && y >= 0 && y <= 8 && octa(y - 4, z); }

    private static boolean baySolid(int x, int y, int z) { return x >= 0 && x < BAY_LEN && y >= 0 && y <= 12 && Math.abs(z) <= 6 && Math.abs(y - 6) <= 6 && Math.abs(y - 6) + Math.abs(z) <= 9; }

    private static Solid solid(String type) { return ROD.equals(type) ? Station::rodSolid : MIRROR.equals(type) ? Station::mirrorSolid : BAY.equals(type) ? Station::baySolid : Station::habSolid; }

    static BlockState habitatCell(int x, int y, int z) { return tubeCell(HABITAT, x, y, z); }

    static BlockState tubeCell(String type, int x, int y, int z) {
        Solid sd = solid(type);
        int n = len(type);
        boolean rod = ROD.equals(type), mirror = MIRROR.equals(type);
        if (!sd.at(x, y, z) || !shell(sd, x, y, z)) return null;
        if (BAY.equals(type)) return bayCell(x, y, z);
        if (HABITAT.equals(type) && Math.abs(z) == 4 && y >= 3 && y <= 5 && (Math.abs(x - SIDE_X[0]) <= 1 || Math.abs(x - SIDE_X[1]) <= 1)) return null;
        boolean end = x == 0 || x == n - 1;
        if (end && Math.abs(z) <= 1 && y >= 1 && y <= 3 && (!closedEnd(type) || x == 0)) return null;
        if (mirror && x == n - 1 && Math.abs(z) <= 2 && y >= 4 && y <= 6) return Wmd.STATION_WINDOW.get().defaultBlockState();
        if (end && Math.abs(z) <= 2 && y <= 4) return Wmd.STATION_BAND.get().defaultBlockState();
        if (end) return Wmd.STATION_HULL.get().defaultBlockState();
        if (rod && y == 0 && Math.abs(z) <= 1 && x >= 11 && x <= 19) return Wmd.STATION_SOLID.get().defaultBlockState();
        if (x == 2 || x == n - 3) return Wmd.STATION_BAND.get().defaultBlockState();
        if (x % 4 == 0) return Wmd.STATION_HULL_SEAM.get().defaultBlockState();
        if (Math.abs(z) == 4 && (y == 4 || y == 5) && x >= (rod ? 4 : 3) && x <= (rod ? 16 : mirror ? 7 : n - 4) && !HABITAT.equals(type)) return Wmd.STATION_WINDOW.get().defaultBlockState();
        if (y == 0 && Math.abs(z) <= 2) return Wmd.STATION_DECK.get().defaultBlockState();
        return Wmd.STATION_HULL.get().defaultBlockState();
    }

    private static BlockState bayCell(int x, int y, int z) {
        int n = BAY_LEN;
        boolean end = x == 0 || x == n - 1;
        if (x == 0 && Math.abs(z) <= 1 && y >= 1 && y <= 3) return null;
        if (x == n - 1 && Math.abs(z) <= 4 && y >= 2 && y <= 10) return Wmd.STATION_SOLID.get().defaultBlockState();
        if (end && Math.abs(z) <= 2 && y <= 4) return Wmd.STATION_BAND.get().defaultBlockState();
        if (end) return Wmd.STATION_HULL.get().defaultBlockState();
        if (x == 2 || x == n - 3) return Wmd.STATION_BAND.get().defaultBlockState();
        if (Math.abs(z) == 6 && y >= 6 && y <= 8 && x >= 11 && x <= 16) return Wmd.STATION_WINDOW.get().defaultBlockState();
        if (x % 4 == 0) return Wmd.STATION_HULL_SEAM.get().defaultBlockState();
        if (y == 0 && Math.abs(z) <= 3) return Wmd.STATION_DECK.get().defaultBlockState();
        return Wmd.STATION_HULL.get().defaultBlockState();
    }

    private static BlockState bayInside(Module m, int x, int y, int z) {
        if (y == 5 && x >= 1 && x <= 8 && (z == -5 || z == -4)) return Wmd.STATION_SOLID.get().defaultBlockState();
        if (x == 8 && z == -3 && y >= 1 && y <= 5) return Wmd.STATION_LADDER.get().defaultBlockState().setValue(StationHullBlock.Ladder.FACING, worldDir(m, Direction.SOUTH));
        return null;
    }

    private interface Solid { boolean at(int x, int y, int z); }

    private static boolean shell(Solid s, int x, int y, int z) {
        return !s.at(x + 1, y, z) || !s.at(x - 1, y, z) || !s.at(x, y + 1, z) || !s.at(x, y - 1, z) || !s.at(x, y, z + 1) || !s.at(x, y, z - 1);
    }

    static void cells(Module m, BiConsumer<BlockPos, BlockState> out) {
        switch (m.type()) {
            case CORE -> {
                for (int x = -4; x <= 4; x++) for (int y = 0; y <= 8; y++) for (int z = -4; z <= 4; z++)
                    if (coreSolid(x, y, z)) out.accept(world(m, x, y, z), coreCell(x, y, z));
            }
            case HABITAT, ROD, MIRROR, BAY -> {
                Solid sd = solid(m.type());
                int h = half(m.type());
                boolean bay = BAY.equals(m.type());
                for (int x = 0; x < len(m.type()); x++) for (int y = 0; y <= 2 * h; y++) for (int z = -h; z <= h; z++)
                    if (sd.at(x, y, z)) {
                        BlockState st = tubeCell(m.type(), x, y, z);
                        if (st == null && bay) st = bayInside(m, x, y, z);
                        out.accept(world(m, x, y, z), st);
                    }
            }
            case WING, SENSOR, LASER -> {
                int[] b = boomBox(m.type());
                for (int x = b[0]; x <= b[1]; x++) for (int y = b[2]; y <= b[3]; y++) for (int z = b[4]; z <= b[5]; z++)
                    out.accept(world(m, x, y, z), Wmd.STATION_SOLID.get().defaultBlockState());
            }
            default -> {}
        }
    }

    static BlockPos world(Module m, int x, int y, int z) {
        Direction f = m.facing();
        int wx, wz;
        switch (f) {
            case SOUTH -> { wx = -z; wz = x; }
            case WEST -> { wx = -x; wz = -z; }
            case NORTH -> { wx = z; wz = -x; }
            default -> { wx = x; wz = z; }
        }
        return m.origin().offset(wx, y, wz);
    }

    static BlockPos controller(Module m) {
        return switch (m.type()) {
            case WING, SENSOR, LASER -> world(m, 0, 1, 0);
            case TURRET -> world(m, 0, 4, 0);
            default -> world(m, 0, 0, 0);
        };
    }

    private static StationModuleBlock.Look look(String type) {
        return switch (type) { case HABITAT, ROD, MIRROR, BAY -> StationModuleBlock.Look.BAND; case WING, SENSOR, LASER, TURRET -> StationModuleBlock.Look.NONE; default -> StationModuleBlock.Look.DECK; };
    }

    static BlockPos local(Module m, BlockPos p) {
        int dx = p.getX() - m.origin().getX(), dy = p.getY() - m.origin().getY(), dz = p.getZ() - m.origin().getZ();
        return switch (m.facing()) {
            case SOUTH -> new BlockPos(dz, dy, -dx);
            case WEST -> new BlockPos(-dx, dy, -dz);
            case NORTH -> new BlockPos(-dz, dy, dx);
            default -> new BlockPos(dx, dy, dz);
        };
    }

    static boolean interior(Module m, BlockPos l) {
        int x = l.getX(), y = l.getY(), z = l.getZ();
        return switch (m.type()) {
            case CORE -> coreSolid(x, y, z) && (!shell(Station::coreSolid, x, y, z) || coreCell(x, y, z) == null);
            case HABITAT, ROD, MIRROR, BAY -> solid(m.type()).at(x, y, z) && (!shell(solid(m.type()), x, y, z) || tubeCell(m.type(), x, y, z) == null);
            default -> false;
        };
    }

    static Map<Integer, List<BlockPos>> openings(Module m) {
        Map<Integer, List<BlockPos>> o = new HashMap<>();
        switch (m.type()) {
            case CORE -> {
                for (int k = 0; k < 4; k++) {
                    List<BlockPos> c = new ArrayList<>();
                    for (int y = 1; y <= 3; y++) for (int h = -1; h <= 1; h++)
                        c.add(switch (k) { case 0 -> new BlockPos(4, y, h); case 1 -> new BlockPos(h, y, 4); case 2 -> new BlockPos(-4, y, h); default -> new BlockPos(h, y, -4); });
                    o.put(k, c);
                }
                List<BlockPos> top = new ArrayList<>();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) top.add(new BlockPos(x, 8, z));
                o.put(4, top);
            }
            case HABITAT, ROD, MIRROR, BAY -> {
                for (int k = 0; k < (closedEnd(m.type()) ? 1 : 2); k++) {
                    List<BlockPos> c = new ArrayList<>();
                    for (int y = 1; y <= 3; y++) for (int z = -1; z <= 1; z++) c.add(new BlockPos(k == 0 ? 0 : HAB_LEN - 1, y, z));
                    o.put(k, c);
                }
                if (HABITAT.equals(m.type())) for (int n = 0; n < 4; n++) {
                    List<BlockPos> c = new ArrayList<>();
                    for (int y = 3; y <= 5; y++) for (int dx = -1; dx <= 1; dx++) c.add(new BlockPos(SIDE_X[n] + dx, y, 4 * SIDE_Z[n]));
                    o.put(2 + n, c);
                }
            }
            default -> {}
        }
        return o;
    }

    record HullCell(BlockPos pos, int face) {}
    private static final Map<Integer, List<HullCell>> HULL = new HashMap<>();

    static void clearStatics() { HULL.clear(); }

    static List<HullCell> hull(Module m) {
        return HULL.computeIfAbsent(m.id(), k -> {
            List<HullCell> out = new ArrayList<>();
            if (CORE.equals(m.type())) {
                for (int x = -4; x <= 4; x++) for (int y = 0; y <= 8; y++) for (int z = -4; z <= 4; z++)
                    if (coreSolid(x, y, z) && coreCell(x, y, z) != null) out.add(new HullCell(world(m, x, y, z), face(Math.atan2(z, x))));
            } else if (tube(m.type())) {
                Solid sd = solid(m.type());
                int h = half(m.type());
                for (int x = 0; x < len(m.type()); x++) for (int y = 0; y <= 2 * h; y++) for (int z = -h; z <= h; z++)
                    if (sd.at(x, y, z) && tubeCell(m.type(), x, y, z) != null) out.add(new HullCell(world(m, x, y, z), face(Math.atan2(z, -(y - h)))));
            }
            return out;
        });
    }

    private static int face(double angle) { return Math.floorMod((int) Math.round(Math.toDegrees(angle) / 45.0), 8); }

    static int blocked(ServerLevel level, Module m) {
        int[] n = { 0 };
        cells(m, (p, st) -> {
            BlockState b = level.getBlockState(p);
            if (!b.isAir() && !testPad(b)) n[0]++;
        });
        return n[0];
    }

    private static boolean testPad(BlockState b) { return b.is(Blocks.BLUE_CONCRETE) || b.is(Blocks.LIGHT_GRAY_CONCRETE) || b.is(Blocks.SEA_LANTERN); }

    static int maxHealth(String type) {
        return switch (type) { case HABITAT -> Balance.STATION_HP_HABITAT; case WING -> Balance.STATION_HP_WING; case SENSOR -> Balance.STATION_HP_SENSOR; case LASER -> Balance.STATION_HP_LASER; case ROD -> Balance.STATION_HP_ROD; case MIRROR -> Balance.STATION_HP_MIRROR; case BAY -> Balance.STATION_HP_BAY; case TURRET -> Balance.STATION_HP_TURRET; default -> Balance.STATION_HP_CORE; };
    }

    static int health(MinecraftServer server, Module m) { return Data.get(server).health.getOrDefault(m.id(), maxHealth(m.type())); }

    static boolean wrecked(MinecraftServer server, Module m) { return health(server, m) <= 0; }

    static Module moduleAt(MinecraftServer server, int slot, BlockPos pos) {
        if (slot < 0) return null;
        for (Module m : modules(server, slot)) {
            if (!m.built()) continue;
            if (controller(m).equals(pos)) return m;
            for (HullCell c : hull(m)) if (c.pos().equals(pos)) return m;
            if (boom(m.type())) {
                BlockPos l = local(m, pos);
                int[] b = boomBox(m.type());
                if (l.getX() >= b[0] && l.getX() <= b[1] && l.getY() >= b[2] && l.getY() <= b[3] && l.getZ() >= b[4] && l.getZ() <= b[5]) return m;
            }
        }
        return null;
    }

    static int repair(ServerLevel level, int slot, Module m) {
        Data d = Data.get(level.getServer());
        d.health.remove(m.id());
        d.setDirty();
        int[] n = { 0 };
        cells(m, (p, st) -> {
            if (st != null && level.getBlockState(p).isAir()) { level.setBlock(p, st, Block.UPDATE_ALL); n[0]++; }
        });
        if (level.getBlockEntity(controller(m)) instanceof StationModuleBlockEntity be) be.setWrecked(false);
        return n[0];
    }

    static int freePort(MinecraftServer server, int slot, int want) {
        Module core = core(server, slot);
        if (core == null) return -1;
        boolean[] taken = new boolean[8];
        Module[] onPort = new Module[4];
        for (Module o : modules(server, slot)) if (o.host() == core.id() && o.port() >= 0 && o.port() < 4) { taken[o.port()] = true; onPort[o.port()] = o; }
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        for (int k = 0; k < 4; k++) if (Cargo.podAt(orbit, slot, k)) taken[k] = true;
        for (int k = 0; k < 4; k++) {
            Module h = onPort[k];
            taken[4 + k] = h == null || !HABITAT.equals(h.type()) || !h.built();
            if (!taken[4 + k]) for (Module o : modules(server, slot)) if (o.host() == h.id() && o.port() == 1) taken[4 + k] = true;
        }
        if (want >= 0) return want < 8 && !taken[want] ? want : -1;
        Integer pick = Data.get(server).nextPort.get(slot);
        if (pick != null && pick >= 0 && pick < 4 && !taken[pick]) return pick;
        for (int k = 0; k < 8; k++) if (!taken[k]) return k;
        return -1;
    }

    static int freePort(MinecraftServer server, int slot, int want, String type) {
        for (int p : spots(type)) if (spotState(server, slot, p) == 0) return p;
        return -1;
    }

    private static Module onPort(MinecraftServer server, int slot, Module core, int id, String type, int p, long arrive) {
        if (p == NADIR) return new Module(id, type, core.origin().below(6), Direction.EAST, core.id(), NADIR, arrive);
        if (p >= SIDE0 && p <= DOCK) {
            Module hab = habitatOn(server, slot, 0);
            if (hab == null) return null;
            int n = p - SIDE0;
            Direction dir = worldDir(hab, SIDE_Z[n] > 0 ? Direction.SOUTH : Direction.NORTH);
            return new Module(id, type, world(hab, SIDE_X[n], 2, 5 * SIDE_Z[n]), dir, hab.id(), 2 + n, arrive);
        }
        if (p < 4) {
            Direction dir = PORT_DIR[p];
            return new Module(id, type, core.origin().relative(dir, 5), dir, core.id(), p, arrive);
        }
        Module hab = null;
        for (Module o : modules(server, slot)) if (o.host() == core.id() && o.port() == p - 4 && HABITAT.equals(o.type())) hab = o;
        if (hab == null) return null;
        return new Module(id, type, world(hab, HAB_LEN, 0, 0), hab.facing(), hab.id(), 1, arrive);
    }

    static int shownPort(MinecraftServer server, Module m) {
        if (m.host() < 0) return -1;
        Module h = byId(server, m.host());
        if (h != null && !CORE.equals(h.type()) && m.port() >= 2) return SIDE0 + m.port() - 2;
        return h != null && !CORE.equals(h.type()) ? 4 + h.port() : m.port();
    }

    static String deliver(ServerLevel orbit, int slot, String type, int want, UUID owner) {
        MinecraftServer server = orbit.getServer();
        Data d = Data.get(server);
        Module core = core(server, slot);
        long now = orbit.getGameTime();
        Module m;
        if (CORE.equals(type)) {
            if (core != null) return "the station already has a core";
            m = new Module(d.next, CORE, Orbit.slotCentre(slot).below(), Direction.EAST, -1, -1, now);
        } else {
            if (core == null) return "the station has no core yet";
            int port = freePort(server, slot, want, type);
            if (port < 0) { String why = spotWhy(server, slot, type); return why != null ? why : "its spot is taken"; }
            m = onPort(server, slot, core, d.next, type, port, now);
            if (m == null) return "port " + port + " is taken";
        }
        d.next++;
        d.of(slot).add(m);
        d.setDirty();
        BlockPos c = controller(m);
        BlockState here = orbit.getBlockState(c);
        if (here.isAir() || testPad(here)) {
            orbit.setBlock(c, Wmd.STATION_MODULE.get().defaultBlockState().setValue(StationModuleBlock.LOOK, StationModuleBlock.Look.NONE), Block.UPDATE_CLIENTS);
            if (orbit.getBlockEntity(c) instanceof StationModuleBlockEntity be) { be.setUp(m.type(), m.facing(), owner, m.id()); be.setTimes(now, 0L); }
        }
        Wmd.LOGGER.info("[station] {} arriving at slot {} ({})", type, slot, m.origin().toShortString());
        return null;
    }

    static boolean latch(ServerLevel orbit, int slot, Module m, UUID owner) {
        BlockPos c = controller(m);
        int[] n = { 0 };
        cells(m, (p, st) -> { BlockState b = orbit.getBlockState(p); if (!p.equals(c) && !b.isAir() && !testPad(b)) n[0]++; });
        if (n[0] > 0) return false;
        Module done = m.latched();
        Data d = Data.get(orbit.getServer());
        List<Module> list = d.of(slot);
        list.set(list.indexOf(m), done);
        d.setDirty();
        place(orbit, done, owner);
        if (orbit.getBlockEntity(c) instanceof StationModuleBlockEntity be) be.setTimes(0L, boom(m.type()) ? orbit.getGameTime() : 0L);
        return true;
    }

    record Fixture(Block block, BlockPos local, Direction front) {}

    static List<Fixture> fixtures(Module m) {
        List<Fixture> out = new ArrayList<>();
        switch (m.type()) {
            case CORE -> out.add(new Fixture(Wmd.STATION_CONSOLE.get(), new BlockPos(2, 1, 3), Direction.NORTH));
            case HABITAT -> {
                for (int x : new int[] { 5, 9 }) {
                    out.add(new Fixture(Wmd.STATION_BUNK.get(), new BlockPos(x, 2, 2), Direction.NORTH));
                    out.add(new Fixture(Wmd.STATION_BUNK.get(), new BlockPos(x, 2, -2), Direction.SOUTH));
                }
                out.add(new Fixture(Wmd.STATION_OXYGEN_RACK.get(), new BlockPos(7, 1, -2), Direction.SOUTH));
            }
            case ROD -> {
                out.add(new Fixture(Wmd.STATION_ROD_RACK.get(), new BlockPos(2, 5, -3), Direction.NORTH));
                out.add(new Fixture(Wmd.STATION_ROD_CONSOLE.get(), new BlockPos(11, 2, -3), Direction.SOUTH));
            }
            case MIRROR -> out.add(new Fixture(Wmd.STATION_MIRROR_DESK.get(), new BlockPos(6, 1, -1), Direction.NORTH));
            case BAY -> out.add(new Fixture(Wmd.STATION_BAY_CONSOLE.get(), new BlockPos(2, 6, -4), Direction.NORTH));
            case TURRET -> out.add(new Fixture(Wmd.STATION_TURRET_HATCH.get(), new BlockPos(-2, 7, 2), Direction.NORTH));
            default -> {}
        }
        return out;
    }

    static Direction worldDir(Module m, Direction local) {
        if (local.getAxis().isVertical()) return local;
        int turns = switch (m.facing()) { case SOUTH -> 1; case WEST -> 2; case NORTH -> 3; default -> 0; };
        Direction d = local;
        for (int i = 0; i < turns; i++) d = d.getClockWise();
        return d;
    }

    private static void placeFixtures(ServerLevel level, Module m) {
        for (Fixture f : fixtures(m)) {
            BlockPos p = world(m, f.local().getX(), f.local().getY(), f.local().getZ());
            if (!level.getBlockState(p).isAir()) continue;
            BlockState st = f.block().defaultBlockState().setValue(MachineBlock.FACING, worldDir(m, f.front()));
            level.setBlock(p, st, Block.UPDATE_ALL);
            st.getBlock().setPlacedBy(level, p, st, null, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    static Module rodModuleAt(MinecraftServer server, int slot, BlockPos controller) { return fixtureModule(server, slot, controller, ROD); }

    static Module fixtureModule(MinecraftServer server, int slot, BlockPos controller, String type) {
        if (slot < 0) return null;
        for (Module m : modules(server, slot)) {
            if (!type.equals(m.type()) || !m.built()) continue;
            for (Fixture f : fixtures(m)) if (world(m, f.local().getX(), f.local().getY(), f.local().getZ()).equals(controller)) return m;
        }
        return null;
    }

    static void poleLadder(ServerLevel level, Module m, boolean put) {
        if (!CORE.equals(m.type())) return;
        for (int y = 1; y <= 7; y++) {
            BlockPos p = world(m, 0, y, 0);
            BlockState b = level.getBlockState(p);
            if (put && b.isAir()) level.setBlock(p, Wmd.STATION_POLE_LADDER.get().defaultBlockState(), Block.UPDATE_ALL);
            else if (!put && b.is(Wmd.STATION_POLE_LADDER.get())) level.removeBlock(p, false);
        }
    }

    static void moveRodRack(ServerLevel level, Module m, boolean sweep) {
        if (!ROD.equals(m.type())) return;
        BlockPos old = world(m, 2, 1, -2);
        if (level.getBlockState(old).is(Wmd.STATION_ROD_RACK.get())) {
            level.removeBlock(old, false);
            placeFixtures(level, m);
            Wmd.LOGGER.info("[rod] slot {}: the rod rack moved up into the overhead magazine", Orbit.slotAt(old.getX() + 0.5, old.getZ() + 0.5));
        }
        if (!sweep) return;

        int n = 0;
        for (int x = 2; x <= 9; x++) for (int y = 1; y <= 6; y++) for (int z = -2; z <= 2; z++) {
            BlockPos p = world(m, x, y, z);
            if (level.getBlockState(p).is(Wmd.MACHINE_PART.get()) && level.getBlockEntity(p) instanceof MachinePartBlockEntity be && be.validController(level) == null) { level.removeBlock(p, false); n++; }
        }
        if (level.getBlockState(world(m, 2, 5, -3)).isAir()) placeFixtures(level, m);
        if (n > 0) Wmd.LOGGER.info("[rod] module {}: {} orphan part block(s) of the old drum removed", m.id(), n);
    }

    private static void removeFixtures(ServerLevel level, Module m) {
        poleLadder(level, m, false);
        for (Fixture f : fixtures(m)) {
            BlockPos p = world(m, f.local().getX(), f.local().getY(), f.local().getZ());
            if (level.getBlockState(p).is(f.block())) level.removeBlock(p, false);
        }
    }

    private static void place(ServerLevel level, Module m, UUID owner) {
        cells(m, (p, st) -> level.setBlock(p, st == null ? Blocks.AIR.defaultBlockState() : st, Block.UPDATE_CLIENTS));
        BlockPos c = controller(m);
        level.setBlock(c, Wmd.STATION_MODULE.get().defaultBlockState().setValue(StationModuleBlock.LOOK, look(m.type())), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(c) instanceof StationModuleBlockEntity be) be.setUp(m.type(), m.facing(), owner, m.id());
        placeFixtures(level, m);
        poleLadder(level, m, true);
    }

    static String spawnCore(ServerLevel orbit, int slot, UUID owner) {
        Data d = Data.get(orbit.getServer());
        if (core(orbit.getServer(), slot) != null) return "This station already has a core";
        Module m = new Module(d.next, CORE, Orbit.slotCentre(slot).below(), Direction.EAST, -1, -1, 0L);
        int b = blocked(orbit, m);
        if (b > 0) return b + " block(s) in the way of the core";
        d.next++;
        d.of(slot).add(m);
        d.setDirty();
        place(orbit, m, owner);
        Wmd.LOGGER.info("[station] core placed at {} (slot {})", m.origin().toShortString(), slot);
        return null;
    }

    static String spawnOnCore(ServerLevel orbit, int slot, String type, int port, UUID owner) {
        Data d = Data.get(orbit.getServer());
        Module core = core(orbit.getServer(), slot);
        if (core == null) return "No core yet (the core comes first)";
        port = freePort(orbit.getServer(), slot, -1, type);
        if (port < 0) { String why = spotWhy(orbit.getServer(), slot, type); return "The " + type + ": " + (why != null ? why : "its spot is taken"); }
        Module m = onPort(orbit.getServer(), slot, core, d.next, type, port, 0L);
        if (m == null) return "Port " + port + " has no habitat";
        int b = blocked(orbit, m);
        if (b > 0) return b + " block(s) in the way of the " + type;
        d.next++;
        d.of(slot).add(m);
        d.setDirty();
        place(orbit, m, owner);
        Wmd.LOGGER.info("[station] {} placed on port {} at {} (slot {})", type, port, m.origin().toShortString(), slot);
        return null;
    }

    record BatterySpot(BlockPos local, Direction front) {}

    static List<BatterySpot> batterySpots(String type) {
        return switch (type) {
            case CORE -> List.of(new BatterySpot(new BlockPos(-2, 1, 3), Direction.NORTH), new BatterySpot(new BlockPos(2, 1, -3), Direction.SOUTH));
            case HABITAT -> List.of(new BatterySpot(new BlockPos(3, 1, 2), Direction.NORTH), new BatterySpot(new BlockPos(3, 1, -2), Direction.SOUTH),
                    new BatterySpot(new BlockPos(11, 1, 2), Direction.NORTH), new BatterySpot(new BlockPos(11, 1, -2), Direction.SOUTH));
            default -> List.of();
        };
    }

    static long capacity(MinecraftServer server, int slot) {
        Data d = Data.get(server);
        long cap = Balance.STATION_BUFFER;
        for (Module m : modules(server, slot)) {
            Integer mask = d.batteries.get(m.id());
            if (mask != null && m.built() && !wrecked(server, m)) cap += Balance.STATION_BATTERY_FE * Integer.bitCount(mask);
        }
        return cap;
    }

    static int placeBattery(ServerLevel level, Module m, BlockPos near) {
        Data d = Data.get(level.getServer());
        int mask = d.batteries.getOrDefault(m.id(), 0), best = -1;
        double bd = Double.MAX_VALUE;
        List<BatterySpot> spots = batterySpots(m.type());
        for (int i = 0; i < spots.size(); i++) {
            if ((mask >> i & 1) != 0) continue;
            BlockPos p = world(m, spots.get(i).local().getX(), spots.get(i).local().getY(), spots.get(i).local().getZ());
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            double dist = p.distSqr(near);
            if (dist < bd) { bd = dist; best = i; }
        }
        if (best < 0) return -1;
        BatterySpot s = spots.get(best);
        BlockPos p = world(m, s.local().getX(), s.local().getY(), s.local().getZ());
        BlockState st = Wmd.STATION_BATTERY.get().defaultBlockState().setValue(MachineBlock.FACING, worldDir(m, s.front()));
        level.setBlock(p, st, Block.UPDATE_ALL);
        st.getBlock().setPlacedBy(level, p, st, null, net.minecraft.world.item.ItemStack.EMPTY);
        d.batteries.put(m.id(), mask | 1 << best);
        d.setDirty();
        return best;
    }

    static boolean takeBattery(ServerLevel level, int slot, BlockPos pos) {
        Data d = Data.get(level.getServer());
        for (Module m : modules(level.getServer(), slot)) {
            List<BatterySpot> spots = batterySpots(m.type());
            for (int i = 0; i < spots.size(); i++) {
                BlockPos p = world(m, spots.get(i).local().getX(), spots.get(i).local().getY(), spots.get(i).local().getZ());
                if (!p.equals(pos)) continue;
                int mask = d.batteries.getOrDefault(m.id(), 0);
                if ((mask >> i & 1) == 0) return false;
                d.batteries.put(m.id(), mask & ~(1 << i));
                d.setDirty();
                MachineBlock.removing = true;
                try { level.removeBlock(p, false); level.removeBlock(p.above(), false); } finally { MachineBlock.removing = false; }
                return true;
            }
        }
        return false;
    }

    private static void removeBatteries(ServerLevel level, Module m) {
        Data d = Data.get(level.getServer());
        Integer mask = d.batteries.remove(m.id());
        if (mask == null) return;
        List<BatterySpot> spots = batterySpots(m.type());
        for (int i = 0; i < spots.size(); i++) {
            if ((mask >> i & 1) == 0) continue;
            BlockPos p = world(m, spots.get(i).local().getX(), spots.get(i).local().getY(), spots.get(i).local().getZ());
            if (level.getBlockState(p).is(Wmd.STATION_BATTERY.get())) { level.removeBlock(p, false); level.removeBlock(p.above(), false); }
        }
    }

    static void syncBatteries(ServerLevel level, int slot, long buf, long cap, boolean draining) {
        Data d = Data.get(level.getServer());
        for (Module m : modules(level.getServer(), slot)) {
            Integer mask = d.batteries.get(m.id());
            if (mask == null) continue;
            boolean dead = !m.built() || wrecked(level.getServer(), m);
            int cells = dead ? 0 : (int) Math.round(12.0 * buf / Math.max(1L, cap));
            List<BatterySpot> spots = batterySpots(m.type());
            for (int i = 0; i < spots.size(); i++) {
                if ((mask >> i & 1) == 0) continue;
                BlockPos p = world(m, spots.get(i).local().getX(), spots.get(i).local().getY(), spots.get(i).local().getZ());
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof StationFixtureBlockEntity be) be.setCharge(cells, draining && !dead);
            }
        }
    }

    static boolean breaksOff(String type) {
        return WING.equals(type) || SENSOR.equals(type) || LASER.equals(type) || ROD.equals(type) || MIRROR.equals(type) || BAY.equals(type) || TURRET.equals(type);
    }

    static int detach(ServerLevel orbit, int slot, Module m) {
        Data d = Data.get(orbit.getServer());
        List<Module> list = d.of(slot);
        int n = 0;
        for (Module h : new java.util.ArrayList<>(list)) if (h.host() == m.id()) n += detach(orbit, slot, h);
        removeBatteries(orbit, m);
        removeFixtures(orbit, m);
        orbit.setBlock(controller(m), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        cells(m, (p, st) -> { if (!orbit.getBlockState(p).isAir()) orbit.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); });
        list.removeIf(x -> x.id() == m.id());
        if (BAY.equals(m.type())) AsteroidDrop.aborted(orbit, slot, d.bay.get(m.id()), d.bayT.get(m.id()));
        HULL.remove(m.id()); d.air.remove(m.id()); d.health.remove(m.id()); d.rods.remove(m.id()); d.bay.remove(m.id()); d.bayT.remove(m.id());
        if (Integer.valueOf(m.id()).equals(d.rodArmed.get(slot))) d.rodArmed.remove(slot);
        if (MIRROR.equals(m.type())) d.mirrorUp.remove(slot);
        if (LASER.equals(m.type()) && list.stream().noneMatch(x -> LASER.equals(x.type()))) d.laserAuto.remove(slot);
        d.setDirty();
        Wmd.LOGGER.info("[station] slot {}: the {} (module {}) broke off", slot, m.type(), m.id());
        return n + 1;
    }

    static int clear(ServerLevel orbit, int slot) {
        Data d = Data.get(orbit.getServer());
        List<Module> list = d.of(slot);
        int n = list.size();
        for (Module m : list) {
            removeBatteries(orbit, m);
            removeFixtures(orbit, m);
            orbit.setBlock(controller(m), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            cells(m, (p, st) -> { if (!orbit.getBlockState(p).isAir()) orbit.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); });
        }
        for (Module m : list) { HULL.remove(m.id()); d.air.remove(m.id()); d.health.remove(m.id()); d.rods.remove(m.id()); d.bay.remove(m.id()); d.bayT.remove(m.id()); }
        list.clear();
        d.energy.remove(slot);
        d.setDirty();
        StationLife.forget(slot);
        AsteroidDrop.forget(slot);
        return n;
    }
}
