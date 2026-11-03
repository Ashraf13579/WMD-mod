package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CcPlannerBlockEntity extends CcConsoleBlockEntity {

    public static final int MAP_R = 128, MAX_ROUTES = 64, MAX_BOXES = 96;
    private final List<PlannerRoute> routes = new ArrayList<>();
    private int nextId = 1;

    private final List<PlannerRoutine> routines = new ArrayList<>();
    public static final int MAX_ROUTINES = 16;

    public CcPlannerBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_PLANNER_BE.get(), pos, state, Balance.PLANNER_FE); }

    @Override public CcModule kind() { return CcModule.PLANNER; }

    List<PlannerRoute> routes() { return routes; }

    List<PlannerRoutine> routines() { return routines; }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!level.isClientSide) Logistics.notePlanner(this);
        super.moduleTick(level, now);
    }

    private record Box(BlockEntity be, int role) {}

    private List<BlockPos> chests = List.of();
    private long chestsAt = -1000;

    private Component chestDenied(ServerLevel sl, BlockPos c) {
        if (!inMap(c)) return Component.translatable("message.wmd.planner.no_target");
        return Logistics.chestAllowed(sl, getOwner(), c) ? null : Component.translatable("message.wmd.place.claimed", c.getX(), c.getY(), c.getZ());
    }

    private boolean inMap(BlockPos p) { return Math.abs(p.getX() - worldPosition.getX()) <= MAP_R + 32 && Math.abs(p.getZ() - worldPosition.getZ()) <= MAP_R + 32; }

    private List<Box> boxes(ServerLevel sl, UUID owner) {
        List<Box> l = new ArrayList<>();
        for (LaunchTerminalBlockEntity t : CcLaunch.terminalsOf(owner)) if (t.getLevel() == sl) l.add(new Box(t, 2));
        for (InterceptorSite s : CommandCenter.sitesOf(owner, 64)) if (s instanceof BlockEntity be && be.getLevel() == sl) l.add(new Box(be, 2));
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(owner)) if (g.getLevel() == sl && (g.gun() == Artillery.MORTAR || g.gun() == Artillery.HOWITZER)) l.add(new Box(g, 2));
        for (BayModuleBlockEntity w : Logistics.warheadCellsOf(sl, owner)) l.add(new Box(w, 2));
        for (BayModuleBlockEntity core : Logistics.baysIn(sl)) {
            if (!owner.equals(core.getOwner())) continue;
            for (BayModuleBlockEntity m : BayLine.chain(core)) if (m.kind == BayKind.CRANE) l.add(new Box(m, 2));
        }
        for (BlockEntity be : Logistics.sourcesIn(sl))
            if (be instanceof LabModuleBlockEntity st && owner.equals(st.getOwner()) && LabLine.coreOf(st) instanceof LabModuleBlockEntity core && l.stream().noneMatch(b -> b.be() == core))
                l.add(new Box(core, 2));
        if (sl.getGameTime() - chestsAt >= 200) { chests = Logistics.chestsNear(sl, worldPosition, MAP_R, 48); chestsAt = sl.getGameTime(); }
        for (BlockPos c : chests) if (sl.isLoaded(c) && sl.getBlockEntity(c) != null && Logistics.chestAllowed(sl, owner, c)) l.add(new Box(sl.getBlockEntity(c), 3));
        for (MissileTeleporterBlockEntity t : Logistics.teleportersIn(sl)) if (owner.equals(t.getOwner())) l.add(new Box(t, 2));
        for (BlockEntity be : Logistics.sourcesIn(sl)) {
            if (be instanceof BayModuleBlockEntity b && (b.kind != BayKind.CORE || !owner.equals(b.getOwner()))) continue;
            if (be instanceof BayModuleBlockEntity) { l.add(new Box(be, 3)); continue; }
            if (be instanceof MissileDepotBlockEntity d) { if (owner.equals(d.getOwner())) l.add(new Box(d, 3)); continue; }
            if (be instanceof LabModuleBlockEntity lab && !owner.equals(lab.getOwner())) continue;
            if (be instanceof ItemProcessBlockEntity) { Owned o = Access.ownerAt(sl, be.getBlockPos()); if (o != null && !Access.trusts(o, owner)) continue; }
            l.add(new Box(be, 1));
        }
        l.removeIf(b -> !inMap(b.be().getBlockPos()));
        l.sort(Comparator.comparingDouble(b -> b.be().getBlockPos().distSqr(worldPosition)));
        return l.size() > MAX_BOXES ? new ArrayList<>(l.subList(0, MAX_BOXES)) : l;
    }

    private static int[] ids(List<Item> items) {
        int n = Math.min(64, items.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = BuiltInRegistries.ITEM.getId(items.get(i));
        return out;
    }

    private static int category(BlockEntity be) {
        if (be instanceof BayModuleBlockEntity b) return b.kind == BayKind.CORE ? 3 : 2;
        if (be instanceof LabModuleBlockEntity) return 2;
        if (be instanceof LaunchTerminalBlockEntity || be instanceof InterceptorSite || be instanceof ArtilleryGunBlockEntity || be instanceof MissileDepotBlockEntity
                || be instanceof MissileTeleporterBlockEntity || be instanceof ItemProcessBlockEntity) return 1;
        return 3;
    }

    private static int[] extent(ServerLevel sl, BlockEntity be) {
        List<BlockPos> cells = new ArrayList<>(Logistics.cells(sl, be.getBlockPos()));
        if (be instanceof BayModuleBlockEntity b && b.kind == BayKind.CORE) {
            for (BayModuleBlockEntity m : BayLine.chain(b)) if (m != b) cells.addAll(Logistics.cells(sl, m.getBlockPos()));
            for (int s = 0; s < BayLine.RACKS; s++) { BayModuleBlockEntity r = BayLine.rackOf(b, s); if (r != null) cells.addAll(Logistics.cells(sl, r.getBlockPos())); }
        }
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos c : cells) { x0 = Math.min(x0, c.getX()); z0 = Math.min(z0, c.getZ()); x1 = Math.max(x1, c.getX()); z1 = Math.max(z1, c.getZ()); }
        return new int[] { x0, z0, x1, z1 };
    }

    static List<Item> options(BlockEntity be) {
        List<Item> l = new ArrayList<>();
        if (be instanceof LaunchTerminalBlockEntity t) {
            if (t.getTier() >= 4) { l.add(Wmd.STAGE_CORE.get()); return l; }
            l.add(Wmd.MISSILE_T1.get());
            if (t.getTier() >= 2) l.add(Wmd.MISSILE_T2.get());
            if (t.getTier() >= 3) l.add(Wmd.MISSILE_T3.get());
        } else if (be instanceof BayModuleBlockEntity bm) {

            int[] slots = bm.kind == BayKind.CORE ? new int[] { 0, 1, 2, -1, 3 } : bm.kind == BayKind.CRANE ? new int[] { 1 } : bm.kind == BayKind.WARHEAD ? new int[] { 2, -1, 3 } : new int[0];
            for (int slot : slots) {
                if (slot == -1) { l.add(Wmd.UPLINK_CELL.get()); l.add(Wmd.CAM_POD.get()); l.add(Wmd.LASER_SEEKER.get()); l.add(Wmd.HEAT_SEEKER.get()); l.add(Wmd.ARM_SEEKER.get()); continue; }
                for (var it : Wmd.partItems()) if (it.get().slot() == slot && !l.contains(it.get())) l.add(it.get());
            }
        } else if (be instanceof LabModuleBlockEntity lab && lab.kind() == LabKind.CORE) {
            l.addAll(LAB_MATERIALS.get());
        } else if (be instanceof MissileTeleporterBlockEntity) {
            l.add(Wmd.MISSILE_T1.get());
            l.add(Wmd.MISSILE_T2.get());
            l.add(Wmd.MISSILE_T3.get());
        } else if (be instanceof MissileDepotBlockEntity) {
            l.add(Wmd.MISSILE_T1.get());
            l.add(Wmd.MISSILE_T2.get());
            l.add(Wmd.MISSILE_T3.get());
            l.add(Wmd.STAGE_CORE.get());
            l.add(Wmd.STAGE_UPPER.get());
            l.add(Wmd.STAGE_BOOSTER.get());
        } else if (be instanceof InterceptorSite s) {
            for (Item it : new Item[] { Wmd.INTERCEPTOR_T1.get(), Wmd.INTERCEPTOR_T2.get() }) if (s.magazineHandler().isItemValid(0, new ItemStack(it))) l.add(it);
        } else if (be instanceof ArtilleryGunBlockEntity g) {
            for (int t = 0; t < Artillery.TYPES.length; t++) {
                Item it = Artillery.shellItem(g.gun(), t);
                if (it != null && it != Items.AIR && !l.contains(it)) l.add(it);
            }
        }
        return l;
    }

    private static final java.util.function.Supplier<List<Item>> LAB_MATERIALS = com.google.common.base.Suppliers.memoize(() -> {
        List<Item> l = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) if (it != Items.AIR && LabRecipes.accepts(new ItemStack(it))) l.add(it);
        return l;
    });

    private List<MissileRobotEntity> vehicles(ServerLevel sl, UUID owner) {
        List<MissileRobotEntity> l = new ArrayList<>();
        for (MissileRobotEntity r : Logistics.robotsOf(owner)) if (r.level() == sl) l.add(r);
        l.sort(Comparator.comparingInt((MissileRobotEntity r) -> r.vehicleKind()).thenComparing(r -> r.getUUID()));
        return l;
    }

    private static String label(List<MissileRobotEntity> vs, MissileRobotEntity v) {
        int n = 0;
        for (MissileRobotEntity r : vs) { if (r.vehicleKind() == v.vehicleKind()) n++; if (r == v) break; }
        return (v.vehicleKind() == MissileRobotEntity.KIND_XL ? "X" : v.vehicleKind() == MissileRobotEntity.KIND_RUNNER ? "P" : "R") + n;
    }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        for (Box b : boxes(level, owner)) {
            BlockPos p = b.be().getBlockPos();
            CompoundTag c = new CompoundTag();
            c.putInt("t", 0);
            c.putLong("p", p.asLong());
            c.putString("n", BuiltInRegistries.BLOCK.getKey(b.be().getBlockState().getBlock()).toString());
            c.putInt("x", p.getX());
            c.putInt("z", p.getZ());
            c.putIntArray("b", extent(level, b.be()));
            c.putInt("r", b.role());

            if ((b.role() & 2) != 0) c.putIntArray("opt", ids(options(b.be())));
            if (Logistics.chestAt(level, p) != null) c.putBoolean("any", true);
            if ((b.role() & 1) != 0) c.putIntArray("has", ids(new ArrayList<>(Logistics.stockOf(level, b.be()))));
            c.putInt("cat", category(b.be()));
            out.add(c);
        }
        List<MissileRobotEntity> vs = vehicles(level, owner);
        for (MissileRobotEntity r : vs) {
            if (!inMap(r.blockPosition())) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("t", 1);
            c.putInt("id", r.getId());
            c.putInt("x", (int) Math.floor(r.getX()));
            c.putInt("z", (int) Math.floor(r.getZ()));
            c.putInt("s", r.state());
            c.putInt("b", r.batteryPercent());
            c.putInt("vk", r.vehicleKind());
            c.putInt("k", r.stuck());
            c.putIntArray("line", r.routeLine(48));
            c.putString("lb", label(vs, r));
            c.putBoolean("up", r.upgraded());
            out.add(c);
        }
        for (PlannerRoute r : routes) {
            CompoundTag c = new CompoundTag();
            c.putInt("t", 2);
            c.putInt("id", r.id);
            c.putLong("src", r.src);
            c.putLong("dst", r.dst);
            c.putString("f", BuiltInRegistries.ITEM.getKey(r.item).toString());
            c.putInt("keep", r.keep);
            c.putInt("prio", r.prio);
            c.putBoolean("on", r.on);
            c.putInt("st", Logistics.route(level, this, r, false));
            c.putInt("cond", r.cond);
            c.putBoolean("act", Logistics.active(level, this, r.cond));
            c.putBoolean("flt", CargoFilter.appliesTo(r.item));
            c.putString("fw", r.filter.warhead());
            c.putBoolean("fu", r.filter.uplink());
            c.putBoolean("fr", r.filter.refined());
            String vl = "";
            if (r.vehicle != null) { vl = "?"; for (MissileRobotEntity v : vs) if (r.vehicle.equals(v.getUUID())) vl = label(vs, v); }
            c.putString("vl", vl);
            c.putBoolean("dep", level.isLoaded(BlockPos.of(r.dst)) && level.getBlockEntity(BlockPos.of(r.dst)) instanceof MissileDepotBlockEntity);
            boolean chest = level.isLoaded(BlockPos.of(r.dst)) && Logistics.chestAt(level, BlockPos.of(r.dst)) != null;
            boolean rn = false;
            for (int k : Logistics.kindsFor(r.item)) rn |= k == MissileRobotEntity.KIND_RUNNER;
            c.putBoolean("chest", chest);
            c.putBoolean("rn", rn);
            c.putBoolean("bulk", r.bulk(chest));
            c.putIntArray("also", ids(r.also));
            c.putBoolean("any", r.anyItem);
            c.putInt("mode", r.mode);
            out.add(c);
        }
        for (PlannerRoutine rt : routines) {
            CompoundTag c = new CompoundTag();
            c.putInt("t", 3);
            c.putInt("rid", rt.id);
            MissileRobotEntity v = Logistics.vehicleOf(owner, rt.vehicle);
            boolean here = v != null && v.level() == level;
            c.putInt("veh", here ? v.getId() : -1);
            c.putString("vl", here ? label(vs, v) : "?");
            c.putInt("vk", here ? v.vehicleKind() : -1);
            c.putBoolean("loop", rt.loop);
            c.putBoolean("run", rt.running);
            c.putInt("cond", rt.cond);
            c.putInt("at", rt.at);
            c.putInt("st", rt.state);
            ListTag sl = new ListTag();
            for (PlannerRoutine.Step s : rt.steps) {
                CompoundTag t = new CompoundTag();
                t.putLong("src", s.src());
                t.putLong("dst", s.dst());
                t.putString("f", BuiltInRegistries.ITEM.getKey(s.item()).toString());
                t.putInt("a", s.amt());
                sl.add(t);
            }
            c.put("steps", sl);
            out.add(c);
        }
        return out;
    }

    private PlannerRoutine routineById(long id) {
        for (PlannerRoutine r : routines) if (r.id == id) return r;
        return null;
    }

    private Component routineAct(ServerLevel sl, CcBPayload p) {
        UUID owner = getOwner();
        if (p.x() == 0) {
            MissileRobotEntity v = null;
            for (MissileRobotEntity m : Logistics.robotsOf(owner)) if (m.level() == sl && m.getId() == p.arg()) v = m;
            if (v == null) return null;
            for (PlannerRoutine r : routines) if (r.vehicle.equals(v.getUUID())) return null;
            if (routines.size() >= MAX_ROUTINES) return Component.translatable("message.wmd.planner.rt_full", MAX_ROUTINES);
            routines.add(new PlannerRoutine(nextId++, v.getUUID()));
            setChanged();
            return null;
        }
        PlannerRoutine rt = routineById(p.arg());
        if (rt == null) return null;
        int z = p.z(), n = rt.steps.size();
        switch (p.x()) {
            case 1 -> {
                if (p.pads().size() < 2 || n >= PlannerRoutine.MAX_STEPS) return n >= PlannerRoutine.MAX_STEPS ? Component.translatable("message.wmd.planner.rt_steps", PlannerRoutine.MAX_STEPS) : null;
                BlockPos src = BlockPos.of(p.pads().get(0)), dst = BlockPos.of(p.pads().get(1));
                if (src.equals(dst) || !sl.isLoaded(src) || !sl.isLoaded(dst) || sl.getBlockEntity(dst) == null) return null;
                boolean chest = Logistics.chestAt(sl, dst) != null;
                if (chest) { Component dn = chestDenied(sl, dst); if (dn != null) return dn; }
                if (Logistics.chestAt(sl, src) != null) { Component dn = chestDenied(sl, src); if (dn != null) return dn; }
                Owned o = Access.ownerAt(sl, dst);
                if (!chest && (o == null || !Access.trusts(o, owner))) return Component.translatable("message.wmd.planner.not_yours");
                Item it = BuiltInRegistries.ITEM.byId(z);
                if (it == Items.AIR || !chest && !options(sl.getBlockEntity(dst)).contains(it)) return null;
                MissileRobotEntity v = Logistics.vehicleOf(owner, rt.vehicle);
                if (chest && (v == null || !v.upgraded())) return Component.translatable("message.wmd.planner.rt_up");
                if (v != null) {
                    boolean ok = false;
                    for (int k : Logistics.kindsFor(it)) ok |= k == v.vehicleKind();
                    if (!ok) return Component.translatable("message.wmd.planner.rt_kind");
                }
                rt.steps.add(new PlannerRoutine.Step(src.asLong(), dst.asLong(), it, v != null && v.vehicleKind() == MissileRobotEntity.KIND_RUNNER ? PlannerRoutine.AMT_FILL : PlannerRoutine.AMT_ONE));
            }
            case 2, 3 -> {
                int to = z + (p.x() == 2 ? -1 : 1);
                if (z < 0 || z >= n || to < 0 || to >= n) return null;
                rt.steps.add(to, rt.steps.remove(z));
            }
            case 4 -> {
                if (z < 0 || z >= n) return null;
                rt.steps.remove(z);
                if (rt.steps.isEmpty()) rt.running = false;
                rt.at = rt.steps.isEmpty() ? 0 : Math.floorMod(rt.at, rt.steps.size());
            }
            case 5 -> {
                if (z < 0 || z >= n) return null;
                PlannerRoutine.Step s = rt.steps.get(z);
                rt.steps.set(z, new PlannerRoutine.Step(s.src(), s.dst(), s.item(), (s.amt() + 1) % PlannerRoutine.AMT_N));
            }
            case 6 -> rt.loop = !rt.loop;
            case 7 -> {
                if (rt.steps.isEmpty()) return null;
                rt.running = !rt.running;
                rt.at = 0;
                rt.waits = 0;
                rt.state = rt.running ? PlannerRoutine.RS_BUSY : PlannerRoutine.RS_STOPPED;
            }
            case 8 -> rt.cond = Math.floorMod(rt.cond + (z < 0 ? -1 : 1), Logistics.COND_N);
            case 9 -> routines.remove(rt);
            default -> { return null; }
        }
        setChanged();
        return null;
    }

    private PlannerRoute byId(long id) {
        for (PlannerRoute r : routes) if (r.id == id) return r;
        return null;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (!(level instanceof ServerLevel sl)) return null;
        switch (p.action()) {
            case CcBPayload.PLAN_ADD -> {
                if (p.pads().isEmpty()) return null;
                BlockPos dst = BlockPos.of(p.pads().get(0));
                if (!sl.isLoaded(dst)) return null;
                BlockEntity tbe = sl.getBlockEntity(dst);
                if (Logistics.chestAt(sl, dst) != null) {
                    Component dn = chestDenied(sl, dst);
                    if (dn != null) return dn;
                    long cs = p.arg();
                    if (cs == 0 || cs == dst.asLong() || !sl.isLoaded(BlockPos.of(cs)) || sl.getBlockEntity(BlockPos.of(cs)) == null)
                        return Component.translatable("message.wmd.planner.chest_source");
                    if (Logistics.chestAt(sl, BlockPos.of(cs)) != null) { dn = chestDenied(sl, BlockPos.of(cs)); if (dn != null) return dn; }
                    if (routes.size() >= MAX_ROUTES) return Component.translatable("message.wmd.planner.full", MAX_ROUTES);
                    Item first = null;
                    for (Item it : Logistics.stockOf(sl, sl.getBlockEntity(BlockPos.of(cs)))) {
                        boolean rn = false;
                        for (int k : Logistics.kindsFor(it)) rn |= k == MissileRobotEntity.KIND_RUNNER;
                        if (rn) { first = it; break; }
                    }
                    PlannerRoute nr = new PlannerRoute(nextId++, cs, dst.asLong(), first != null ? first : Items.CHEST);
                    nr.anyItem = first == null;
                    nr.keep = 64;
                    routes.add(nr);
                    setChanged();
                    return null;
                }
                Owned o = Access.ownerAt(sl, dst);
                if (tbe == null || o == null || !Access.trusts(o, getOwner())) return Component.translatable("message.wmd.planner.not_yours");
                List<Item> opts = options(tbe);
                if (opts.isEmpty()) return Component.translatable("message.wmd.planner.no_target");
                if (routes.size() >= MAX_ROUTES) return Component.translatable("message.wmd.planner.full", MAX_ROUTES);
                long src = p.arg();
                if (src != 0 && src == dst.asLong()) return null;
                if (src != 0 && sl.isLoaded(BlockPos.of(src)) && Logistics.chestAt(sl, BlockPos.of(src)) != null) { Component dn = chestDenied(sl, BlockPos.of(src)); if (dn != null) return dn; }
                Item item = opts.get(0);
                if (src != 0 && sl.isLoaded(BlockPos.of(src)))
                    for (Item it : opts) if (Logistics.sourceAt(sl, getOwner(), BlockPos.of(src), it, dst) != null) { item = it; break; }
                routes.add(new PlannerRoute(nextId++, src, dst.asLong(), item));
                setChanged();
                return null;
            }
            case CcBPayload.PLAN_SET -> {
                PlannerRoute r = byId(p.arg());
                if (r == null) return null;
                int d = p.z();
                switch (p.x()) {
                    case 0 -> {
                        BlockPos dst = BlockPos.of(r.dst);
                        if (!sl.isLoaded(dst) || sl.getBlockEntity(dst) == null) return null;
                        List<Item> opts = options(sl.getBlockEntity(dst));
                        if (opts.isEmpty()) return null;
                        int i = Math.max(0, opts.indexOf(r.item));
                        r.item = opts.get(Math.floorMod(i + (d < 0 ? -1 : 1), opts.size()));
                    }
                    case 1 -> {
                        boolean big = r.bulk(sl.isLoaded(BlockPos.of(r.dst)) && Logistics.chestAt(sl, BlockPos.of(r.dst)) != null);
                        int step = big ? Math.max(1, Math.min(64, Math.abs(d))) * Integer.signum(d) : Integer.signum(d);
                        r.keep = Math.max(1, Math.min(big ? Balance.RUNNER_UP_KEEP : PlannerRoute.MAX_KEEP, r.keep + step));
                    }
                    case 2 -> {
                        List<MissileRobotEntity> vs = new ArrayList<>();
                        int[] kinds = Logistics.kindsFor(r.item);
                        for (MissileRobotEntity v : vehicles(sl, getOwner())) for (int k : kinds) if (v.vehicleKind() == k) { vs.add(v); break; }
                        int i = -1;
                        for (int k = 0; k < vs.size(); k++) if (vs.get(k).getUUID().equals(r.vehicle)) i = k;
                        int n = Math.floorMod(i + 1 + (d < 0 ? -1 : 1), vs.size() + 1) - 1;
                        r.vehicle = n < 0 ? null : vs.get(n).getUUID();
                    }
                    case 3 -> r.prio = Math.max(0, Math.min(PlannerRoute.MAX_PRIO, r.prio + d));
                    case 4 -> r.on = !r.on;
                    case 5 -> r.src = 0;
                    case 6 -> {
                        BlockPos dst = BlockPos.of(r.dst);
                        if (!sl.isLoaded(dst) || sl.getBlockEntity(dst) == null) return null;
                        Item it = BuiltInRegistries.ITEM.byId(d);
                        if (options(sl.getBlockEntity(dst)).contains(it) || Logistics.chestAt(sl, dst) != null && it != Items.AIR) { r.item = it; r.also.remove(it); r.anyItem = false; }
                    }
                    case 7 -> r.cond = Math.floorMod(r.cond + (d < 0 ? -1 : 1), Logistics.COND_N);
                    case 8 -> r.filter = r.filter.cycleWarhead(d);
                    case 9 -> r.filter = r.filter.withUplink(!r.filter.uplink());
                    case 10 -> r.filter = r.filter.withRefined(!r.filter.refined());
                    case 11 -> {
                        BlockPos dst = BlockPos.of(r.dst);
                        if (!sl.isLoaded(dst) || sl.getBlockEntity(dst) == null) return null;
                        Item it = BuiltInRegistries.ITEM.byId(d);
                        if (it == Items.AIR || !(options(sl.getBlockEntity(dst)).contains(it) || Logistics.chestAt(sl, dst) != null)) return null;
                        if (it == r.item) { if (!r.also.isEmpty()) r.item = r.also.remove(0); }
                        else if (r.also.contains(it)) r.also.remove(it);
                        else if (r.also.size() < Balance.RUNNER_UP_ITEMS - 1) r.also.add(it);
                        else return Component.translatable("message.wmd.planner.items_full", Balance.RUNNER_UP_ITEMS);
                        r.anyItem = false;
                    }
                    case 12 -> r.anyItem = !r.anyItem;
                    case 13 -> r.mode = r.mode == PlannerRoute.MODE_ALL ? PlannerRoute.MODE_KEEP : PlannerRoute.MODE_ALL;
                    default -> { return null; }
                }
                setChanged();
                return null;
            }
            case CcBPayload.PLAN_DEL -> {
                routes.removeIf(r -> r.id == p.arg());
                setChanged();
                return null;
            }
            case CcBPayload.PLAN_RT -> { return routineAct(sl, p); }
            default -> { return null; }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag l = new ListTag();
        for (PlannerRoute r : routes) l.add(r.write());
        tag.put("planRoutes", l);
        tag.putInt("planNext", nextId);
        ListTag rl = new ListTag();
        for (PlannerRoutine r : routines) rl.add(r.write());
        tag.put("planRoutines", rl);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("planRoutes")) {
            routes.clear();
            ListTag l = tag.getList("planRoutes", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) { PlannerRoute r = PlannerRoute.read(l.getCompound(i)); if (r != null) routes.add(r); }
            nextId = Math.max(tag.getInt("planNext"), 1);
            for (PlannerRoute r : routes) nextId = Math.max(nextId, r.id + 1);
            routines.clear();
            ListTag rl = tag.getList("planRoutines", Tag.TAG_COMPOUND);
            for (int i = 0; i < rl.size(); i++) { PlannerRoutine r = PlannerRoutine.read(rl.getCompound(i)); if (r != null) routines.add(r); }
            for (PlannerRoutine r : routines) nextId = Math.max(nextId, r.id + 1);
        } else if (tag.contains("planRules")) {
            routes.clear();
            ListTag l = tag.getList("planRules", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.getString("item")));
                if (it != Items.AIR) routes.add(new PlannerRoute(nextId++, 0L, c.getLong("p"), it));
            }
        }
    }
}
