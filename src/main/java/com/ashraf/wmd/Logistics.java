package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Logistics {

    private Logistics() {}

    record Job(BlockPos sourceBlock, int sourceKind, BlockPos target, Item item, boolean returning, int amount, CargoFilter filter) {
        Job(BlockPos sourceBlock, int sourceKind, BlockPos target, Item item, boolean returning, int amount) { this(sourceBlock, sourceKind, target, item, returning, amount, CargoFilter.ANY); }
        Job(BlockPos sourceBlock, int sourceKind, BlockPos target, Item item, boolean returning) { this(sourceBlock, sourceKind, target, item, returning, 1); }
        Job back() { return new Job(sourceBlock, sourceKind, target, item, true, amount, filter); }

        CompoundTag write(HolderLookup.Provider r) {
            CompoundTag t = new CompoundTag();
            t.putLong("src", sourceBlock.asLong());
            t.putInt("kind", sourceKind);
            t.putLong("target", target.asLong());
            t.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
            t.putBoolean("back", returning);
            t.putInt("amt", amount);
            filter.write(t);
            return t;
        }

        static Job read(CompoundTag t, HolderLookup.Provider r) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("item")));
            return new Job(BlockPos.of(t.getLong("src")), t.getInt("kind"), BlockPos.of(t.getLong("target")), it, t.getBoolean("back"), Math.max(1, t.getInt("amt")),
                    CargoFilter.read(t));
        }
    }

    public static final int SRC_BAY_OUT = 0, SRC_RACK = 1, SRC_MACHINE = 2, SRC_DEPOT = 3, SRC_LAB = 4, SRC_CHEST = 5;

    static int[] kindsFor(Item item) {
        if (heavy(item)) return new int[] { MissileRobotEntity.KIND_XL };
        if (item instanceof LaunchMissileItem || ROBOT_LOADS.get().contains(item)) return new int[] { MissileRobotEntity.KIND_ROBOT };
        if (item instanceof MissilePartItem p && p.slot() == 3) return new int[] { MissileRobotEntity.KIND_RUNNER, MissileRobotEntity.KIND_ROBOT };
        return new int[] { MissileRobotEntity.KIND_RUNNER };
    }

    private static final java.util.function.Supplier<Set<Item>> ROBOT_LOADS = com.google.common.base.Suppliers.memoize(() -> {
        Set<Item> s = new java.util.HashSet<>();
        s.add(Wmd.INTERCEPTOR_T1.get());
        s.add(Wmd.INTERCEPTOR_T2.get());
        for (int g = 0; g < 4; g++) for (int t = 0; t < Artillery.TYPES.length; t++) {
            try { Item it = Artillery.shellItem(g, t); if (it != null && it != net.minecraft.world.item.Items.AIR) s.add(it); } catch (RuntimeException ignored) {}
        }
        return s;
    });

    private static boolean kindOk(MissileRobotEntity v, int[] kinds) {
        for (int k : kinds) if (v.vehicleKind() == k) return true;
        return false;
    }

    static IItemHandler chestAt(Level level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (!(be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity)) return null;
        if (Wmd.MODID.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace())) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
    }

    private record ChestKey(UUID owner, net.minecraft.resources.ResourceKey<Level> dim, long pos) {}

    private static final java.util.Map<ChestKey, long[]> CHEST_OK = new java.util.HashMap<>();
    private static final long CHEST_TTL = 100L;

    static boolean chestAllowed(ServerLevel sl, UUID owner, BlockPos p) {
        if (owner == null) return false;
        long now = sl.getGameTime();
        ChestKey k = new ChestKey(owner, sl.dimension(), p.asLong());
        long[] c = CHEST_OK.get(k);
        net.minecraft.server.level.ServerPlayer op = sl.getServer().getPlayerList().getPlayer(owner);
        if (c != null && (op == null || now < c[0] && c[0] - now <= CHEST_TTL)) return c[1] != 0L;
        boolean ok = op != null ? ClaimGuard.mayPlaceQuiet(sl, op, p) : !ClaimGuard.protectedAt(sl, p);
        if (CHEST_OK.size() > 4096) CHEST_OK.clear();
        CHEST_OK.put(k, new long[] { now + CHEST_TTL, ok ? 1L : 0L });
        return ok;
    }

    static IItemHandler chestFor(ServerLevel sl, UUID owner, BlockPos p) {
        IItemHandler h = chestAt(sl, p);
        return h != null && chestAllowed(sl, owner, p) ? h : null;
    }

    static void clearStatics() { CHEST_OK.clear(); }

    static Set<Item> stockOf(ServerLevel sl, BlockEntity be) {
        Set<Item> s = new java.util.LinkedHashSet<>();
        java.util.function.Consumer<IItemHandler> add = h -> { if (h != null) for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) s.add(h.getStackInSlot(i).getItem()); };
        if (be instanceof BayModuleBlockEntity b && b.kind == BayKind.CORE) {
            add.accept(b.out);
            for (int side = 0; side < BayLine.RACKS; side++) { BayModuleBlockEntity r = BayLine.rackOf(b, side); if (r != null) add.accept(r.rackHandler()); }
        } else if (be instanceof MissileDepotBlockEntity d) add.accept(d.cells());
        else if (be instanceof LabModuleBlockEntity lab) add.accept(lab.storageOut());
        else if (be instanceof ItemProcessBlockEntity m) m.takeOutput(st -> { s.add(st.getItem()); return false; }, true);
        else add.accept(chestAt(sl, be.getBlockPos()));
        return s;
    }

    static List<BlockPos> chestsNear(ServerLevel sl, BlockPos centre, int r, int max) {
        List<BlockPos> l = new ArrayList<>();
        int c0x = (centre.getX() - r) >> 4, c1x = (centre.getX() + r) >> 4, c0z = (centre.getZ() - r) >> 4, c1z = (centre.getZ() + r) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            var ch = sl.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values())
                if (be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity && chestAt(sl, be.getBlockPos()) != null) l.add(be.getBlockPos());
        }
        l.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(centre)));
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    private static final Set<BayModuleBlockEntity> BAYS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<ItemProcessBlockEntity> MACHINES = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<MissileRobotEntity> ROBOTS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<CcPlannerBlockEntity> PLANNERS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<MissileDepotBlockEntity> DEPOTS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    private static final Set<LabModuleBlockEntity> LABS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    private static final Set<MissileTeleporterBlockEntity> TELEPORTERS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    static void noteBay(BayModuleBlockEntity core) { BAYS.add(core); }
    static void noteSource(ItemProcessBlockEntity m) { MACHINES.add(m); }
    static void noteRobot(MissileRobotEntity r) { ROBOTS.add(r); }
    static void notePlanner(CcPlannerBlockEntity p) { PLANNERS.add(p); }
    static void noteDepot(MissileDepotBlockEntity d) { DEPOTS.add(d); }
    static void noteLab(LabModuleBlockEntity storage) { LABS.add(storage); }
    static void noteTeleporter(MissileTeleporterBlockEntity t) { TELEPORTERS.add(t); }

    static List<MissileTeleporterBlockEntity> teleportersIn(Level level) {
        List<MissileTeleporterBlockEntity> l = new ArrayList<>();
        for (MissileTeleporterBlockEntity t : TELEPORTERS) if (!t.isRemoved() && t.getLevel() == level) l.add(t);
        return l;
    }

    static List<BayModuleBlockEntity> warheadCellsOf(ServerLevel sl, UUID owner) {
        List<BayModuleBlockEntity> l = new ArrayList<>();
        for (BayModuleBlockEntity core : baysIn(sl)) {
            if (!owner.equals(core.getOwner())) continue;
            for (BayModuleBlockEntity m : BayLine.chain(core)) if (m.kind == BayKind.WARHEAD && !l.contains(m)) l.add(m);
        }
        return l;
    }

    static List<MissileDepotBlockEntity> depotsOf(UUID owner) {
        List<MissileDepotBlockEntity> l = new ArrayList<>();
        for (MissileDepotBlockEntity d : DEPOTS) if (!d.isRemoved() && owner.equals(d.getOwner())) l.add(d);
        return l;
    }

    static List<MissileDepotBlockEntity> depotsIn(Level level) {
        List<MissileDepotBlockEntity> l = new ArrayList<>();
        for (MissileDepotBlockEntity d : DEPOTS) if (!d.isRemoved() && d.getLevel() == level) l.add(d);
        return l;
    }

    static List<BayModuleBlockEntity> baysIn(Level level) {
        List<BayModuleBlockEntity> l = new ArrayList<>();
        for (BayModuleBlockEntity b : BAYS) if (!b.isRemoved() && b.getLevel() == level) l.add(b);
        return l;
    }

    static boolean heavy(Item item) { return item == Wmd.MISSILE_T3.get() || item instanceof StageItem; }

    static MissileBuild buildOf(ItemStack s) {
        if (s.isEmpty()) return null;
        MissileBuild b = s.get(WmdComponents.MISSILE_BUILD.get());
        if (b == null && s.getItem() instanceof StageItem st) b = st.defaultBuild();
        return b;
    }

    static List<MissileRobotEntity> robotsOf(UUID owner) {
        List<MissileRobotEntity> l = new ArrayList<>();
        for (MissileRobotEntity r : ROBOTS) if (!r.isRemoved() && owner.equals(r.owner())) l.add(r);
        return l;
    }

    static boolean busyWith(BlockPos target) { return onTheWay(target) > 0; }

    static int onTheWay(BlockPos target) {
        int n = 0;
        for (MissileRobotEntity r : ROBOTS) if (!r.isRemoved() && r.job() != null && !r.job().returning() && r.job().target().equals(target)) n++;
        return n;
    }

    private static MissileDepotBlockEntity depotAt(ServerLevel sl, BlockPos target) {
        return sl.getBlockEntity(target) instanceof MissileDepotBlockEntity d ? d : null;
    }

    static List<Item> wanted(ServerLevel sl, BlockPos target, Item rule) {
        if (rule == Wmd.STAGE_CORE.get() && sl.getBlockEntity(target) instanceof LaunchTerminalBlockEntity t && t.getTier() >= 4)
            return List.of(Wmd.STAGE_CORE.get(), Wmd.STAGE_UPPER.get(), Wmd.STAGE_BOOSTER.get());
        return List.of(rule);
    }

    private static int slots(ServerLevel sl, BlockPos target) {
        MissileDepotBlockEntity d = depotAt(sl, target);
        return d == null ? 1 : d.freeCells();
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % Balance.PLANNER_INTERVAL != 0) return;
        for (CcPlannerBlockEntity p : new ArrayList<>(PLANNERS)) {
            if (p.isRemoved() || !p.isOnline() || !(p.getLevel() instanceof ServerLevel sl)) continue;
            List<PlannerRoute> rs = new ArrayList<>(p.routes());
            rs.sort((x, y) -> Integer.compare(y.prio, x.prio));
            for (PlannerRoute r : rs) route(sl, p, r, true);
            for (PlannerRoutine rt : p.routines()) runRoutine(sl, p, rt);
        }
    }

    public static final int COND_ALWAYS = 0, COND_DEFCON3 = 1, COND_DEFCON2 = 2, COND_DEFCON1 = 3, COND_RS_ON = 4, COND_RS_OFF = 5, COND_DAY = 6, COND_NIGHT = 7, COND_N = 8;

    static boolean active(ServerLevel sl, CcPlannerBlockEntity p, int cond) {
        return switch (cond) {
            case COND_DEFCON3, COND_DEFCON2, COND_DEFCON1 -> p.getOwner() != null && CcDefcon.level(p.getOwner()) <= 4 - cond;
            case COND_RS_ON, COND_RS_OFF -> redstone(sl, p.getBlockPos()) == (cond == COND_RS_ON);
            case COND_DAY -> sl.isDay();
            case COND_NIGHT -> !sl.isDay();
            default -> true;
        };
    }

    private static boolean redstone(ServerLevel sl, BlockPos p) {
        for (BlockPos c : cells(sl, p)) if (sl.hasNeighborSignal(c)) return true;
        return false;
    }

    static boolean reserved(UUID vehicle) {
        for (CcPlannerBlockEntity p : PLANNERS) if (!p.isRemoved()) for (PlannerRoutine rt : p.routines()) if (rt.running && rt.vehicle.equals(vehicle)) return true;
        return false;
    }

    static MissileRobotEntity vehicleOf(UUID owner, UUID id) {
        if (owner == null || id == null) return null;
        for (MissileRobotEntity m : robotsOf(owner)) if (id.equals(m.getUUID())) return m;
        return null;
    }

    static void runRoutine(ServerLevel sl, CcPlannerBlockEntity p, PlannerRoutine rt) {
        UUID owner = p.getOwner();
        if (!rt.running || rt.steps.isEmpty() || owner == null) { if (rt.state != PlannerRoutine.RS_DONE) rt.state = PlannerRoutine.RS_STOPPED; return; }
        if (!active(sl, p, rt.cond)) { rt.state = PlannerRoutine.RS_COND; return; }
        MissileRobotEntity v = vehicleOf(owner, rt.vehicle);
        if (v == null || v.level() != sl) { rt.state = PlannerRoutine.RS_GONE; return; }
        if (!v.readyForNext()) { rt.state = v.job() != null ? PlannerRoutine.RS_ON : PlannerRoutine.RS_BUSY; return; }
        rt.at = Math.floorMod(rt.at, rt.steps.size());
        Job j = stepJob(sl, owner, v, rt.steps.get(rt.at));
        if (j == null) {
            rt.state = PlannerRoutine.RS_STEP_WAIT;
            if (++rt.waits >= 2) rt.advance();
            p.setChanged();
            return;
        }
        v.start(j);
        rt.advance();
        rt.state = rt.running ? PlannerRoutine.RS_ON : PlannerRoutine.RS_DONE;
        p.setChanged();
    }

    static Job stepJob(ServerLevel sl, UUID owner, MissileRobotEntity v, PlannerRoutine.Step s) {
        if (!kindOk(v, kindsFor(s.item()))) return null;
        BlockPos src = BlockPos.of(s.src()), dst = BlockPos.of(s.dst());
        if (sl.isLoaded(dst) && chestAt(sl, dst) != null && !v.upgraded()) return null;
        if (!sl.isLoaded(src) || !sl.isLoaded(dst) || src.distSqr(dst) > (double) Balance.ROBOT_ZONE * Balance.ROBOT_ZONE) return null;
        Src from = sourceAt(sl, owner, src, s.item(), dst, CargoFilter.ANY);
        if (from == null) return null;
        IItemHandler h = port(sl, owner, dst, new ItemStack(s.item()));
        if (h == null) return null;
        int load = Math.max(1, v.carryFor(s.item()));
        int amount = switch (s.amt()) {
            case PlannerRoutine.AMT_LOAD -> load;
            case PlannerRoutine.AMT_FILL -> fits(sl, owner, dst, s.item(), load);
            default -> 1;
        };
        return new Job(from.pos(), from.kind(), dst, s.item(), false, Math.max(1, amount));
    }

    public static final int ST_UNLOADED = 0, ST_DONE = 1, ST_NO_STOCK = 2, ST_ON_WAY = 3, ST_NO_VEHICLE = 4, ST_NEXT = 5, ST_TOO_FAR = 6, ST_OFF = 7, ST_OWN_BUSY = 8,
            ST_WAIT = 9, ST_GONE = 10, ST_SRC_GONE = 11, ST_NO_UPGRADED = 12;

    static int route(ServerLevel sl, CcPlannerBlockEntity p, PlannerRoute r, boolean act) {
        UUID owner = p.getOwner();
        if (owner == null) return ST_UNLOADED;
        if (!r.on) return ST_OFF;
        if (!active(sl, p, r.cond)) return ST_WAIT;
        BlockPos target = BlockPos.of(r.dst);
        if (!sl.isLoaded(target)) return ST_UNLOADED;
        BlockEntity tbe = sl.getBlockEntity(target);
        boolean chest = chestFor(sl, owner, target) != null;
        if (tbe == null || !chest && CcPlannerBlockEntity.options(tbe).isEmpty()) return ST_GONE;
        if (r.src != 0 && sl.isLoaded(BlockPos.of(r.src)) && !isSource(sl, BlockPos.of(r.src))) return ST_SRC_GONE;
        if (r.bulk(chest)) return routeBulk(sl, p, r, target, act);
        MissileDepotBlockEntity depot = depotAt(sl, target);
        int busy = onTheWay(target);
        Item item = null;
        if (depot != null) {
            int have = 0;
            for (int i = 0; i < depot.cells().getSlots(); i++) if (r.filter.test(depot.cells().getStackInSlot(i), r.item)) have++;
            if (have + onTheWay(target, r.item, r.filter) >= r.keep || depot.freeCells() - busy <= 0) return busy > 0 ? ST_ON_WAY : ST_DONE;
            if (port(sl, owner, target, new ItemStack(r.item)) != null) item = r.item;
        } else {
            if (busy > 0) return ST_ON_WAY;
            for (Item it : wanted(sl, target, r.item)) if (port(sl, owner, target, new ItemStack(it)) != null) { item = it; break; }
        }
        if (item == null) return busy > 0 ? ST_ON_WAY : ST_DONE;
        Src src;
        if (r.src == 0) src = findSource(sl, owner, target, item, r.filter);
        else {
            BlockPos sp = BlockPos.of(r.src);
            if (sp.distSqr(target) > (double) Balance.ROBOT_ZONE * Balance.ROBOT_ZONE) return ST_TOO_FAR;
            src = sl.isLoaded(sp) ? sourceAt(sl, owner, sp, item, target, r.filter) : null;
        }
        if (src == null) return busy > 0 ? ST_ON_WAY : ST_NO_STOCK;
        int[] kinds = kindsFor(item);
        MissileRobotEntity v;
        if (r.vehicle != null) {
            v = null;
            for (MissileRobotEntity m : robotsOf(owner)) if (r.vehicle.equals(m.getUUID())) v = m;
            if (v == null || v.level() != sl || !v.free() || !kindOk(v, kinds) || reserved(v.getUUID())) return busy > 0 ? ST_ON_WAY : ST_OWN_BUSY;
        } else {
            v = null;
            for (int k : kinds) { v = freeVehicle(sl, owner, src.pos(), k, false); if (v != null) break; }
        }
        if (v == null) return busy > 0 ? ST_ON_WAY : ST_NO_VEHICLE;
        if (!act) return ST_NEXT;
        int amount = depot == null ? fits(sl, owner, target, item, v.carryFor(item)) : 1;
        v.start(new Job(src.pos(), src.kind(), target, item, false, amount, r.filter));
        return ST_ON_WAY;
    }

    static int fits(ServerLevel sl, UUID owner, BlockPos target, Item item, int max) {
        if (max <= 1) return 1;
        IItemHandler h = port(sl, owner, target, new ItemStack(item));
        if (h == null) return 1;
        ItemStack probe = new ItemStack(item, Math.min(max, item.getDefaultMaxStackSize()));
        int took = probe.getCount() - ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, probe.copy(), true)).getCount();
        return Math.max(1, took < probe.getCount() ? took : max);
    }

    static int count(IItemHandler h, Item item) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).is(item)) n += h.getStackInSlot(i).getCount();
        return n;
    }

    private static int routeBulk(ServerLevel sl, CcPlannerBlockEntity p, PlannerRoute r, BlockPos target, boolean act) {
        UUID owner = p.getOwner();
        if (onTheWay(target) > 0) return ST_ON_WAY;
        BlockPos sp = r.src == 0 ? null : BlockPos.of(r.src);
        if (sp != null && sp.distSqr(target) > (double) Balance.ROBOT_ZONE * Balance.ROBOT_ZONE) return ST_TOO_FAR;
        List<Item> cands = new ArrayList<>();
        if (r.anyItem) {
            if (sp == null || !sl.isLoaded(sp) || sl.getBlockEntity(sp) == null) return ST_NO_STOCK;
            cands.addAll(stockOf(sl, sl.getBlockEntity(sp)));
        } else cands.addAll(r.items());
        boolean wants = false;
        for (Item it : cands) {
            boolean runnerItem = false;
            for (int k : kindsFor(it)) runnerItem |= k == MissileRobotEntity.KIND_RUNNER;
            if (!runnerItem) continue;
            IItemHandler h = port(sl, owner, target, new ItemStack(it));
            if (h == null) continue;
            int need = r.mode == PlannerRoute.MODE_ALL ? Integer.MAX_VALUE : r.keep - count(h, it);
            if (need <= 0) continue;
            wants = true;
            Src src = sp == null ? findSource(sl, owner, target, it, r.filter) : sl.isLoaded(sp) ? sourceAt(sl, owner, sp, it, target, r.filter) : null;
            if (src == null) continue;
            MissileRobotEntity v;
            if (r.vehicle != null) {
                v = vehicleOf(owner, r.vehicle);
                if (v == null || v.level() != sl || !v.free() || !v.upgraded() || reserved(v.getUUID())) return ST_OWN_BUSY;
            } else v = freeVehicle(sl, owner, src.pos(), MissileRobotEntity.KIND_RUNNER, true);
            if (v == null) return ST_NO_UPGRADED;
            if (!act) return ST_NEXT;
            int amount = Math.min(need, fits(sl, owner, target, it, v.carryFor(it)));
            v.start(new Job(src.pos(), src.kind(), target, it, false, Math.max(1, amount), r.filter));
            return ST_ON_WAY;
        }
        return wants ? ST_NO_STOCK : ST_DONE;
    }

    static int onTheWay(BlockPos target, Item item, CargoFilter filter) {
        int n = 0;
        for (MissileRobotEntity r : ROBOTS)
            if (!r.isRemoved() && r.job() != null && !r.job().returning() && r.job().target().equals(target) && r.job().item() == item && r.job().filter().equals(filter)) n++;
        return n;
    }

    static Src sourceAt(ServerLevel sl, UUID owner, BlockPos p, Item item, BlockPos target) { return sourceAt(sl, owner, p, item, target, CargoFilter.ANY); }

    static Src sourceAt(ServerLevel sl, UUID owner, BlockPos p, Item item, BlockPos target, CargoFilter f) {
        if (p.equals(target)) return null;
        java.util.function.Predicate<ItemStack> want = f.of(item);
        BlockEntity be = sl.getBlockEntity(p);
        if (be instanceof BayModuleBlockEntity core && core.kind == BayKind.CORE && owner.equals(core.getOwner())) {
            if (has(core.out, want)) return new Src(core.getBlockPos(), SRC_BAY_OUT);
            for (int side = 0; side < BayLine.RACKS; side++) {
                BayModuleBlockEntity rk = BayLine.rackOf(core, side);
                if (rk != null && has(rk.rackHandler(), want)) return new Src(rk.getBlockPos(), SRC_RACK);
            }
            return null;
        }
        if (be instanceof MissileDepotBlockEntity dp && owner.equals(dp.getOwner())) return dp.takeOne(want, true).isEmpty() ? null : new Src(p, SRC_DEPOT);
        if (be instanceof LabModuleBlockEntity lab && owner.equals(lab.getOwner())) return has(lab.storageOut(), want) ? new Src(p, SRC_LAB) : null;
        if (be instanceof ItemProcessBlockEntity m) {
            Owned o = Access.ownerAt(sl, p);
            if (o != null && !Access.trusts(o, owner)) return null;
            return m.takeOutput(want, true).isEmpty() ? null : new Src(p, SRC_MACHINE);
        }
        IItemHandler chest = chestFor(sl, owner, p);
        if (chest != null && has(chest, want)) return new Src(p, SRC_CHEST);
        return null;
    }

    static boolean isSource(ServerLevel sl, BlockPos p) {
        BlockEntity be = sl.getBlockEntity(p);
        return be instanceof BayModuleBlockEntity b && b.kind == BayKind.CORE || be instanceof MissileDepotBlockEntity || be instanceof LabModuleBlockEntity
                || be instanceof ItemProcessBlockEntity || chestAt(sl, p) != null;
    }

    static boolean sourceOk(ServerLevel sl, Job job) {
        BlockPos p = job.sourceBlock();
        if (!sl.isLoaded(p)) return true;
        java.util.function.Predicate<ItemStack> want = job.filter().of(job.item());
        BlockEntity be = sl.getBlockEntity(p);
        return switch (job.sourceKind()) {
            case SRC_CHEST -> { IItemHandler h = chestAt(sl, p); yield h != null && has(h, want); }
            case SRC_BAY_OUT -> be instanceof BayModuleBlockEntity b && has(b.out, want);
            case SRC_RACK -> be instanceof BayModuleBlockEntity b && has(b.rackHandler(), want);
            case SRC_DEPOT -> be instanceof MissileDepotBlockEntity d && !d.takeOne(want, true).isEmpty();
            case SRC_LAB -> be instanceof LabModuleBlockEntity l && has(l.storageOut(), want);
            case SRC_MACHINE -> be instanceof ItemProcessBlockEntity m && !m.takeOutput(want, true).isEmpty();
            default -> false;
        };
    }

    static void craneShow(ServerLevel sl, BlockPos depot, Job job, ItemStack load, boolean in) {
        if (!(sl.getBlockEntity(depot) instanceof MissileDepotBlockEntity d)) return;
        if (in) { d.announce(load, true); return; }
        java.util.function.Predicate<ItemStack> want = job.filter().of(job.item());
        for (int i = 0; i < d.cells().getSlots(); i++) if (want.test(d.cells().getStackInSlot(i))) { d.announce(d.cells().getStackInSlot(i), false); return; }
    }

    static boolean sourceStands(ServerLevel sl, Job job) { return !sl.isLoaded(job.sourceBlock()) || sl.getBlockEntity(job.sourceBlock()) != null; }

    private static MissileRobotEntity freeVehicle(ServerLevel sl, UUID owner, BlockPos src, int kind, boolean needUpgrade) {
        MissileRobotEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (MissileRobotEntity r : robotsOf(owner)) {
            if (r.level() != sl || !r.free() || r.vehicleKind() != kind || reserved(r.getUUID()) || needUpgrade && !r.upgraded()) continue;
            double d = r.position().distanceToSqr(src.getX(), src.getY(), src.getZ());
            if (d < bestD && d <= (2.0 * Balance.ROBOT_ZONE) * (2.0 * Balance.ROBOT_ZONE)) { bestD = d; best = r; }
        }
        return best;
    }

    static List<BlockEntity> sourcesIn(Level level) {
        List<BlockEntity> l = new ArrayList<>();
        for (BayModuleBlockEntity b : BAYS) if (!b.isRemoved() && b.getLevel() == level) l.add(b);
        for (MissileDepotBlockEntity d : DEPOTS) if (!d.isRemoved() && d.getLevel() == level) l.add(d);
        for (LabModuleBlockEntity s : LABS) if (!s.isRemoved() && s.getLevel() == level) l.add(s);
        for (ItemProcessBlockEntity m : MACHINES) if (!m.isRemoved() && m.getLevel() == level) l.add(m);
        return l;
    }

    static IItemHandler port(ServerLevel sl, UUID owner, BlockPos target, ItemStack stack) {
        Owned o = Access.ownerAt(sl, target);
        if (o != null && !Access.trusts(o, owner)) return null;
        if (o == null && chestAt(sl, target) != null && !chestAllowed(sl, owner, target)) return null;
        List<BlockPos> cs = cells(sl, target);
        if (sl.getBlockEntity(target) instanceof BayModuleBlockEntity b && b.kind == BayKind.CORE)
            for (BayModuleBlockEntity m : BayLine.chain(b)) if (m.kind == BayKind.CRANE || m.kind == BayKind.WARHEAD) cs.addAll(cells(sl, m.getBlockPos()));
        for (BlockPos cell : cs) for (Direction d : Direction.values()) {
            IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, cell, d);
            if (h == null) continue;
            ItemStack rest = ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, stack.copy(), true));
            if (rest.getCount() < stack.getCount()) return h;
        }
        return null;
    }

    static List<BlockPos> cells(Level level, BlockPos pos) {
        List<BlockPos> l = new ArrayList<>();
        var st = level.getBlockState(pos);
        if (st.getBlock() instanceof MachineBlock mb && st.hasProperty(MachineBlock.FACING)) {
            for (BlockPos c : mb.footprint()) l.add(Machines.toWorld(pos, st.getValue(MachineBlock.FACING), Machines.mount(st), c.getX(), c.getY(), c.getZ()));
        } else l.add(pos);
        return l;
    }

    static ItemStack deliver(ServerLevel sl, UUID owner, Job job, ItemStack load) {
        IItemHandler h = port(sl, owner, job.target(), load);
        if (h == null) return load;
        return ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, load.copy(), false));
    }

    record Src(BlockPos pos, int kind) {}

    static Src findSource(ServerLevel sl, UUID owner, BlockPos target, Item item, CargoFilter f) {
        java.util.function.Predicate<ItemStack> want = f.of(item);
        Src best = null;
        double bestD = (double) Balance.ROBOT_ZONE * Balance.ROBOT_ZONE;
        if (depotAt(sl, target) == null) for (MissileDepotBlockEntity dp : new ArrayList<>(DEPOTS)) {
            if (dp.isRemoved() || dp.getLevel() != sl || !owner.equals(dp.getOwner())) continue;
            double d = dp.getBlockPos().distSqr(target);
            if (d <= bestD && !dp.takeOne(want, true).isEmpty()) { best = new Src(dp.getBlockPos(), SRC_DEPOT); bestD = d; }
        }
        for (BayModuleBlockEntity core : new ArrayList<>(BAYS)) {
            if (core.isRemoved() || core.getLevel() != sl || !owner.equals(core.getOwner())) continue;
            double d = core.getBlockPos().distSqr(target);
            if (d > bestD) continue;
            if (has(core.out, want)) { best = new Src(core.getBlockPos(), SRC_BAY_OUT); bestD = d; continue; }
            for (int side = 0; side < BayLine.RACKS; side++) {
                BayModuleBlockEntity r = BayLine.rackOf(core, side);
                if (r != null && has(r.rackHandler(), want)) { best = new Src(r.getBlockPos(), SRC_RACK); bestD = d; break; }
            }
        }
        for (LabModuleBlockEntity lab : new ArrayList<>(LABS)) {
            if (lab.isRemoved() || lab.getLevel() != sl || !owner.equals(lab.getOwner())) continue;
            double d = lab.getBlockPos().distSqr(target);
            if (d <= bestD && has(lab.storageOut(), want)) { best = new Src(lab.getBlockPos(), SRC_LAB); bestD = d; }
        }
        for (ItemProcessBlockEntity m : new ArrayList<>(MACHINES)) {
            if (m.isRemoved() || m.getLevel() != sl) continue;
            Owned o = Access.ownerAt(sl, m.getBlockPos());
            if (o != null && !Access.trusts(o, owner)) continue;
            double d = m.getBlockPos().distSqr(target);
            if (d <= bestD && !m.takeOutput(want, true).isEmpty()) { best = new Src(m.getBlockPos(), SRC_MACHINE); bestD = d; }
        }
        return best;
    }

    private static boolean has(IItemHandler h, java.util.function.Predicate<ItemStack> want) {
        for (int i = 0; i < h.getSlots(); i++) if (want.test(h.getStackInSlot(i))) return true;
        return false;
    }

    static List<ItemStack> take(ServerLevel sl, Job job) {
        ItemStack one = takeOne(sl, job);
        if (job.sourceKind() != SRC_CHEST) return one.isEmpty() ? List.of() : List.of(one);
        IItemHandler h = chestAt(sl, job.sourceBlock());
        if (h == null) return List.of();
        java.util.function.Predicate<ItemStack> want = job.filter().of(job.item());
        List<ItemStack> got = new ArrayList<>();
        int left = job.amount();
        for (int i = 0; i < h.getSlots() && left > 0; i++) {
            ItemStack in = h.getStackInSlot(i);
            if (!want.test(in)) continue;
            ItemStack part = h.extractItem(i, Math.min(left, in.getMaxStackSize()), false);
            if (part.isEmpty()) continue;
            left -= part.getCount();
            for (ItemStack g : got) {
                if (part.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(g, part) || g.getCount() >= g.getMaxStackSize()) continue;
                int n = Math.min(part.getCount(), g.getMaxStackSize() - g.getCount());
                g.grow(n);
                part.shrink(n);
            }
            if (!part.isEmpty()) got.add(part);
        }
        return got;
    }

    private static ItemStack takeOne(ServerLevel sl, Job job) {
        java.util.function.Predicate<ItemStack> want = job.filter().of(job.item());
        if (job.sourceKind() == SRC_CHEST || !sl.isLoaded(job.sourceBlock())) return ItemStack.EMPTY;
        BlockEntity be = sl.getBlockEntity(job.sourceBlock());
        if (be instanceof BayModuleBlockEntity b) {
            IItemHandler h = job.sourceKind() == SRC_RACK ? b.rackHandler() : b.out;
            for (int i = 0; i < h.getSlots(); i++) if (want.test(h.getStackInSlot(i))) return h.extractItem(i, 1, false);
            return ItemStack.EMPTY;
        }
        if (be instanceof ItemProcessBlockEntity m) return m.takeOutput(want, false);
        if (be instanceof MissileDepotBlockEntity dp) return dp.takeOne(want, false);
        if (be instanceof LabModuleBlockEntity lab) {
            IItemHandler h = lab.storageOut();
            for (int i = 0; i < h.getSlots(); i++) if (want.test(h.getStackInSlot(i))) return h.extractItem(i, 1, false);
        }
        return ItemStack.EMPTY;
    }

    static ItemStack putBack(ServerLevel sl, Job job, ItemStack load) {
        if (!sl.isLoaded(job.sourceBlock())) return load;
        if (job.sourceKind() == SRC_CHEST) { IItemHandler h = chestAt(sl, job.sourceBlock()); return h == null ? load : ItemHandlerHelper.insertItem(h, load.copy(), false); }
        BlockEntity be = sl.getBlockEntity(job.sourceBlock());
        if (be instanceof BayModuleBlockEntity b) return ItemHandlerHelper.insertItem(job.sourceKind() == SRC_RACK ? b.rackHandler() : b.out, load.copy(), false);
        if (be instanceof MissileDepotBlockEntity dp) return dp.putBack(load.copy());
        if (be instanceof LabModuleBlockEntity lab) return ItemHandlerHelper.insertItem(lab.storageOut(), load.copy(), false);
        if (be instanceof ItemProcessBlockEntity m) return m.returnOutput(load);
        return load;
    }
}
