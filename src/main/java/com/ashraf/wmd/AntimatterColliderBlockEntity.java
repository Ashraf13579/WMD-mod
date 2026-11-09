package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class AntimatterColliderBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private final FluidTank hydrogen = new FluidTank(Balance.AM_TANK_MB, fs -> fs.getFluid() == WmdFluids.HYDROGEN.source().get());
    private final FluidTank water = new FluidTank(Balance.AM_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot empty = new Slot(16, false, s -> s.is(Wmd.EMPTY_TRAP_CELL.get()));
    private final Slot cell = new Slot(1, true, s -> false);
    private final Slot[] slots = { empty, cell };
    private final List<FluidTank> tanks = List.of(hydrogen, water, empty.gauge, cell.gauge);
    private final IFluidHandler h2In = fillOnly(hydrogen), waterIn = fillOnly(water);
    private int progress;

    public AntimatterColliderBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ANTIMATTER_COLLIDER_BE.get(), pos, state, Balance.AM_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (cell.count() > 0) return fail(OUT_FULL);
        if (empty.count() < 1) return fail(NO_INPUT);
        if (hydrogen.getFluidAmount() < Balance.AM_H2_MB || water.getFluidAmount() < Balance.AM_WATER_MB) return fail(NO_INPUT);
        hydrogen.drain(Balance.AM_H2_MB, IFluidHandler.FluidAction.EXECUTE);
        water.drain(Balance.AM_WATER_MB, IFluidHandler.FluidAction.EXECUTE);
        flowAcc += Balance.AM_WATER_MB;
        if (++progress >= Balance.AM_CYCLE_TICKS) {
            progress = 0;
            empty.take(1);
            cell.add(Wmd.ANTIMATTER_CELL.get(), 1);
            Wmd.LOGGER.info("[antimatter] collider at {} made an antimatter cell", getBlockPos().toShortString());
        }
        if (progress % 200 == 0) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    static final java.util.Set<AntimatterColliderBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

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

    private boolean healed;

    void healParts() {
        if (healed || level == null || level.isClientSide) return;
        healed = true;
        AntimatterColliderBlock.clearStrays(level, worldPosition, facing(), Machines.mount(getBlockState()), false);
    }

    private long minedTick = Long.MIN_VALUE;
    void markMined(net.minecraft.world.level.Level lv) { minedTick = lv.getGameTime(); }

    private void cellLost(String why) {
        if (cell.count() == 0 || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        cell.take(cell.count());
        setChanged();
        AntimatterStoreBlockEntity.annihilate(sl, worldPoint(0.0, 0.0, 4.0), 1, "collider at " + worldPosition.toShortString() + " (" + why + ")");
    }

    void removed(net.minecraft.world.level.Level lv, BlockPos pos) {
        if (lv.getGameTime() - minedTick <= 1) {
            for (Slot s : slots) if (!s.stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(lv, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.stack.copy());
            for (Slot s : slots) { s.stack = ItemStack.EMPTY; s.sync(); }
        } else if (lv instanceof net.minecraft.server.level.ServerLevel sl && cell.count() > 0) {
            sl.getServer().execute(() -> cellLost("destroyed"));
        }
    }

    static void blastAt(net.minecraft.server.level.ServerLevel sl, net.minecraft.world.phys.Vec3 pos, double r) {
        for (AntimatterColliderBlockEntity be : new java.util.ArrayList<>(LOADED)) {
            if (be.level != sl || be.isRemoved() || be.cell.count() == 0) continue;
            double[] c = be.worldPoint(0.0, 4.0, 4.0);
            if (Math.sqrt((c[0] - pos.x) * (c[0] - pos.x) + (c[1] - pos.y) * (c[1] - pos.y) + (c[2] - pos.z) * (c[2] - pos.z)) > r + 4.0) continue;
            be.cellLost("caught in a blast r " + Math.round(r));
        }
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER || t == UpgradeItem.Type.AUTO_EJECT; }

    static final String[] SEG_BONES = new String[ClientColliderFx.NSEG], MAG_BONES = new String[16], PROG_BONES = new String[5];
    static {
        for (int k = 0; k < SEG_BONES.length; k++) SEG_BONES[k] = String.format("seg%02d", k);
        for (int k = 0; k < MAG_BONES.length; k++) MAG_BONES[k] = String.format("mag%02d", k);
        for (int k = 0; k < PROG_BONES.length; k++) PROG_BONES[k] = "prog" + k;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean on = running && !dark;

        double ang = ClientColliderFx.bunchAngle(tt);
        int head = ClientColliderFx.segAt(ang);
        for (int k = 0; k < SEG_BONES.length; k++) {
            final boolean lit = on && Math.floorMod(head - k, ClientColliderFx.NSEG) < ClientColliderFx.COMET;
            model.getBone(SEG_BONES[k]).ifPresent(b -> b.setHidden(!lit));
        }

        boolean ramp = on && ClientColliderFx.inRamp(tt), rampLit = ramp && ClientColliderFx.rampBlink(tt);
        for (int m = 0; m < MAG_BONES.length; m++) {
            final boolean lit = ramp ? rampLit : on && ClientColliderFx.magnetLit(m, ang);
            model.getBone(MAG_BONES[m]).ifPresent(b -> b.setHidden(!lit));
        }

        float batch = batch01((long) tt);
        boolean blink = ((long) tt / 10L) % 2L == 0L;
        for (int k = 0; k < PROG_BONES.length; k++) {
            final boolean lit = !dark && (clientCell || batch >= (k + 1) / 5.0F || (running && blink && batch >= k / 5.0F));
            model.getBone(PROG_BONES[k]).ifPresent(b -> b.setHidden(!lit));
        }
        model.getBone("cooler").ifPresent(b -> b.setHidden(!on));
        final boolean rf = on && ((long) tt) % 10 < 7;
        model.getBone("rf").ifPresent(b -> b.setHidden(!rf));
        final boolean shot = on && Math.floorMod((long) tt - ClientColliderFx.SHOT_AT, 40L) < ClientColliderFx.SHOT_LEN;
        model.getBone("target").ifPresent(b -> b.setHidden(!shot));
        final boolean desk = !dark && (running || clientProgress > 0);
        model.getBone("desk").ifPresent(b -> b.setHidden(!desk));
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected String[] iconIds() { return new String[] { "empty_trap_cell", "antimatter_cell" }; }
    @Override protected float progress01() { return progress / (float) Balance.AM_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.HYDROGEN.source().get(), Fluids.WATER, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("hydrogen", "water", "empty_trap_cell", "antimatter_cell"); }
    @Override protected int inputTanks() { return 2; }
    @Override protected int rateMbPerTick() { return Balance.AM_WATER_MB; }
    @Override public boolean guiTankOutput(int t) { return t == 3; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }

    @Override
    public ItemStack guiPreview(List<ItemStack> in) { return new ItemStack(Wmd.ANTIMATTER_CELL.get()); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", String.format("%,d", Balance.AM_FE_PER_TICK * 20)),
                Component.translatable("gui.wmd.info.antimatter_collider", Balance.AM_H2_MB * 20, Balance.AM_WATER_MB * 20, Balance.AM_CYCLE_TICKS / 1200),
                Component.translatable("gui.wmd.info.antimatter_store_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.AM_COLLIDER_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 4); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }

    @Override
    protected String detail() {
        return "hydrogen " + mb(hydrogen) + ", water " + mb(water) + ", empty cells " + empty.count() + ", antimatter cells " + cell.count()
                + ", cycle " + progress * 100 / Balance.AM_CYCLE_TICKS + "% (" + (Balance.AM_CYCLE_TICKS - progress) / 20 + " s left)";
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -4, 1, 0, Direction.NORTH)) return h2In;
        if (at(part, side, 4, 1, 0, Direction.NORTH)) return waterIn;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 4, 1, 5, Direction.EAST) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(-16, 16, 33)
            .fluid(-4, 1, 0, Direction.NORTH, "hydrogen", false).fluid(4, 1, 0, Direction.NORTH, "water", false)
            .power(4, 1, 5, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    private int clientProgress;
    private boolean clientCell;
    private long clientSyncTick = Long.MIN_VALUE;
    int shownFifth = -1;

    float batch01(long now) {
        if (clientCell) return 1.0F;
        if (clientSyncTick == Long.MIN_VALUE) clientSyncTick = now;
        long p = clientProgress + (running ? Math.max(0L, now - clientSyncTick) : 0L);
        return Math.min(Balance.AM_CYCLE_TICKS - 1, p) / (float) Balance.AM_CYCLE_TICKS;
    }

    @Override protected void clientEffects(Level level) { ClientColliderFx.tick(this, level); }
    @Override protected void runStarted(Level level) { ClientColliderFx.started(this, level); }
    @Override protected void runStopped(Level level) { ClientColliderFx.stopped(this, level, clientCell); }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("am_progress", progress);
        ownerData.write(tag);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("am_progress")) {
            progress = Math.max(0, tag.getInt("am_progress"));
            ownerData.read(tag);
        }
        clientProgress = tag.getInt("am_shown");
        clientCell = tag.getBoolean("am_cell");
        clientSyncTick = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("am_shown", progress);
        tag.putBoolean("am_cell", cell.count() > 0);
        return tag;
    }
}
