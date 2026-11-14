package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class AmmoDepotBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(0, 0, 2, Direction.SOUTH, false).power(1, 0, 2, Direction.EAST, false);

    static final float XS = -1F;
    static final float DOOR_SLIDE = 10.6F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private final SimpleContainer store = new SimpleContainer(Balance.AD_SLOTS) {
        @Override public boolean canPlaceItem(int slot, ItemStack s) { return Ammo.isAmmo(s); }
        @Override public void setChanged() { super.setChanged(); AmmoDepotBlockEntity.this.stored(); }
        @Override public void startOpen(Player p) { AmmoDepotBlockEntity.this.opened(); }
        @Override public boolean stillValid(Player p) { return !isRemoved() && Reach.within(p, worldPosition, 10.0); }
    };
    private final IItemHandler hatch = new IItemHandler() {
        @Override public int getSlots() { return store.getContainerSize(); }
        @Override public ItemStack getStackInSlot(int i) { return store.getItem(i); }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            if (s.isEmpty() || !Ammo.isAmmo(s)) return s;
            ItemStack cur = store.getItem(i);
            int max = Math.min(s.getMaxStackSize(), store.getMaxStackSize());
            if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, s)) return s;
            int room = max - cur.getCount();
            if (room <= 0) return s;
            int n = Math.min(room, s.getCount());
            if (!sim) {
                if (cur.isEmpty()) store.setItem(i, s.copyWithCount(n)); else { cur.grow(n); store.setChanged(); }
            }
            return n >= s.getCount() ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
        }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int i) { return 64; }
        @Override public boolean isItemValid(int i, ItemStack s) { return Ammo.isAmmo(s); }
    };

    private long orderUntil = Long.MIN_VALUE / 2, lastJob = Long.MIN_VALUE / 2, cacheAt = Long.MIN_VALUE / 2;
    private final Set<Long> busy = new HashSet<>(), loaders = new HashSet<>();

    private final Set<Long> failed = new HashSet<>();
    private List<Cand> cache = List.of();

    private record Cand(UUID ship, BlockPos pos, StockTargets.Target t, Vec3 world) {}

    record Job(UUID ship, BlockPos target, ItemStack item, int supply, boolean unload) {}

    static final int MODE_STOCK = 1, MODE_UNLOAD = 2;
    private final Map<UUID, Integer> craftOrder = new HashMap<>();
    private boolean allStock;
    private Map<UUID, Map<Long, ItemStack>> plans = Map.of();

    private final Map<UUID, CompoundTag> fallback = new HashMap<>();

    private long doorsUntil = Long.MIN_VALUE / 2, doorsFrom = Long.MIN_VALUE / 2;
    private int rounds;

    public AmmoDepotBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.AMMO_DEPOT_BE.get(), pos, state, Balance.AD_FE, Balance.AD_BUFFER, Balance.AD_RECEIVE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public SimpleContainer store() { return store; }

    public int rounds() { return rounds; }

    int insertHeld(Player player, ItemStack held) {
        if (!Ammo.isAmmo(held)) return 0;
        int before = held.getCount();
        ItemStack rest = held.copy();
        for (int i = 0; i < store.getContainerSize() && !rest.isEmpty(); i++) rest = hatch.insertItem(i, rest, false);
        int n = before - rest.getCount();
        if (n > 0 && !player.getAbilities().instabuild) held.setCount(rest.getCount());
        if (n > 0) opened();
        return n;
    }

    private void opened() {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        boolean wasOpen = now < doorsUntil;
        if (!wasOpen) {
            doorsFrom = now;
            playOnce(level, WmdSounds.AD_DOORS_OPEN.get(), Balance.SND_MECH, 1.0F);
        }
        doorsUntil = now + Balance.AD_DOOR_TICKS + Balance.AD_DOOR_HOLD;
        sync();
    }

    private void stored() {
        setChanged();
        if (level == null || level.isClientSide) return;
        int n = 0;
        for (int i = 0; i < store.getContainerSize(); i++) n += store.getItem(i).getCount();
        if (n > rounds) playOnce(level, WmdSounds.AD_STOW.get(), Balance.SND_CLICK, 1.0F);
        if (n != rounds) { rounds = n; opened(); }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) AmmoDepots.add(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) AmmoDepots.remove(sl, worldPosition);
        super.setRemoved();
    }

    public boolean ordered() { return level != null && level.getGameTime() < orderUntil; }

    int order() {
        if (!(level instanceof ServerLevel sl)) return 0;
        long now = sl.getGameTime();
        orderUntil = now + Balance.AD_ORDER_TICKS;
        lastJob = now;
        cacheAt = Long.MIN_VALUE / 2;
        allStock = true;
        refresh(sl);
        opened();
        Set<UUID> craft = new HashSet<>();
        for (Cand c : cache) craft.add(c.ship());
        return craft.size();
    }

    void orderCraft(UUID ship, int mode) {
        if (!(level instanceof ServerLevel sl) || ship == null) return;
        long now = sl.getGameTime();
        craftOrder.put(ship, mode);
        orderUntil = Math.max(orderUntil, now + Balance.AD_ORDER_TICKS);
        lastJob = now;
        cacheAt = Long.MIN_VALUE / 2;
        opened();
    }

    int modeFor(UUID ship) {
        if (!ordered()) return 0;
        Integer m = craftOrder.get(ship);
        return m != null ? m : allStock ? MODE_STOCK : 0;
    }

    CompoundTag fallbackLoadout(UUID ship) { return fallback.getOrDefault(ship, new CompoundTag()); }
    void setFallbackLoadout(UUID ship, CompoundTag t) { if (t == null || t.isEmpty()) fallback.remove(ship); else fallback.put(ship, t); setChanged(); }

    List<ItemStack> storeTypes() {
        List<ItemStack> l = new ArrayList<>();
        outer:
        for (int i = 0; i < store.getContainerSize(); i++) {
            ItemStack s = store.getItem(i);
            if (s.isEmpty()) continue;
            for (ItemStack t : l) if (ItemStack.isSameItemSameComponents(t, s)) { t.grow(s.getCount()); continue outer; }
            l.add(s.copy());
        }
        return l;
    }

    private boolean has(ItemStack one) {
        for (int i = 0; i < store.getContainerSize(); i++) if (ItemStack.isSameItemSameComponents(store.getItem(i), one)) return true;
        return false;
    }

    private void refresh(ServerLevel sl) {
        long now = sl.getGameTime();
        if (now - cacheAt < Balance.AD_SCAN_TICKS) return;
        cacheAt = now;
        UUID owner = getOwner();
        if (owner == null) { cache = List.of(); return; }
        Vec3 me = SableBridge.worldPos(sl, worldPosition);
        AABB box = new AABB(me, me).inflate(Balance.AD_RANGE + Balance.LOADER_TRACK_MAX, Balance.AD_RANGE_Y, Balance.AD_RANGE + Balance.LOADER_TRACK_MAX);
        List<Cand> l = new ArrayList<>();
        Map<UUID, Map<Long, ItemStack>> pl = new HashMap<>();
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) {
            List<StockTargets.Target> ts = StockTargets.of(sl, s.id(), owner);
            for (StockTargets.Target t : ts) l.add(new Cand(s.id(), t.pos(), t, SableBridge.worldPos(sl, t.pos())));
            CompoundTag lo = LoadoutPlan.read(sl, s.id(), this);
            if (!LoadoutPlan.hasPlan(lo)) continue;
            Map<Long, ItemStack> plan = LoadoutPlan.plan(lo, sl.registryAccess());
            if (stalePlan(plan, ts)) {
                LoadoutPlan.clearPlan(lo);
                lo.remove("active");
                LoadoutPlan.write(sl, s.id(), this, lo);
                continue;
            }
            pl.put(s.id(), plan);
        }
        cache = l;
        plans = pl;
    }

    static boolean stalePlan(Map<Long, ItemStack> plan, List<StockTargets.Target> stations) {
        if (plan.isEmpty() || stations.isEmpty()) return false;
        for (StockTargets.Target t : stations) if (plan.containsKey(t.pos().asLong())) return false;
        return true;
    }

    Job claim(ServerLevel sl, OrdnanceLoaderBlockEntity ld) {
        if (!AirGate.on() || !ordered() || getOwner() == null || !getOwner().equals(ld.getOwner())) return null;
        long key = ld.getBlockPos().asLong();
        if (!loaders.contains(key) && loaders.size() >= Balance.AD_MAX_LOADERS) return null;
        loaders.add(key);
        refresh(sl);
        Vec3 lw = SableBridge.worldPos(sl, ld.getBlockPos());
        List<Cand> l = new ArrayList<>(cache);
        l.sort(Comparator.comparingDouble(c -> c.world().distanceToSqr(lw)));
        java.util.Map<String, Integer> why = new java.util.TreeMap<>();
        for (Cand c : l) {
            int mode = modeFor(c.ship());
            if (mode == 0) { why.merge("no order on craft", 1, Integer::sum); continue; }
            if (busy.contains(c.pos().asLong())) { why.merge("busy", 1, Integer::sum); continue; }
            if (AirPicture.isAircraft(sl, c.ship())) { why.merge("craft counts as airborne", 1, Integer::sum); continue; }
            if (!ld.parked(sl, c.ship())) { why.merge("craft not parked", 1, Integer::sum); continue; }
            if (failed.contains(c.pos().asLong())) { why.merge("refused before", 1, Integer::sum); continue; }
            if (sl.getBlockEntity(c.pos()) == null) { why.merge("station gone", 1, Integer::sum); continue; }
            StockTargets.Target t = c.t();
            ItemStack cur = t.current();
            boolean unload;
            ItemStack one;
            if (mode == MODE_UNLOAD) {
                if (!t.unloadable() || cur.isEmpty()) { why.merge("nothing to unload", 1, Integer::sum); continue; }
                unload = true; one = ItemStack.EMPTY;
            } else {
                Map<Long, ItemStack> plan = plans.get(c.ship());
                ItemStack want = plan == null ? null : plan.get(c.pos().asLong());
                if (plan != null && want == null) { why.merge("no plan entry", 1, Integer::sum); continue; }
                if (want != null && !cur.isEmpty() && !ItemStack.isSameItem(cur, want) && t.unloadable()) {
                    unload = true; one = ItemStack.EMPTY;
                } else {
                    unload = false;
                    one = want == null ? t.pick(store) : (StockTargets.room(t, want) && has(want) ? want.copyWithCount(1) : ItemStack.EMPTY);
                    if (one.isEmpty()) { why.merge(want == null ? "full / no round fits" : "depot has no " + want.getHoverName().getString(), 1, Integer::sum); continue; }
                }
            }
            if (!Ammo.carries(ld.heavy(), unload ? cur : one)) { why.merge((ld.heavy() ? "heavy" : "light") + " loader cannot carry " + (unload ? cur : one).getHoverName().getString(), 1, Integer::sum); continue; }
            if (!ld.canServe(sl, c.pos())) { why.merge("out of reach: " + ld.reachWhy(sl, c.pos()), 1, Integer::sum); continue; }

            if (!unload && (one = takeUpTo(one, t.roomFor(one))).isEmpty()) { why.merge("take failed", 1, Integer::sum); continue; }
            busy.add(c.pos().asLong());
            long now = sl.getGameTime();
            lastJob = now;
            orderUntil = Math.max(orderUntil, now + Balance.AD_ORDER_TICKS);
            double dist = lw.distanceTo(SableBridge.worldPos(sl, worldPosition));
            return new Job(c.ship(), c.pos(), one, unload ? 0 : Balance.LOADER_SUPPLY_BASE + (int) (dist / Balance.LOADER_SUPPLY_SPEED), unload);
        }
        Long lw0 = WHY_AT.get(key);
        if (!why.isEmpty() && (lw0 == null || sl.getGameTime() - lw0 >= 200)) {
            WHY_AT.put(key, sl.getGameTime());
            Wmd.LOGGER.info("[loader] {} loader at {}: no job ({} stations seen): {}", ld.heavy() ? "heavy" : "light", ld.getBlockPos().toShortString(), l.size(), why);
        }
        return null;
    }

    private final java.util.Map<Long, Long> WHY_AT = new java.util.HashMap<>();

    boolean busyAt(BlockPos target) { return busy.contains(target.asLong()); }

    void done(BlockPos target) { busy.remove(target.asLong()); }

    void done(BlockPos target, boolean ok) { done(target); if (!ok) failed.add(target.asLong()); }

    boolean giveBack(ItemStack s) {
        ItemStack rest = s.copy();
        for (int i = 0; i < store.getContainerSize() && !rest.isEmpty(); i++) rest = hatch.insertItem(i, rest, false);
        if (!rest.isEmpty()) { s.setCount(rest.getCount()); return false; }
        return true;
    }

    private ItemStack takeUpTo(ItemStack one, int n) {
        int want = Math.max(1, Math.min(n, one.getMaxStackSize())), got = 0;
        for (int i = 0; i < store.getContainerSize() && got < want; i++) {
            ItemStack it = store.getItem(i);
            if (it.isEmpty() || !ItemStack.isSameItemSameComponents(it, one)) continue;
            int k = Math.min(want - got, it.getCount());
            it.shrink(k);
            got += k;
        }
        if (got > 0) store.setChanged();
        return got > 0 ? one.copyWithCount(got) : ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        for (int i = 0; i < store.getContainerSize(); i++) {
            ItemStack s = store.removeItemNoUpdate(i);
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    @Override
    protected boolean work() {
        if (level == null || !AirGate.on()) return false;
        long now = level.getGameTime();
        if (now == doorsUntil) playOnce(level, WmdSounds.AD_DOORS_CLOSE.get(), Balance.SND_MECH, 1.0F);
        if (ordered() && busy.isEmpty() && now - lastJob > Balance.AD_ORDER_IDLE) orderUntil = Long.MIN_VALUE / 2;
        if (!ordered() && (!busy.isEmpty() || !loaders.isEmpty() || !failed.isEmpty() || !craftOrder.isEmpty() || allStock)) {
            busy.clear(); loaders.clear(); failed.clear(); craftOrder.clear(); allStock = false;
        }
        if (ordered() && now < doorsUntil + 2) doorsUntil = now + Balance.AD_DOOR_TICKS + Balance.AD_DOOR_HOLD;
        return now < doorsUntil && getOwner() != null;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double open = doorOpen(tt);
        final float dx = (float) (XS * DOOR_SLIDE * open);
        model.getBone("door_l").ifPresent(b -> b.setPosX(-dx));
        model.getBone("door_r").ifPresent(b -> b.setPosX(dx));
        final float f = spinAngle(pt) * Balance.AD_FAN_RAD / 0.9F;
        model.getBone("fan").ifPresent(b -> b.setRotY(f));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean powered = guiEnergy().getEnergyStored() > 0;
        final boolean ready = !dark && getOwner() != null && powered;
        final boolean warn = !dark && !ready;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready));
        model.getBone("lamp_a").ifPresent(b -> b.setHidden(!warn));
    }

    private double doorOpen(double tt) {
        double d = Balance.AD_DOOR_TICKS;
        if (tt < doorsFrom) return 0.0;
        double o = Math.min(1.0, (tt - doorsFrom) / d);
        if (tt > doorsUntil - d) o = Math.min(o, Math.max(0.0, (doorsUntil - tt) / d));
        return o * o * (3.0 - 2.0 * o);
    }

    @Override public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? hatch : null; }
    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 2, Direction.EAST) ? energy() : null; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.AD_VENT_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected String detail() { return rounds + " rounds, doors " + (level != null && level.getGameTime() < doorsUntil ? "open" : "shut"); }

    @Override
    public Component status() {
        if (getOwner() == null) return Component.translatable("message.wmd.ammo_depot.unowned");
        return Component.translatable("message.wmd.ammo_depot.status", rounds, usedSlots(), Balance.AD_SLOTS);
    }

    private int usedSlots() {
        int n = 0;
        for (int i = 0; i < store.getContainerSize(); i++) if (!store.getItem(i).isEmpty()) n++;
        return n;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) items.set(i, store.getItem(i));
        CompoundTag inv = new CompoundTag();
        ContainerHelper.saveAllItems(inv, items, registries);
        tag.put("adStore", inv);
        ListTag fb = new ListTag();
        for (Map.Entry<UUID, CompoundTag> e : fallback.entrySet()) { CompoundTag c = new CompoundTag(); c.putUUID("id", e.getKey()); c.put("lo", e.getValue()); fb.add(c); }
        if (!fb.isEmpty()) tag.put("adLoadouts", fb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("adStore")) {
            NonNullList<ItemStack> items = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("adStore"), items, registries);
            for (int i = 0; i < items.size(); i++) store.getItems().set(i, items.get(i));
            int n = 0;
            for (ItemStack s : items) n += s.getCount();
            rounds = n;
        }
        if (tag.contains("adLoadouts")) {
            fallback.clear();
            ListTag fb = tag.getList("adLoadouts", Tag.TAG_COMPOUND);
            for (int i = 0; i < fb.size(); i++) fallback.put(fb.getCompound(i).getUUID("id"), fb.getCompound(i).getCompound("lo"));
        }
        if (tag.contains("adDoorTo")) { doorsUntil = tag.getLong("adDoorTo"); doorsFrom = tag.getLong("adDoorFrom"); rounds = tag.getInt("adRounds"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putLong("adDoorTo", doorsUntil);
        tag.putLong("adDoorFrom", doorsFrom);
        tag.putInt("adRounds", rounds);
        return tag;
    }
}
