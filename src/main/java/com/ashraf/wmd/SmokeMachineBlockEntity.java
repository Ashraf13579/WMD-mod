package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SmokeMachineBlockEntity extends ProcessUnitBlockEntity implements Owned, GuiItemSlots, GuiAction, CcAnimated {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);
    public static final int A_LAUNCH = 0, TUBES = 8, TUBE_GAP = 2;

    private final OwnerData ownerData = new OwnerData();
    private ItemStack canisters = ItemStack.EMPTY;

    private long launchedAt = -1L;
    private final FluidTank gauge = new FluidTank(Balance.SMOKE_CANISTERS_MAX);

    private int clientCans;

    public SmokeMachineBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SMOKE_MACHINE_BE.get(), pos, state, Balance.SMOKE_FE_PER_TICK); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public long launchedAt() { return launchedAt; }

    long sinceLaunch(long now) { return launchedAt < 0L ? Long.MAX_VALUE : now - launchedAt; }

    public boolean smoking(long now) { return sinceLaunch(now) < Balance.SMOKE_SECONDS * 20L; }

    public int cooldownLeft(long now) {
        long s = sinceLaunch(now);
        return s >= Balance.SMOKE_COOLDOWN * 20L ? 0 : (int) ((Balance.SMOKE_COOLDOWN * 20L - s + 19) / 20);
    }

    public int canisterCount() { return level != null && level.isClientSide ? clientCans : canisters.getCount(); }

    Vec3 groundCentre() {
        BlockState s = getBlockState();
        Direction f = s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH;
        AABB b = new AABB(worldPosition);
        if (s.getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) b = b.minmax(new AABB(Machines.toWorld(worldPosition, f, Machines.mount(s), l.getX(), l.getY(), l.getZ())));
        Vec3 c = new Vec3(b.getCenter().x, b.minY, b.getCenter().z);
        return level == null ? c : SableBridge.toWorld(level, c);
    }

    String launchBlock(long now) {
        if (cooldownLeft(now) > 0) return "message.wmd.smoke.cooldown";
        if (canisters.isEmpty()) return "message.wmd.smoke.no_canister";
        if (energy().getEnergyStored() < Balance.SMOKE_FE_PER_LAUNCH + feCost()) return "message.wmd.smoke.no_power";
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition)) return "message.wmd.smoke.emp";
        return null;
    }

    @Override
    public boolean guiAction(Player player, int id) {
        if (id != A_LAUNCH || !(level instanceof ServerLevel sl)) return false;
        if (!Access.mayUse(this, player)) { Access.deny(this, player); return false; }
        long now = sl.getGameTime();
        String why = launchBlock(now);
        if (why != null) { player.displayClientMessage(Component.translatable(why, cooldownLeft(now)), true); return false; }
        if (!useExtraEnergy(Balance.SMOKE_FE_PER_LAUNCH)) return false;
        canisters.shrink(1);
        if (canisters.isEmpty()) canisters = ItemStack.EMPTY;
        syncGauge();
        launchedAt = now;
        SmokeScreens.launch(sl, groundCentre(), getOwner());
        Wmd.LOGGER.info("Smoke: {} launched a smoke screen at {}", player.getName().getString(), worldPosition.toShortString());
        setChanged();
        sync();
        return true;
    }

    public static void serverTickSmoke(Level level, BlockPos pos, BlockState state, SmokeMachineBlockEntity be) {
        if (level instanceof ServerLevel sl && be.launchedAt >= 0L) {
            long t = sl.getGameTime() - be.launchedAt;
            if (t >= 0 && t < (long) TUBES * TUBE_GAP && t % TUBE_GAP == 0) {
                Vec3 c = be.groundCentre().add(0.0, 2.2, 0.0);
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.CHAFF_CANISTERS.get(), SoundSource.BLOCKS, 2.5F, 0.85F + 0.04F * (t / TUBE_GAP));
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.DECOY_POP.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
            }
            if (t == 30) {
                Vec3 c = be.groundCentre();
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.CHAFF_HISS.get(), SoundSource.BLOCKS, 3.0F, 0.5F);
            }
        }
        if (level.getGameTime() % 10 == 0) be.syncGauge();
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
    }

    @Override
    protected boolean work() {
        if (level == null) return false;
        long now = level.getGameTime();
        reason = canisters.isEmpty() ? 4 : 0;
        return smoking(now);
    }

    private static boolean isCanister(ItemStack s) { return !s.isEmpty() && s.is(Wmd.SMOKE_CANISTER.get()); }

    @Override public int slotCount() { return 1; }
    @Override public int firstSlotTank() { return 0; }
    @Override public int slotMax(int i) { return Balance.SMOKE_CANISTERS_MAX; }

    @Override
    public net.minecraft.world.Container slotContainer() {
        return new net.minecraft.world.Container() {
            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return canisters.isEmpty(); }
            @Override public ItemStack getItem(int i) { return canisters; }
            @Override public ItemStack removeItem(int i, int n) { if (canisters.isEmpty()) return ItemStack.EMPTY; ItemStack r = canisters.split(n); syncGauge(); SmokeMachineBlockEntity.this.setChanged(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = canisters; canisters = ItemStack.EMPTY; syncGauge(); return r; }
            @Override public void setItem(int i, ItemStack st) { canisters = st; syncGauge(); SmokeMachineBlockEntity.this.setChanged(); }
            @Override public int getMaxStackSize() { return Balance.SMOKE_CANISTERS_MAX; }
            @Override public void setChanged() { SmokeMachineBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return isCanister(st); }
            @Override public void clearContent() { canisters = ItemStack.EMPTY; syncGauge(); }
        };
    }

    public ItemStack takeCanisters() {
        ItemStack c = canisters;
        canisters = ItemStack.EMPTY;
        syncGauge();
        return c;
    }

    private void syncGauge() {
        if (level != null && !level.isClientSide && clientCans != canisters.getCount()) { clientCans = canisters.getCount(); sync(); }
        gauge.setFluid(canisters.isEmpty() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, canisters.getCount()));
    }

    @Override protected List<FluidTank> tanks() { return List.of(gauge); }
    @Override protected List<Fluid> tankFluids() { return List.of(net.minecraft.world.level.material.Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("smoke_canisters"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(Wmd.SMOKE_CANISTER.get())); }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.smoke.info_cloud", Balance.SMOKE_RADIUS, Balance.SMOKE_GROW_SECONDS, Balance.SMOKE_SECONDS),
                Component.translatable("gui.wmd.smoke.info_cost", String.format("%,d", Balance.SMOKE_FE_PER_LAUNCH), Balance.SMOKE_COOLDOWN));
    }

    @Override
    protected String detail() {
        long now = level == null ? 0L : level.getGameTime();
        return smoking(now) ? "smoke" : cooldownLeft(now) > 0 ? "cooldown " + cooldownLeft(now) + " s" : canisters.isEmpty() ? "no canister" : "ready";
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected SoundEvent runSound() { return WmdSounds.CHAFF_HISS.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return 0.35F; }
    @Override protected float soundPitch() { return 0.6F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double t = launchedAt < 0L ? -1.0 : tt - launchedAt, fire = (double) TUBES * TUBE_GAP;
        float turn = t < 0.0 || t > fire ? 0.0F : (float) (Math.PI * 2.0 * (0.5 - 0.5 * Math.cos(Math.PI * t / fire)));
        model.getBone("turret").ifPresent(b -> b.setRotY(turn));
        boolean on = level != null && smoking(level.getGameTime());
        model.getBone("glow").ifPresent(b -> b.setHidden(!on));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (!canisters.isEmpty()) tag.put("canisters", canisters.save(registries));
        tag.putLong("launched", launchedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("energy")) { canisters = ItemStack.parseOptional(registries, tag.getCompound("canisters")); syncGauge(); }
        if (tag.contains("launched")) launchedAt = tag.getLong("launched");
        if (tag.contains("cans")) clientCans = tag.getInt("cans");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("launched", launchedAt);
        tag.putInt("cans", canisters.getCount());
        ownerData.write(tag);
        return tag;
    }
}
