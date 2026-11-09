package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class NavalReactorBlockEntity extends ProcessUnitBlockEntity implements GuiItemSlots, NavalPanel, Owned, CcAnimated {

    public static final int SCRAM_ROW = 0;
    private static final PortMap PORT_MAP = new PortMap(0, 2, 3).item(0, 0, 0, Direction.NORTH, false).item(2, 0, 0, Direction.NORTH, true)
            .fluid(0, 0, 1, Direction.WEST, "water", false).power(1, 0, 2, Direction.SOUTH, true);

    private final OwnerData ownerData = new OwnerData();
    private ItemStack fuel = ItemStack.EMPTY, spent = ItemStack.EMPTY;
    private double fuelLeft;
    private int lvl;
    private boolean scram;
    private double heat;
    private int decayLeft;
    private boolean damaged, meltdownPending, melted;
    private int stored;
    private double waterAcc;
    private boolean sea, cooled;
    private boolean cracked;
    private float shownWater = -1.0F;
    private int pctNow;
    private int seaCheck;
    private final FluidTank water = new FluidTank(Balance.NR_WATER_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final FluidTank fuelGauge = new FluidTank(Balance.NR_FUEL_SLOTS), spentGauge = new FluidTank(Balance.NR_SPENT_SLOTS);
    private final IFluidHandler waterIn = fillOnly(water);
    private int syncKey = Integer.MIN_VALUE;

    private int shownHeat;

    private final IEnergyStorage out = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, stored), Balance.NR_OUT_PER_TICK);
            if (t > 0 && !sim) { stored -= t; setChanged(); }
            return t;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.NR_BUFFER_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    private final IItemHandler fuelHatch = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int i) { return fuel; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return insertFuel(s, sim); }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int i) { return Balance.NR_FUEL_SLOTS; }
        @Override public boolean isItemValid(int i, ItemStack s) { return s.is(rod()); }
    };

    private final IItemHandler spentHatch = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int i) { return spent; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
        @Override public ItemStack extractItem(int i, int n, boolean sim) {
            if (spent.isEmpty() || n <= 0) return ItemStack.EMPTY;
            ItemStack r = spent.copyWithCount(Math.min(n, spent.getCount()));
            if (!sim) { spent.shrink(r.getCount()); if (spent.isEmpty()) spent = ItemStack.EMPTY; syncGauges(); setChanged(); }
            return r;
        }
        @Override public int getSlotLimit(int i) { return Balance.NR_SPENT_SLOTS; }
        @Override public boolean isItemValid(int i, ItemStack s) { return false; }
    };

    public NavalReactorBlockEntity(BlockPos pos, BlockState state) { super(Wmd.NAVAL_REACTOR_BE.get(), pos, state, 0); }

    static Item rod() { return wmdItem("fuel_rod"); }
    static Item spentRod() { return wmdItem("spent_fuel_rod"); }
    private static Item wmdItem(String id) { return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private ItemStack insertFuel(ItemStack s, boolean sim) {
        if (s.isEmpty() || !s.is(rod())) return s;
        int n = Math.min(Balance.NR_FUEL_SLOTS - fuel.getCount(), s.getCount());
        if (n <= 0) return s;
        if (!sim) {
            if (fuel.isEmpty()) fuel = s.copyWithCount(n); else fuel.grow(n);
            syncGauges();
            setChanged();
        }
        return s.getCount() == n ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
    }

    public boolean tryInsertFuel(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.is(rod())) return false;
        if (getOwner() != null && !Access.mayUse(this, player)) return false;
        ItemStack rest = insertFuel(held, false);
        if (rest.getCount() == held.getCount()) return false;
        if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
        return true;
    }

    static final java.util.Set<NavalReactorBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) LOADED.remove(this);
    }

    public static void blastAt(ServerLevel sl, Vec3 pos, double r) {
        for (NavalReactorBlockEntity be : new ArrayList<>(LOADED)) {
            if (be.level != sl || be.isRemoved() || be.cracked || be.melted) continue;
            double[] c = be.worldPoint(1.0, 1.8, 1.0);
            double d = Math.sqrt((c[0] - pos.x) * (c[0] - pos.x) + (c[1] - pos.y) * (c[1] - pos.y) + (c[2] - pos.z) * (c[2] - pos.z));
            if (d > r + Balance.NR_CRACK_REACH) continue;
            be.crack(sl);
        }
    }

    void crack(ServerLevel sl) {
        if (cracked) return;
        cracked = true;
        BlockPos c = corePos(sl);
        sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.7F);
        sl.playSound(null, worldPosition, WmdSounds.NR_STEAM.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.2F);
        tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable("message.wmd.naval_reactor.cracked", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.GOLD));
        Wmd.LOGGER.info("[naval reactor] tank at {} cracked", worldPosition.toShortString());
        setChanged();
        sync();
    }

    public boolean tryRepair(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(damaged || cracked) || held.isEmpty() || !held.is(wmdItem("reactor_repair_kit"))) return false;
        if (getOwner() != null && !Access.mayUse(this, player)) return false;
        if (!damaged) {
            cracked = false;
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.displayClientMessage(Component.translatable("message.wmd.naval_reactor.sealed"), true);
            setChanged();
            sync();
            return true;
        }
        damaged = false;
        cracked = false;
        heat = Math.min(heat, Balance.NR_HEAT_ALARM - 1.0);
        lvl = 0;
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.displayClientMessage(Component.translatable("message.wmd.naval_reactor.repaired"), true);
        setChanged();
        sync();
        return true;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        if (!fuel.isEmpty()) l.add(fuel);
        if (!spent.isEmpty()) l.add(spent);
        fuel = ItemStack.EMPTY;
        spent = ItemStack.EMPTY;
        syncGauges();
        return l;
    }

    private void syncGauges() {
        fuelGauge.setFluid(fuel.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, fuel.getCount()));
        spentGauge.setFluid(spent.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, spent.getCount()));
    }

    private boolean spentRoom() { return spent.isEmpty() || spent.getCount() < Balance.NR_SPENT_SLOTS; }

    public static void serverTickReactor(Level lv, BlockPos pos, BlockState state, NavalReactorBlockEntity be) {
        ProcessUnitBlockEntity.serverTick(lv, pos, state, be);
        if (be.meltdownPending && !be.melted && lv instanceof ServerLevel sl) be.meltdown(sl);
    }

    public static int pctOf(int level) { return Balance.NR_LEVELS[Mth.clamp(level, 0, Balance.NR_LEVELS.length - 1)]; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        int want = damaged || scram ? 0 : pctOf(lvl);

        if (want > 0 && fuelLeft <= 0.0) {
            if (!fuel.isEmpty() && spentRoom()) {
                fuel.shrink(1);
                if (fuel.isEmpty()) fuel = ItemStack.EMPTY;
                fuelLeft = Balance.NR_FUEL_TICKS;
                syncGauges();
                sl.playSound(null, worldPosition, WmdSounds.NR_RODS.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.1F);
                setChanged();
            } else want = 0;
        }

        int room = Balance.NR_BUFFER_FE - stored;
        double share = want <= 0 ? 0.0 : Math.min(1.0, room / (Balance.NR_FE_PER_TICK * want / 100.0));
        double eff = want * Math.max(0.0, share);
        pctNow = (int) Math.round(eff);

        sea = false;
        waterAcc += (eff > 0 ? Balance.NR_WATER_MB * eff / 100.0 : (now % 20 == 0 ? 1.0 : 0.0)) + (cracked ? Balance.NR_LEAK_MB : 0.0);
        int need = (int) Math.floor(waterAcc);
        if (need > 0) {
            water.drain(Math.min(need, water.getFluidAmount()), IFluidHandler.FluidAction.EXECUTE);
            waterAcc -= need;
        }
        cooled = water.getFluidAmount() * 100 >= Balance.NR_WATER_TANK_MB * Balance.NR_TANK_COOL_PCT;

        if (cooled) {
            double target = Balance.NR_HEAT_OPERATING * eff / 100.0;
            heat += Mth.clamp(target - heat, -Balance.NR_HEAT_FALL, Balance.NR_HEAT_FALL);
        } else {
            heat += Balance.NR_HEAT_RISE * eff / 100.0 + (decayLeft > 0 ? Balance.NR_DECAY_RISE : 0.0);
        }
        if (decayLeft > 0) decayLeft--;
        heat = Mth.clamp(heat, 0.0, Balance.NR_HEAT_MAX);

        if (eff > 0.0) {
            fuelLeft -= eff / 100.0;
            stored = Math.min(Balance.NR_BUFFER_FE, stored + (int) Math.round(Balance.NR_FE_PER_TICK * eff / 100.0));
            flowAcc += (int) Math.round(Balance.NR_FE_PER_TICK * eff / 100.0);
            if (fuelLeft <= 0.0) {
                fuelLeft = 0.0;
                if (spent.isEmpty()) spent = new ItemStack(spentRod()); else spent.grow(1);
                syncGauges();
                setChanged();
            }
        }

        if (stored > 0) {
            Direction fd = getBlockState().getValue(MachineBlock.FACING), md = Machines.mount(getBlockState());
            BlockPos flange = Machines.toWorld(worldPosition, fd, md, 2, 1, 1);
            Direction east = Machines.dirToWorld(fd, md, Direction.EAST);
            IEnergyStorage onFlange = sl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, flange.relative(east), east.getOpposite());
            if (onFlange != null && onFlange.canReceive()) {
                int given = onFlange.receiveEnergy(Math.min(stored, Balance.NR_OUT_PER_TICK), false);
                if (given > 0) { stored -= given; setChanged(); }
            }
        }

        if (now % 20 == 3) {
            if (eff > Balance.NR_QUIET_PCT) {
                double db = Balance.NR_PUMP_DB + (Balance.NR_PUMP_DB_FULL - Balance.NR_PUMP_DB) * Mth.clamp((eff - Balance.NR_QUIET_PCT) / (100.0 - Balance.NR_QUIET_PCT), 0.0, 1.0);
                NoiseNet.emit(sl, this, SableBridge.worldPos(sl, worldPosition.getX() + 1.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 1.5), db,
                        NoiseNet.Kind.MACHINE, getOwner(), SableBridge.shipIdAt(sl, worldPosition));
            } else NoiseNet.silence(sl, this);
        }

        if (heat >= Balance.NR_HEAT_SCRAM && !scram && !damaged && want > 0) autoScram(sl);
        if (heat >= Balance.NR_HEAT_MAX && !damaged && !meltdownPending) overheat(sl);
        warnings(sl, now);
        if (cracked && water.getFluidAmount() > 0 && now % 50 == 25) sl.playSound(null, worldPosition, WmdSounds.NR_STEAM.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.25F + sl.random.nextFloat() * 0.15F);
        sync();
        return eff > 0.0;
    }

    private boolean touchingWater(ServerLevel sl) {
        if (!(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        Direction f = facing(), mo = Machines.mount(getBlockState());
        java.util.Set<BlockPos> mine = new java.util.HashSet<>();
        for (BlockPos l : mb.footprint()) mine.add(Machines.toWorld(worldPosition, f, mo, l.getX(), l.getY(), l.getZ()));
        for (BlockPos c : mine) {
            if (sl.getFluidState(c).is(FluidTags.WATER)) return true;
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (!mine.contains(n) && sl.getFluidState(n).is(FluidTags.WATER)) return true;
            }
        }
        if (SableBridge.isOnShip(sl, worldPosition)) {
            Vec3 w = SableBridge.worldPos(sl, worldPosition.getX() + 1.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 1.5);
            return sl.getFluidState(BlockPos.containing(w)).is(FluidTags.WATER);
        }
        return false;
    }

    private void autoScram(ServerLevel sl) {
        scram = true;
        decayLeft = Balance.NR_DECAY_TICKS;
        BlockPos c = corePos(sl);
        sl.playSound(null, worldPosition, WmdSounds.NR_RODS.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable("message.wmd.naval_reactor.scram", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.RED));
        setChanged();
    }

    private void overheat(ServerLevel sl) {
        if (Config.reactorMeltdown()) { meltdownPending = true; return; }
        damaged = true;
        scram = true;
        lvl = 0;
        BlockPos c = corePos(sl);
        sl.playSound(null, worldPosition, WmdSounds.NR_DAMAGE.get(), SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
        tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable("message.wmd.naval_reactor.damaged", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.RED));
        setChanged();
    }

    private void meltdown(ServerLevel sl) {
        if (melted || isRemoved()) return;
        melted = true;
        meltdownPending = false;
        BlockPos c = corePos(sl);
        int gx = c.getX(), gy = c.getY(), gz = c.getZ();
        tell(sl, Balance.REACTOR_WARN_RADIUS * 4, Component.translatable("message.wmd.naval_reactor.meltdown", gx, gy, gz).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        MusicTriggersHook.fire(sl, "wmd:reactor_meltdown", worldPosition);
        fuel = ItemStack.EMPTY;
        spent = ItemStack.EMPTY;
        water.setFluid(FluidStack.EMPTY);
        sl.removeBlock(worldPosition, false);
        FlightManager mgr = FlightManager.get(sl);
        int tier = Balance.NR_MELTDOWN_TIER;
        mgr.holdForTest(sl, gx, gz, WarheadFx.holdRadius(tier, WarheadType.NUKE));
        Forcefield.inside(() -> WarheadFx.detonate(sl, mgr, gx, gy, gz, tier, WarheadType.NUKE, 0.0, 1.0));
    }

    private void warnings(ServerLevel sl, long now) {
        if (heat >= Balance.NR_HEAT_ALARM && now % 40 == 0) sl.playSound(null, worldPosition, WmdSounds.NR_ALARM.get(), SoundSource.BLOCKS, Balance.SND_ALARM, heat >= Balance.NR_HEAT_STEAM ? 1.15F : 1.0F);
        if (heat >= Balance.NR_HEAT_STEAM && now % 40 == 20) sl.playSound(null, worldPosition, WmdSounds.NR_STEAM.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.9F + sl.random.nextFloat() * 0.2F);
        if (heat >= Balance.NR_HEAT_SCRAM + 50.0 && now % 60 == 0) sl.playSound(null, worldPosition, WmdSounds.SIREN.get(), SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
    }

    public BlockPos corePos(Level lv) {
        double[] c = worldPoint(1.0, 1.5, 1.0);
        return BlockPos.containing(c[0], c[1], c[2]);
    }

    private void tell(ServerLevel sl, int r, Component msg) {
        Vec3 c = Vec3.atCenterOf(corePos(sl));
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(c.x, c.y, c.z) <= (double) r * r) p.sendSystemMessage(msg);
    }

    @Override public String panelTitle() { return "gui.wmd.naval_reactor.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.naval_reactor.power", pctNow, pctNow * Balance.NR_FE_PER_TICK / 100 * 20));
        l.add(Component.translatable("gui.wmd.naval_reactor.heat", shownHeat, (int) Balance.NR_HEAT_MAX));
        l.add(Component.translatable("gui.wmd.naval_reactor.coolant", water.getFluidAmount() * 100 / Balance.NR_WATER_TANK_MB, Balance.NR_TANK_COOL_PCT));
        l.add(Component.translatable(cracked ? "gui.wmd.naval_reactor.cracked" : sea ? "gui.wmd.naval_reactor.cool_sea" : cooled ? "gui.wmd.naval_reactor.cool_pipe" : "gui.wmd.naval_reactor.cool_none"));
        l.add(Component.translatable(pctNow > Balance.NR_QUIET_PCT ? "gui.wmd.naval_reactor.pumps_on" : "gui.wmd.naval_reactor.pumps_off"));
        l.add(Component.translatable("gui.wmd.naval_reactor.feed", (int) Math.round(Balance.NR_WATER_MB * Math.max(pctNow, 1) / 100.0 * 20.0)));
        l.add(Component.translatable("gui.wmd.naval_reactor.rod", (int) Math.round(100.0 * fuelLeft / Balance.NR_FUEL_TICKS)));

        double ticks = fuelLeft + (double) fuel.getCount() * Balance.NR_FUEL_TICKS;
        int at = pctNow > 0 ? pctNow : 100;
        l.add(Component.translatable("gui.wmd.naval_reactor.fuel_time", fuelTime(ticks * 100.0 / at), at));
        return l;
    }

    static Component fuelTime(double ticks) {
        long min = (long) Math.floor(ticks / 1200.0);
        if (ticks <= 0.0) return Component.translatable("gui.wmd.naval_reactor.time_none");
        if (min < 1) return Component.translatable("gui.wmd.naval_reactor.time_lt1");
        return min >= 60 ? Component.translatable("gui.wmd.naval_reactor.time_hm", min / 60, min % 60) : Component.translatable("gui.wmd.naval_reactor.time_m", min);
    }

    @Override
    public List<Component> panelRows() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.naval_reactor.row_scram"));
        l.add(Component.translatable("gui.wmd.naval_reactor.row_quiet", Balance.NR_LEVELS[1]));
        for (int i = 2; i < Balance.NR_LEVELS.length; i++) l.add(Component.translatable("gui.wmd.naval_reactor.row_level", Balance.NR_LEVELS[i]));
        return l;
    }

    @Override public int panelPicked() { return damaged || scram ? SCRAM_ROW : lvl; }
    @Override public boolean panelDanger(int row) { return row == SCRAM_ROW; }
    @Override public boolean panelEnabled(int row) { return !damaged || row == SCRAM_ROW; }

    @Override
    public int panelChipKind() {
        if (damaged || heat >= Balance.NR_HEAT_SCRAM) return WmdUi.K_BAD;
        if (heat >= Balance.NR_HEAT_ALARM || !cooled || cracked) return WmdUi.K_WARN;
        return pctNow > 0 ? WmdUi.K_GOOD : WmdUi.K_IDLE;
    }

    @Override
    public Component panelChip() {
        return Component.translatable(damaged ? "gui.wmd.naval_reactor.chip_damaged" : scram ? "gui.wmd.naval_reactor.chip_scram"
                : pctNow > 0 && pctNow <= Balance.NR_QUIET_PCT ? "gui.wmd.naval_reactor.chip_quiet" : pctNow > 0 ? "gui.wmd.naval_reactor.chip_on" : "gui.wmd.naval_reactor.chip_off");
    }

    @Override
    public void setMode(int row) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (row == SCRAM_ROW) {
            if (!scram && pctOf(lvl) > 0) decayLeft = (int) (Balance.NR_DECAY_TICKS * Math.min(1.0, heat / Balance.NR_HEAT_MAX));
            scram = true;
            lvl = 0;
        } else if (!damaged) {
            lvl = Mth.clamp(row, 0, Balance.NR_LEVELS.length - 1);
            scram = false;
        }
        sl.playSound(null, worldPosition, WmdSounds.NR_RODS.get(), SoundSource.BLOCKS, Balance.SND_MECH, row == SCRAM_ROW ? 0.8F : 1.0F);
        setChanged();
        syncKey = Integer.MIN_VALUE;
        sync();
    }

    @Override public int slotCount() { return 2; }
    @Override public int firstSlotTank() { return 1; }
    @Override public int slotMax(int i) { return i == 0 ? Balance.NR_FUEL_SLOTS : Balance.NR_SPENT_SLOTS; }

    @Override
    public net.minecraft.world.Container slotContainer() {
        return new net.minecraft.world.Container() {
            @Override public int getContainerSize() { return 2; }
            @Override public boolean isEmpty() { return fuel.isEmpty() && spent.isEmpty(); }
            @Override public ItemStack getItem(int i) { return i == 0 ? fuel : spent; }
            @Override public ItemStack removeItem(int i, int n) {
                ItemStack s = i == 0 ? fuel : spent;
                if (s.isEmpty()) return ItemStack.EMPTY;
                ItemStack r = s.split(n);
                if (i == 0 && fuel.isEmpty()) fuel = ItemStack.EMPTY;
                if (i == 1 && spent.isEmpty()) spent = ItemStack.EMPTY;
                syncGauges(); NavalReactorBlockEntity.this.setChanged();
                return r;
            }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = i == 0 ? fuel : spent; if (i == 0) fuel = ItemStack.EMPTY; else spent = ItemStack.EMPTY; syncGauges(); return r; }
            @Override public void setItem(int i, ItemStack st) { if (i == 0) fuel = st; else spent = st; syncGauges(); NavalReactorBlockEntity.this.setChanged(); }
            @Override public int getMaxStackSize() { return Balance.NR_FUEL_SLOTS; }
            @Override public void setChanged() { NavalReactorBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return i == 0 && st.is(rod()); }
            @Override public void clearContent() { fuel = ItemStack.EMPTY; spent = ItemStack.EMPTY; syncGauges(); }
        };
    }

    @Override protected List<FluidTank> tanks() { return List.of(water, fuelGauge, spentGauge); }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "fuel_rods", "spent_fuel"); }
    @Override protected int inputTanks() { return 2; }
    @Override protected int rateMbPerTick() { return Balance.NR_FE_PER_TICK; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(ItemStack.EMPTY, new ItemStack(rod()), new ItemStack(spentRod())); }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gen_output", Balance.NR_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.naval_reactor", Balance.NR_FUEL_TICKS / 72_000, Balance.NR_QUIET_PCT),
                Component.translatable("gui.wmd.info.naval_reactor_cool"));
    }

    @Override
    public int guiStatus() {
        if (pctNow > 0) return 1;
        if (pctOf(lvl) > 0 && !scram && fuel.isEmpty() && fuelLeft <= 0.0) return 4;
        return 0;
    }

    @Override public void empDrain() { stored = 0; setChanged(); }

    @Override protected SoundEvent runSound() { return WmdSounds.NR_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(1, 1, 1); }
    @Override protected float soundVolume() { return pctNow > Balance.NR_QUIET_PCT ? Balance.SND_HEAVY : Balance.SND_AMBIENT; }

    @Override
    protected String detail() {
        return "level " + pctOf(lvl) + "% (now " + pctNow + "%), heat " + (int) heat + ", fuel " + fuel.getCount() + " (+" + (int) Math.round(100.0 * fuelLeft / Balance.NR_FUEL_TICKS)
                + "% in core), spent " + spent.getCount() + ", water " + mb(water) + ", " + (sea ? "SEA" : cooled ? "piped" : "NOT COOLED") + (scram ? ", SCRAM" : "") + (damaged ? ", DAMAGED" : "")
                + ", stored " + stored + " FE";
    }

    @Override

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 2, Direction.SOUTH) || at(part, side, 2, 1, 1, Direction.EAST) ? out : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.NORTH)) return fuelHatch;
        if (at(part, side, 2, 0, 0, Direction.NORTH)) return spentHatch;
        return null;
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.WEST) ? waterIn : null; }

    public int pctShown() { return pctNow; }
    public int heatShown() { return shownHeat; }
    public boolean damagedShown() { return damaged; }

    public boolean crackedShown() { return cracked; }

    public float waterTarget() { return Mth.clamp(water.getFluidAmount() / (float) Balance.NR_WATER_TANK_MB, 0.0F, 1.0F); }
    public float waterShown() { return Math.max(0.0F, shownWater); }
    private boolean alarmShown() { return shownHeat >= Balance.NR_HEAT_ALARM || cracked || damaged || meltdownPending; }
    private float hotShown() { return Mth.clamp((shownHeat - 350.0F) / 600.0F, 0.0F, 1.0F); }

    private static int lerpC(int a, int b, float k) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(ar + (br - ar) * k) << 16 | Math.round(ag + (bg - ag) * k) << 8 | Math.round(ab + (bb - ab) * k);
    }

    public int coreColour(double tt) {
        float hk = hotShown(), p = pctNow / 100.0F;
        if (meltdownPending || shownHeat >= Balance.NR_HEAT_MAX - 30) return ((long) tt) % 4 < 2 ? 0xFFF4E0 : 0xFFB060;
        if (hk > 0.05F) return lerpC(0xFF7A20, 0xFFF0D8, hk);
        if (pctNow <= 0) return 0x284A70;
        return lerpC(0x2E78C8, 0x7FE6FF, p);
    }

    public int bandColour(double tt) { return hotShown() > 0.05F || alarmShown() ? 0xFF4020 : pctNow <= 0 ? 0x1E3C5A : pctNow <= Balance.NR_QUIET_PCT ? 0x2E6EA8 : 0x5FD0FF; }

    public int lightColour(int k, double tt) {
        if (alarmShown()) return ((int) (tt / 2.0) + k) % 12 < 3 ? 0xFF2818 : 0x300606;
        float w = waterShown();
        if (waterTarget() - w > 0.02F && w < 0.98F) return k < Math.round(w * 12.0F) ? 0x5FD0FF : 0x0A1A28;
        if (pctNow <= 0) return 0x0E1C2A;
        if (pctNow <= Balance.NR_QUIET_PCT) return 0x24508A;
        float b = 0.55F + 0.45F * (float) Math.sin(tt * 0.2 - k * 0.52);
        return lerpC(0x1E4A80, 0x6FDCFF, b);
    }

    public int stripColour(double tt) {
        if (alarmShown()) return ((long) tt) % 20 < 10 ? 0xFF2818 : 0x380808;
        return pctNow <= 0 ? 0x10243A : pctNow <= Balance.NR_QUIET_PCT ? 0x24508A : 0x4FB8F0;
    }

    public int waterTint() {
        float hk = hotShown();
        int rgb = lerpC(0x3C8CE6, 0xC8E4FF, Math.min(1.0F, hk * 1.4F));
        int a = Math.round(150 - 40 * hk);
        return a << 24 | rgb;
    }

    private long fxTick = Long.MIN_VALUE;
    private double lastTt = Double.NaN;

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        final float p = pctNow / 100.0F;

        float target = waterTarget();
        if (shownWater < 0.0F) shownWater = target;
        double dt = Double.isNaN(lastTt) ? 0.0 : Mth.clamp(tt - lastTt, 0.0, 5.0);
        lastTt = tt;
        shownWater += (target - shownWater) * (float) Math.min(1.0, dt * 0.06);
        final float w = shownWater;
        model.getBone("water").ifPresent(b -> { b.setHidden(w < 0.01F); b.setScaleY(Math.max(0.001F, w)); });

        final boolean down = damaged || scram || pctNow <= 0;
        model.getBone("rods").ifPresent(b -> b.setPosY(down ? -4.5F : -4.5F * (1.0F - p)));

        final float hk = hotShown();
        model.getBone("band").ifPresent(b -> b.setRotY((float) (tt * (0.012 + 0.05 * p + 0.12 * hk))));
        model.getBone("crack").ifPresent(b -> b.setHidden(!cracked));
        model.getBone("shaftout").ifPresent(b -> b.setRotX(0.0F));
        if (level != null && level.isClientSide && (long) tt != fxTick) {
            fxTick = (long) tt;
            effects(level, w, hk);
        }
    }

    private void effects(Level lv, float w, float hk) {
        var r = lv.random;
        boolean steam = shownHeat >= Balance.NR_HEAT_STEAM || cracked || meltdownPending;
        if (steam && r.nextInt(meltdownPending ? 1 : cracked && shownHeat < Balance.NR_HEAT_STEAM ? 4 : 2) == 0) {
            for (double vx : new double[] { 1.0 - 17.0 / 16.0, 1.0 + 17.0 / 16.0 }) {
                double[] v = worldPoint(vx, 3.0, 1.0);
                lv.addParticle(ParticleTypes.CLOUD, v[0], v[1], v[2], (r.nextDouble() - 0.5) * 0.04, 0.12 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.04);
                if (r.nextInt(3) == 0) lv.addParticle(ParticleTypes.POOF, v[0], v[1] + 0.1, v[2], 0.0, 0.05, 0.0);
            }
        }
        if (w > 0.03F && (hk > 0.2F || meltdownPending) && r.nextInt(2) == 0) {
            double a = r.nextDouble() * Math.PI * 2.0, rr = Math.sqrt(r.nextDouble()) * 1.25;
            double[] v = worldPoint(1.0 + Math.cos(a) * rr, (16.6 + 23.4 * w) / 16.0, 1.0 + Math.sin(a) * rr);
            lv.addParticle(ParticleTypes.BUBBLE_POP, v[0], v[1], v[2], 0.0, 0.04, 0.0);
            if (r.nextInt(3) == 0) lv.addParticle(ParticleTypes.SPLASH, v[0], v[1], v[2], 0.0, 0.1, 0.0);
        }
        if (cracked && w > 0.03F && r.nextInt(2) == 0) {
            double a = Math.toRadians(-50.0);
            double[] v = worldPoint(1.0 + Math.cos(a) * 1.43, Math.min(1.85, (16.6 + 23.4 * w) / 16.0 - 0.05), 1.0 + Math.sin(a) * 1.43);
            double[] o = worldPoint(1.0 + Math.cos(a) * 1.9, Math.min(1.85, (16.6 + 23.4 * w) / 16.0 - 0.05), 1.0 + Math.sin(a) * 1.9);
            double dx = (o[0] - v[0]) * 0.25, dz = (o[2] - v[2]) * 0.25;
            lv.addParticle(ParticleTypes.SPLASH, v[0], v[1], v[2], dx, 0.05, dz);
            lv.addParticle(ParticleTypes.FALLING_WATER, v[0], v[1], v[2], 0.0, 0.0, 0.0);
            if (r.nextInt(3) == 0) lv.addParticle(ParticleTypes.CLOUD, v[0], v[1], v[2], dx * 0.4, 0.04, dz * 0.4);
        }
    }

    @Override
    protected void clientEffects(Level lv) {}

    private void sync() {
        if (level == null || level.isClientSide) return;
        int key = pctNow * 31 + (int) heat / 4 * 7 + lvl * 101 + (scram ? 3 : 0) + (damaged ? 5 : 0) + (sea ? 11 : 0) + (cooled ? 13 : 0) + fuel.getCount() * 17
                + spent.getCount() * 19 + (int) (fuelLeft / 2880.0) * 23 + water.getFluidAmount() / 80 * 29 + (cracked ? 37 : 0);
        if (key == syncKey && level.getGameTime() % 40 != 0) return;
        if (key != syncKey || level.getGameTime() % 200 == 0) {
            syncKey = key;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag);
        tag.put("nrFuel", fuel.saveOptional(registries));
        tag.put("nrSpent", spent.saveOptional(registries));
        tag.putInt("nrStored", stored);
        tag.putBoolean("nrPending", meltdownPending);
        ownerData.write(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putDouble("nrFuelLeft", fuelLeft);
        tag.putInt("nrLevel", lvl);
        tag.putBoolean("nrScram", scram);
        tag.putDouble("nrHeat", heat);
        tag.putInt("nrDecay", decayLeft);
        tag.putBoolean("nrDamaged", damaged);
        tag.putBoolean("nrSea", sea);
        tag.putBoolean("nrCooled", cooled);
        tag.putInt("nrPct", pctNow);
        tag.putBoolean("nrCracked", cracked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelLeft = tag.getDouble("nrFuelLeft");
        lvl = Mth.clamp(tag.getInt("nrLevel"), 0, Balance.NR_LEVELS.length - 1);
        scram = tag.getBoolean("nrScram");
        heat = tag.getDouble("nrHeat");
        shownHeat = (int) Math.round(heat);
        decayLeft = tag.getInt("nrDecay");
        damaged = tag.getBoolean("nrDamaged");
        sea = tag.getBoolean("nrSea");
        cooled = tag.getBoolean("nrCooled");
        pctNow = tag.getInt("nrPct");
        cracked = tag.getBoolean("nrCracked");
        if (tag.contains("nrFuel")) fuel = ItemStack.parseOptional(registries, tag.getCompound("nrFuel"));
        if (tag.contains("nrSpent")) spent = ItemStack.parseOptional(registries, tag.getCompound("nrSpent"));
        if (tag.contains("nrStored")) stored = Mth.clamp(tag.getInt("nrStored"), 0, Balance.NR_BUFFER_FE);
        meltdownPending = tag.getBoolean("nrPending");
        ownerData.read(tag);
        syncGauges();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeState(tag);
        tag.put("nrFuel", fuel.saveOptional(registries));
        tag.put("nrSpent", spent.saveOptional(registries));
        ownerData.write(tag);
        List<FluidTank> ts = tanks();
        for (int i = 0; i < ts.size(); i++) tag.put("tank" + i, ts.get(i).writeToNBT(registries, new CompoundTag()));
        return tag;
    }
}
