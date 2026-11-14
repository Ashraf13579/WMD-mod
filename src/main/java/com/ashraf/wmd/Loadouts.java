package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class Loadouts {

    private Loadouts() {}

    private record Grid(int w, int h, int f, int minX, int minY, int minZ, byte[] cells, long at) {}
    private static final Map<UUID, Grid> GRIDS = Statics.server(new HashMap<>());

    private static final Map<UUID, int[]> SENT = Statics.server(new HashMap<>());

    static void open(ServerPlayer sp, AmmoDepotBlockEntity d) {
        if (!AirGate.on()) return;
        PacketDistributor.sendToPlayer(sp, new LoadoutPayload.Show(snapshot(sp.serverLevel(), d)));
    }

    static CompoundTag snapshot(ServerLevel sl, AmmoDepotBlockEntity d) {
        CompoundTag t = new CompoundTag();
        t.putLong("depot", d.getBlockPos().asLong());
        t.putInt("rounds", d.rounds());
        t.putBoolean("ordered", d.ordered());
        ListTag stock = new ListTag();
        List<ItemStack> types = d.storeTypes();
        for (ItemStack s : types) stock.add(s.saveOptional(sl.registryAccess()));
        t.put("stock", stock);
        UUID owner = d.getOwner();
        List<OrdnanceLoaderBlockEntity> loaders = loadersNear(sl, d);
        t.putInt("loaders", loaders.size());
        ListTag crafts = new ListTag();
        if (owner != null) {
            Vec3 me = SableBridge.worldPos(sl, d.getBlockPos());
            AABB box = new AABB(me, me).inflate(Balance.AD_RANGE + Balance.LOADER_TRACK_MAX, Balance.AD_RANGE_Y, Balance.AD_RANGE + Balance.LOADER_TRACK_MAX);
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) {
                if (!reachable(sl, d, s, loaders)) continue;
                List<StockTargets.Target> ts = StockTargets.of(sl, s.id(), owner);
                if (ts.isEmpty()) continue;
                crafts.add(craft(sl, d, s, ts, types, loaders));
            }
        }
        t.put("crafts", crafts);
        return t;
    }

    private static CompoundTag craft(ServerLevel sl, AmmoDepotBlockEntity d, SableBridge.ShipInfo s, List<StockTargets.Target> ts, List<ItemStack> types,
                                     List<OrdnanceLoaderBlockEntity> loaders) {
        CompoundTag c = new CompoundTag();
        c.putUUID("id", s.id());
        c.putString("name", s.name() == null || s.name().isBlank() ? "Craft" : s.name());
        c.putString("kind", kind(sl, s.id()));
        boolean air = AirGate.on() && AirPicture.isAircraft(sl, s.id());
        c.putBoolean("air", air);
        boolean parked = !air && !loaders.isEmpty() && loaders.get(0).parked(sl, s.id());
        c.putBoolean("parked", parked || (!air && loaders.isEmpty()));
        c.putInt("mode", d.modeFor(s.id()));
        Grid g = grid(sl, s.id());
        if (g != null) { c.putInt("gw", g.w()); c.putInt("gh", g.h()); c.putByteArray("grid", g.cells()); }
        CompoundTag lo = LoadoutPlan.read(sl, s.id(), d);
        boolean hasPlan = LoadoutPlan.hasPlan(lo);
        c.putBoolean("hasPlan", hasPlan);
        c.putBoolean("holder", LoadoutPlan.holderOf(sl, s.id()) != null);
        Map<Long, ItemStack> plan = LoadoutPlan.plan(lo, sl.registryAccess());
        ListTag list = new ListTag();
        for (StockTargets.Target t : ts) {
            CompoundTag e = new CompoundTag();
            BlockPos p = t.pos();
            e.putLong("p", p.asLong());
            e.putString("g", t.group());
            if (g != null) { e.putInt("x", (p.getX() - g.minX()) / g.f()); e.putInt("z", (p.getZ() - g.minZ()) / g.f()); }
            e.putInt("y", g == null ? 0 : p.getY() - g.minY());
            e.put("cur", t.current().saveOptional(sl.registryAccess()));
            e.putString("fill", t.fill());
            e.putBoolean("full", t.full());
            ItemStack pl = plan.get(p.asLong());
            if (pl != null) e.put("plan", pl.saveOptional(sl.registryAccess()));
            ListTag opt = new ListTag();
            for (ItemStack o : types) if (opt.size() < 8 && t.accepts(o.copyWithCount(1))) opt.add(o.copyWithCount(1).saveOptional(sl.registryAccess()));
            e.put("opt", opt);
            boolean reach = false;
            for (OrdnanceLoaderBlockEntity ld : loaders) if (ld.canServe(sl, p)) { reach = true; break; }
            e.putBoolean("reach", reach);
            e.putBoolean("busy", d.busyAt(p));
            e.putInt("grp", LoadoutPlan.groupOf(lo, p.asLong()));
            list.add(e);
        }
        c.put("targets", list);
        ListTag pn = new ListTag();
        for (String n : LoadoutPlan.presetNames(lo)) { CompoundTag x = new CompoundTag(); x.putString("n", n); pn.add(x); }
        c.put("presets", pn);
        c.putInt("active", LoadoutPlan.activePreset(lo));
        int[] modes = new int[LoadoutPlan.GROUPS];
        for (int i = 0; i < modes.length; i++) modes[i] = LoadoutPlan.groupMode(lo, i);
        c.putIntArray("gmodes", modes);
        return c;
    }

    private static String kind(ServerLevel sl, UUID ship) {
        boolean helm = false;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) {
            var be = sl.getBlockEntity(p);
            if (be instanceof JetCockpitBlockEntity) return "jet";
            if (be instanceof HeliRotorBlockEntity) return "heli";
            if (be instanceof HelmStationBlockEntity) helm = true;
        }
        return helm ? "ship" : "craft";
    }

    private static Grid grid(ServerLevel sl, UUID ship) {
        long now = sl.getGameTime();
        Grid old = GRIDS.get(ship);
        if (old != null && now - old.at() < 100) return old;
        int[] b = SableBridge.plotBox(sl, ship);
        if (b == null) return null;
        int sx = b[3] - b[0] + 1, sz = b[5] - b[2] + 1;
        if (sx <= 0 || sz <= 0 || AirframeRecords.tooBig(b)) return null;
        int f = Math.max(1, (int) Math.ceil(Math.max(sx, sz) / (double) Balance.LO_VIEW_MAX));
        int w = (sx + f - 1) / f, h = (sz + f - 1) / f;
        byte[] cells = new byte[w * h];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            int gi = (z / f) * w + x / f;
            if (cells[gi] != 0) continue;
            for (int y = b[4]; y >= b[1]; y--) {
                m.set(b[0] + x, y, b[2] + z);
                if (!sl.getBlockState(m).isAir()) { cells[gi] = 1; break; }
            }
        }
        Grid g = new Grid(w, h, f, b[0], b[1], b[2], cells, now);
        if (GRIDS.size() > 256) GRIDS.clear();
        GRIDS.put(ship, g);
        return g;
    }

    static List<OrdnanceLoaderBlockEntity> loadersNear(ServerLevel sl, AmmoDepotBlockEntity d) {
        List<OrdnanceLoaderBlockEntity> l = new ArrayList<>();
        for (OrdnanceLoaderBlockEntity ld : AmmoDepots.loaders(sl)) {
            if (d.getOwner() == null || !d.getOwner().equals(ld.getOwner())) continue;
            if (AmmoDepots.nearest(sl, SableBridge.worldPos(sl, ld.getBlockPos()), ld.getOwner()) == d) l.add(ld);
        }
        return l;
    }

    static void act(ServerPlayer sp, BlockPos depotPos, CompoundTag a) {
        if (!AirGate.on()) return;
        ServerLevel sl = sp.serverLevel();
        if (!sl.isLoaded(depotPos) || !(sl.getBlockEntity(depotPos) instanceof AmmoDepotBlockEntity d)) return;
        if (d.getOwner() == null || !d.getOwner().equals(sp.getUUID())) return;
        String what0 = a.getString("a");
        boolean seat = "stock".equals(what0) && a.hasUUID("ship") && a.getUUID("ship").equals(SableBridge.shipUnder(sp));
        if (!seat && !Reach.within(sp, depotPos, Balance.LO_REACH)) return;
        String what = a.getString("a");
        if ("store".equals(what)) { AmmoDepotBlock.openStore(sp, d); return; }
        if ("refresh".equals(what) && AirframeRecords.tooSoon(SENT, sp, 0, Balance.LO_REFRESH_TICKS / 2)) return;
        if (!"refresh".equals(what)) {
            UUID ship = a.hasUUID("ship") ? a.getUUID("ship") : null;
            if (ship == null || !inRange(sl, d, ship)) return;
            if (!Crafts.ownedBy(sl, ship, d.getOwner())) return;
            CompoundTag lo = LoadoutPlan.read(sl, ship, d);
            boolean write = true;
            switch (what) {
                case "plan" -> {
                    long p = a.getLong("p");
                    if (!onCraft(sl, d, ship, p)) return;
                    ItemStack it = a.contains("i") ? ItemStack.parseOptional(sl.registryAccess(), a.getCompound("i")) : ItemStack.EMPTY;
                    if (!it.isEmpty() && !Ammo.isAmmo(it)) return;
                    LoadoutPlan.setPlan(lo, p, it, sl.registryAccess());
                    lo.remove("active");
                }
                case "plan_clear" -> { LoadoutPlan.clearPlan(lo); lo.remove("active"); }
                case "stock" -> { d.orderCraft(ship, AmmoDepotBlockEntity.MODE_STOCK); write = false; }
                case "unload" -> { d.orderCraft(ship, AmmoDepotBlockEntity.MODE_UNLOAD); write = false; }
                case "preset_save" -> LoadoutPlan.savePreset(lo, a.getString("n"));
                case "preset_load" -> LoadoutPlan.loadPreset(lo, a.getInt("i"));
                case "preset_del" -> LoadoutPlan.deletePreset(lo, a.getInt("i"));
                case "group" -> { long p = a.getLong("p"); if (!onCraft(sl, d, ship, p)) return; LoadoutPlan.toggleGroup(lo, a.getInt("g"), p); }
                case "group_mode" -> LoadoutPlan.cycleMode(lo, a.getInt("g"));
                default -> { return; }
            }
            if (write) LoadoutPlan.write(sl, ship, d, lo);
            if (AirframeRecords.tooSoon(SENT, sp, 1, Balance.SCREEN_ASK_TICKS)) return;
        }
        PacketDistributor.sendToPlayer(sp, new LoadoutPayload.Show(snapshot(sl, d)));
    }

    private static boolean inRange(ServerLevel sl, AmmoDepotBlockEntity d, UUID ship) {
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        return s != null && reachable(sl, d, s, loadersNear(sl, d));
    }

    static boolean reachable(ServerLevel sl, AmmoDepotBlockEntity d, SableBridge.ShipInfo s, List<OrdnanceLoaderBlockEntity> loaders) {
        AABB craft = s.worldBounds();
        if (loaders.isEmpty()) {
            Vec3 me = SableBridge.worldPos(sl, d.getBlockPos());
            return craft.intersects(new AABB(me, me).inflate(Balance.AD_LIST_NO_LOADER));
        }
        for (OrdnanceLoaderBlockEntity ld : loaders) {
            Vec3 w = SableBridge.worldPos(sl, ld.getBlockPos());
            double r = ld.trackLength() + (ld.heavy() ? Balance.HEAVY_LOADER_REACH : Balance.LOADER_REACH) + 1.0;
            if (craft.intersects(new AABB(w, w).inflate(r, Balance.AD_RANGE_Y * 0.25 + r * 0.25, r))) return true;
        }
        return false;
    }

    private static boolean onCraft(ServerLevel sl, AmmoDepotBlockEntity d, UUID ship, long pos) {
        for (StockTargets.Target t : StockTargets.of(sl, ship, d.getOwner())) if (t.pos().asLong() == pos) return true;
        return false;
    }
}
