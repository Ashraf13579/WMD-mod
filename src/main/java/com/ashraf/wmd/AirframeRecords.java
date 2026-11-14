package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AirframeRecords extends SavedData {

    static final String NAME = "wmd_airframes";

    static final byte OK = 0, MISSING = 1, WRONG = 2, SHAPE = 3, DAMAGED = 4, SKIN = 5, SETTING = 6;

    static final Set<String> SKIN_KEYS = Set.of("ccSkin", "aeCopy", "spCopy");

    static final Set<String> SETTING_KEYS = Set.of("gpFlush", "jpFlush", "tpFlush", "nrFlush", "lgRole", "lgSize", "hrLen", "hrBlades", "hrFold", "jcTint",
            "jeVar", "tpMode", "nlPower", "nlBright", "dlMode", "aeNacelle", "spNoTail");

    static final Set<String> LIVE_PROPS = Set.of("powered", "open", "lit", "waterlogged", "triggered", "enabled", "extended", "power", "in_wall", "attached",
            "locked", "distance", "persistent", "level", "occupied");

    static boolean sameShape(BlockState cur, BlockState want) {
        for (net.minecraft.world.level.block.state.properties.Property<?> p : want.getProperties()) {
            if (LIVE_PROPS.contains(p.getName())) continue;
            if (!cur.hasProperty(p) || !cur.getValue(p).equals(want.getValue(p))) return false;
        }
        return true;
    }

    static final int A_OPEN = 0, A_RECORD = 1, A_REFRESH = 2, A_RELINK = 3;

    static final class Rec {
        UUID owner, by;
        String byName = "", name = "";
        long time;
        int ox, oy, oz;
        final List<BlockState> pal = new ArrayList<>();
        long[] cells = new long[0];
        final Map<Integer, CompoundTag> spec = new HashMap<>();

        int size() { return cells.length; }

        int x(int i) { return ox + (int) (cells[i] >>> 44 & 0xFFF); }

        int y(int i) { return oy + (int) (cells[i] >>> 32 & 0xFFF); }

        int z(int i) { return oz + (int) (cells[i] >>> 20 & 0xFFF); }

        BlockState state(int i) { return pal.get((int) (cells[i] & 0xFFFFF)); }

        static long pack(int dx, int dy, int dz, int p) { return (long) dx << 44 | (long) dy << 32 | (long) dz << 20 | p; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (owner != null) t.putUUID("own", owner);
            if (by != null) t.putUUID("by", by);
            t.putString("byN", byName);
            t.putString("n", name);
            t.putLong("t", time);
            t.putIntArray("o", new int[] { ox, oy, oz });
            t.put("pal", palTag());
            t.put("c", new LongArrayTag(cells));
            CompoundTag sp = new CompoundTag();
            for (Map.Entry<Integer, CompoundTag> e : spec.entrySet()) sp.put(Integer.toString(e.getKey()), e.getValue());
            t.put("sp", sp);
            return t;
        }

        ListTag palTag() {
            ListTag l = new ListTag();
            for (BlockState s : pal) l.add(NbtUtils.writeBlockState(s));
            return l;
        }

        static Rec load(CompoundTag t, HolderGetter<Block> blocks) {
            Rec r = new Rec();
            r.owner = t.hasUUID("own") ? t.getUUID("own") : null;
            r.by = t.hasUUID("by") ? t.getUUID("by") : null;
            r.byName = t.getString("byN");
            r.name = t.getString("n");
            r.time = t.getLong("t");
            int[] o = t.getIntArray("o");
            if (o.length == 3) { r.ox = o[0]; r.oy = o[1]; r.oz = o[2]; }
            ListTag l = t.getList("pal", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) r.pal.add(NbtUtils.readBlockState(blocks, l.getCompound(i)));
            r.cells = t.getLongArray("c");
            CompoundTag sp = t.getCompound("sp");
            for (String k : sp.getAllKeys()) {
                try { r.spec.put(Integer.parseInt(k), sp.getCompound(k)); } catch (NumberFormatException ignored) {}
            }
            return r;
        }
    }

    private final Map<UUID, Rec[]> ships = new LinkedHashMap<>();

    private final Map<UUID, long[]> addedCache = new HashMap<>();
    private final Map<UUID, int[]> lastAsk = new HashMap<>();

    public static AirframeRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AirframeRecords::new, AirframeRecords::load, null), NAME);
    }

    Rec now(UUID ship) { Rec[] r = ships.get(ship); return r == null ? null : r[0]; }

    static boolean tooSoon(Map<UUID, int[]> last, ServerPlayer sp, int slot, int gap) {
        int tick = sp.server.getTickCount();
        int[] t = last.get(sp.getUUID());
        if (t == null) {
            if (last.size() > 512) last.clear();
            last.put(sp.getUUID(), t = new int[] { Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2 });
        }
        if (tick >= t[slot] && tick - t[slot] < gap) return true;
        t[slot] = tick;
        return false;
    }

    Rec previous(UUID ship) { Rec[] r = ships.get(ship); return r == null ? null : r[1]; }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Rec[]> e : ships.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("id", e.getKey());
            if (e.getValue()[0] != null) s.put("now", e.getValue()[0].save());
            if (e.getValue()[1] != null) s.put("prev", e.getValue()[1].save());
            l.add(s);
        }
        tag.put("ships", l);
        return tag;
    }

    public static AirframeRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        AirframeRecords d = new AirframeRecords();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag l = tag.getList("ships", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag s = l.getCompound(i);
            if (!s.hasUUID("id")) continue;
            Rec now = s.contains("now") ? Rec.load(s.getCompound("now"), blocks) : null;
            Rec prev = s.contains("prev") ? Rec.load(s.getCompound("prev"), blocks) : null;
            if (now != null) d.ships.put(s.getUUID("id"), new Rec[] { now, prev });
        }
        return d;
    }

    static UUID craftOwner(ServerLevel sl, UUID ship) { return Crafts.owner(sl, ship); }

    static CompoundTag specOf(BlockEntity be, HolderLookup.Provider reg) {
        CompoundTag out = new CompoundTag();
        if (be == null) return out;
        CompoundTag t;
        try { t = be.saveWithoutMetadata(reg); } catch (RuntimeException e) { return out; }
        for (String k : t.getAllKeys()) if (SKIN_KEYS.contains(k) || SETTING_KEYS.contains(k)) out.put(k, t.get(k).copy());
        return out;
    }

    static boolean solid(BlockState st) { return !st.isAir() && !(st.getBlock() instanceof LiquidBlock); }

    static boolean tooBig(int[] b) {
        long dx = b[3] - b[0] + 1L, dy = b[4] - b[1] + 1L, dz = b[5] - b[2] + 1L;
        return dx <= 0 || dy <= 0 || dz <= 0 || dx > 4096 || dy > 4096 || dz > 4096 || dx * dy * dz > Balance.CRAFT_SCAN_MAX;
    }

    static Rec capture(ServerLevel sl, UUID ship) {
        int[] b = SableBridge.plotBox(sl, ship);
        if (b == null) return null;
        if (tooBig(b)) return null;
        Rec r = new Rec();
        r.ox = b[0]; r.oy = b[1]; r.oz = b[2];
        Map<BlockState, Integer> idx = new HashMap<>();
        List<Long> cells = new ArrayList<>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        HolderLookup.Provider reg = sl.registryAccess();
        for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) for (int x = b[0]; x <= b[3]; x++) {
            q.set(x, y, z);
            BlockState st = sl.getBlockState(q);
            if (!solid(st)) continue;
            if (cells.size() >= Balance.REC_MAX_BLOCKS) return null;
            Integer p = idx.get(st);
            if (p == null) { p = r.pal.size(); idx.put(st, p); r.pal.add(st); }
            if (st.hasBlockEntity()) {
                CompoundTag sp = specOf(sl.getBlockEntity(q), reg);
                if (!sp.isEmpty()) r.spec.put(cells.size(), sp);
            }
            cells.add(Rec.pack(x - b[0], y - b[1], z - b[2], p));
        }
        r.cells = new long[cells.size()];
        for (int i = 0; i < r.cells.length; i++) r.cells[i] = cells.get(i);
        return r.cells.length == 0 ? null : r;
    }

    static byte[] compare(ServerLevel sl, Rec r, CompoundTag out) {
        byte[] st = new byte[r.size()];
        List<Integer> hp = new ArrayList<>();
        CompoundTag skins = new CompoundTag();
        HolderLookup.Provider reg = sl.registryAccess();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int i = 0; i < r.size(); i++) {
            q.set(r.x(i), r.y(i), r.z(i));
            BlockState want = r.state(i), cur = sl.getBlockState(q);
            if (!solid(cur)) { st[i] = MISSING; continue; }
            if (cur.getBlock() != want.getBlock()) { st[i] = WRONG; continue; }
            if (cur != want && !sameShape(cur, want)) { st[i] = SHAPE; continue; }
            if (!cur.hasBlockEntity()) continue;
            BlockEntity be = sl.getBlockEntity(q);
            if (be instanceof AirEngineBlockEntity ae && ae.damageable() && ae.hp() < Balance.ENGINE_HP) {
                st[i] = DAMAGED;
                hp.add(i); hp.add(ae.hp());
                continue;
            }
            CompoundTag rec = r.spec.getOrDefault(i, new CompoundTag()), now = specOf(be, reg);
            Set<String> keys = new HashSet<>(rec.getAllKeys());
            keys.addAll(now.getAllKeys());
            byte s = OK;
            for (String k : keys) {
                if (java.util.Objects.equals(rec.get(k), now.get(k))) continue;
                if (SKIN_KEYS.contains(k)) {
                    s = SKIN;
                    if (rec.contains(k)) skins.put(Integer.toString(i), rec.get(k).copy());
                    break;
                }
                s = SETTING;
            }
            st[i] = s;
        }
        int[] h = new int[hp.size()];
        for (int i = 0; i < h.length; i++) h[i] = hp.get(i);
        out.putIntArray("hp", h);
        out.put("skin", skins);
        return st;
    }

    static int added(ServerLevel sl, UUID ship, Rec r) {
        int[] b = SableBridge.plotBox(sl, ship);
        if (b == null || tooBig(b)) return 0;
        Map<UUID, long[]> cache = get(sl.getServer()).addedCache;
        long tick = sl.getServer().getTickCount();
        long[] c = cache.get(ship);
        if (c != null && c[0] == r.time && tick - c[1] < Balance.REC_ADDED_CACHE) return (int) c[2];
        Set<Long> have = new HashSet<>();
        for (int i = 0; i < r.size(); i++) have.add(BlockPos.asLong(r.x(i), r.y(i), r.z(i)));
        int n = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) for (int x = b[0]; x <= b[3]; x++) {
            if (have.contains(BlockPos.asLong(x, y, z))) continue;
            q.set(x, y, z);
            if (solid(sl.getBlockState(q))) n++;
        }
        if (cache.size() > 256) cache.clear();
        cache.put(ship, new long[] { r.time, tick, n });
        return n;
    }

    static ListTag pieces(ServerLevel sl, UUID ship) {
        ListTag l = new ListTag();
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        if (s == null) return l;
        for (SableBridge.DebrisScan d : SableBridge.debrisScan(sl)) {
            if (!ship.equals(d.splitFrom()) || d.bounds() == null) continue;
            Vec3 c = d.bounds().getCenter(), v = c.subtract(s.worldCenter());
            CompoundTag t = new CompoundTag();
            t.putInt("d", (int) Math.round(Math.sqrt(v.x * v.x + v.z * v.z)));
            t.putString("dir", compass(v.x, v.z));
            l.add(t);
            if (l.size() >= 4) break;
        }
        return l;
    }

    static String compass(double dx, double dz) {
        String[] c = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        return c[Math.floorMod((int) Math.round(a / 45.0), 8)];
    }

    static Rec moved(Rec r, int bx, int by, int bz, int k, int ax, int ay, int az) {
        Rotation rot = k == 1 ? Rotation.CLOCKWISE_90 : k == 2 ? Rotation.CLOCKWISE_180 : k == 3 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE;
        int n = r.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int[] t = turn(r.x(i) - bx, r.z(i) - bz, k);
            xs[i] = ax + t[0]; ys[i] = ay + r.y(i) - by; zs[i] = az + t[1];
            mx = Math.min(mx, xs[i]); my = Math.min(my, ys[i]); mz = Math.min(mz, zs[i]);
        }
        Rec o = new Rec();
        o.owner = r.owner; o.by = r.by; o.byName = r.byName; o.name = r.name; o.time = r.time;
        o.ox = mx; o.oy = my; o.oz = mz;
        Map<BlockState, Integer> idx = new HashMap<>();
        o.cells = new long[n];
        for (int i = 0; i < n; i++) {
            BlockState st = r.state(i).rotate(rot);
            Integer p = idx.get(st);
            if (p == null) { p = o.pal.size(); idx.put(st, p); o.pal.add(st); }
            o.cells[i] = Rec.pack(xs[i] - mx, ys[i] - my, zs[i] - mz, p);
        }
        o.spec.putAll(r.spec);
        return o;
    }

    static int[] turn(int dx, int dz, int k) {
        for (int i = 0; i < (k & 3); i++) { int t = dx; dx = -dz; dz = t; }
        return new int[] { dx, dz };
    }

    static int[] align(ServerLevel sl, UUID ship, Rec r) {
        int[] b = SableBridge.plotBox(sl, ship);
        if (b == null || r.size() == 0) return null;
        int[] count = new int[r.pal.size()];
        for (int i = 0; i < r.size(); i++) count[(int) (r.cells[i] & 0xFFFFF)]++;
        Block rare = null;
        int best = Integer.MAX_VALUE, anchor = -1;
        for (int i = 0; i < r.size(); i++) {
            int c = count[(int) (r.cells[i] & 0xFFFFF)];
            if (c < best) { best = c; anchor = i; rare = r.state(i).getBlock(); }
        }
        if (rare == null) return null;
        List<BlockPos> cand = new ArrayList<>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) for (int x = b[0]; x <= b[3]; x++) {
            q.set(x, y, z);
            if (sl.getBlockState(q).getBlock() == rare) { cand.add(q.immutable()); if (cand.size() >= 64) break; }
        }
        int step = Math.max(1, r.size() / Balance.REC_RELINK_SAMPLE), samples = (r.size() + step - 1) / step;
        int[] out = null;
        int top = -1;
        int bx = r.x(anchor), by = r.y(anchor), bz = r.z(anchor);
        for (BlockPos c : cand) for (int k = 0; k < 4; k++) {
            int hit = 0;
            for (int i = 0; i < r.size(); i += step) {
                int[] t = turn(r.x(i) - bx, r.z(i) - bz, k);
                q.set(c.getX() + t[0], c.getY() + r.y(i) - by, c.getZ() + t[1]);
                if (sl.getBlockState(q).getBlock() == r.state(i).getBlock()) hit++;
            }
            if (hit > top) { top = hit; out = new int[] { bx, by, bz, k, c.getX(), c.getY(), c.getZ() }; }
        }
        return top >= Balance.REC_RELINK_MATCH * samples ? out : null;
    }

    static void handle(ServerPlayer sp, int action, UUID ship, UUID arg, int base) {

        if (!AirGate.on() || sp == null || ship == null || !(sp.level() instanceof ServerLevel sl) || !SableBridge.active()) {
            Wmd.LOGGER.info("[airframe] ask {} for {}: dropped (gate {}, sable {})", action, ship, AirGate.on(), SableBridge.active());
            return;
        }
        if (!FlightCheck.inReach(sp, ship)) { Wmd.LOGGER.info("[airframe] ask {} for {}: far (eye {})", action, ship, sp.getEyePosition()); msg(sp, "far"); return; }
        AirframeRecords d = get(sl.getServer());
        if (action == A_REFRESH ? tooSoon(d.lastAsk, sp, 0, Balance.REC_REFRESH_TICKS / 2) : tooSoon(d.lastAsk, sp, 1, Balance.SCREEN_ASK_TICKS)) return;
        Rec now = d.now(ship);
        UUID owner = now != null ? now.owner : craftOwner(sl, ship);
        if (owner == null) { Wmd.LOGGER.info("[airframe] ask {} for {}: no owner", action, ship); msg(sp, "no_owner"); return; }
        if (!ShipStrike.sameSide(sp.getUUID(), owner)) { Wmd.LOGGER.info("[airframe] ask {} for {}: not yours", action, ship); msg(sp, "not_yours"); return; }
        switch (action) {
            case A_RECORD -> {
                Rec r = capture(sl, ship);
                if (r == null) { msg(sp, "too_big"); return; }
                SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
                r.owner = owner;
                r.by = sp.getUUID();
                r.byName = sp.getGameProfile().getName();
                r.name = s == null || s.name() == null || s.name().isBlank() ? "" : s.name();
                r.time = System.currentTimeMillis();
                d.ships.put(ship, new Rec[] { r, now });
                d.setDirty();
                sl.playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, net.minecraft.sounds.SoundSource.PLAYERS, Balance.SND_CLICK, 1.2F);
                send(sp, sl, d, ship, 0, true);
            }
            case A_RELINK -> {
                Rec old = arg == null ? null : d.now(arg);
                if (old == null || now != null || !ShipStrike.sameSide(sp.getUUID(), old.owner) || SableBridge.shipInfo(sl, arg) != null) { msg(sp, "relink_fail"); return; }
                int[] a = align(sl, ship, old);
                if (a == null) { msg(sp, "relink_fail"); return; }
                Rec[] pair = d.ships.remove(arg);
                Rec p = pair[1] == null ? null : moved(pair[1], a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
                d.ships.put(ship, new Rec[] { moved(pair[0], a[0], a[1], a[2], a[3], a[4], a[5], a[6]), p });
                d.setDirty();
                msg(sp, "relinked");
                send(sp, sl, d, ship, 0, true);
            }
            case A_REFRESH -> send(sp, sl, d, ship, base, false);
            default -> send(sp, sl, d, ship, base, true);
        }
    }

    static int faults(ServerLevel sl, UUID ship) {
        Rec r = get(sl.getServer()).now(ship);
        if (r == null) return -1;
        int n = 0;
        for (byte b : compare(sl, r, new CompoundTag())) if (b != OK) n++;
        return n;
    }

    static void openFor(ServerPlayer sp, UUID ship) { handle(sp, A_OPEN, ship, null, 0); }

    private static void msg(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable("message.wmd.airframe." + key), true);
    }

    static void send(ServerPlayer sp, ServerLevel sl, AirframeRecords d, UUID ship, int base, boolean full) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", ship);
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        Rec now = d.now(ship), prev = d.previous(ship);
        Rec r = base == 1 && prev != null ? prev : now;
        t.putString("n", s == null || s.name() == null ? "" : s.name());
        t.putBoolean("full", full);
        if (r == null) {

            Rec live = capture(sl, ship);
            t.putInt("mode", 0);
            if (live == null) { Wmd.LOGGER.info("[airframe] {}: too big / no blocks to record", ship); msg(sp, "too_big"); return; }
            putModel(t, live);
            int machines = 0, skins = 0, settings = 0;
            for (int i = 0; i < live.size(); i++) if (live.state(i).hasBlockEntity()) machines++;
            for (CompoundTag sp2 : live.spec.values()) for (String k : sp2.getAllKeys()) { if (SKIN_KEYS.contains(k)) skins++; else settings++; }
            t.putInt("mc", machines);
            t.putInt("sk", skins);
            t.putInt("set", settings);
            ListTag rl = new ListTag();
            for (Map.Entry<UUID, Rec[]> e : d.ships.entrySet()) {
                Rec o = e.getValue()[0];
                if (o == null || !ShipStrike.sameSide(sp.getUUID(), o.owner) || SableBridge.shipInfo(sl, e.getKey()) != null) continue;
                CompoundTag c = new CompoundTag();
                c.putUUID("id", e.getKey());
                c.putString("n", o.name);
                c.putLong("t", o.time);
                c.putInt("b", o.size());
                rl.add(c);
                if (rl.size() >= 3) break;
            }
            t.put("relink", rl);
        } else {
            t.putInt("mode", 1);
            t.putInt("base", r == prev ? 1 : 0);
            t.putBoolean("prev", prev != null);
            t.putLong("t", r.time);
            t.putString("by", r.byName);
            if (full) putModel(t, r);
            t.putByteArray("st", compare(sl, r, t));
            t.putInt("add", added(sl, ship, r));
            t.put("pieces", pieces(sl, ship));
        }
        PacketDistributor.sendToPlayer(sp, new AirframePayload.Show(t));
    }

    private static void putModel(CompoundTag t, Rec r) {
        t.putIntArray("o", new int[] { r.ox, r.oy, r.oz });
        t.put("pal", r.palTag());
        t.put("c", new LongArrayTag(r.cells));
    }
}
