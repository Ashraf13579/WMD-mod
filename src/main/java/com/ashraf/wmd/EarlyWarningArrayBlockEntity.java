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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class EarlyWarningArrayBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    public static final int X0 = -7, X1 = 7, Y0 = 0, Y1 = 17, Z0 = 0, Z1 = 8;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 8, Direction.SOUTH, false);
    public static final int WARN_KEEP = 5;

    static final int PULSE_TICKS = 40, SWEEP_TICKS = 60;
    static final float SWEEP_PX = 96.0F;

    private final OwnerData ownerData = new OwnerData();

    private final List<long[]> recent = new ArrayList<>();

    public EarlyWarningArrayBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.EARLY_WARNING_ARRAY_BE.get(), pos, state, Balance.EWA_FE_PER_TICK);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static final Set<EarlyWarningArrayBlockEntity> ARRAYS = Collections.newSetFromMap(new WeakHashMap<>());
    static void clearStatics() { synchronized (ARRAYS) { ARRAYS.clear(); } }
    private boolean registered;

    static List<EarlyWarningArrayBlockEntity> all() {
        synchronized (ARRAYS) { return new ArrayList<>(ARRAYS); }
    }

    public static void serverTickArray(Level level, BlockPos pos, BlockState state, EarlyWarningArrayBlockEntity be) {
        if (!be.registered) {
            synchronized (ARRAYS) { ARRAYS.add(be); }
            be.registered = true;
        }
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
        RadarEmit.note(be);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (ARRAYS) { ARRAYS.remove(this); }
        registered = false;
    }

    @Override
    protected boolean work() {
        reason = 0;
        return true;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 8, Direction.SOUTH) ? energy() : null; }

    void noteWarning(long ox, long oz, long ix, long iz, long launchGt, long impactGt, boolean sat) {
        recent.add(new long[] { ox, oz, ix, iz, launchGt, impactGt, sat ? 1 : 0 });
        while (recent.size() > WARN_KEEP) recent.remove(0);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void noteAirship(long x, long z, int blocks, boolean hostile, long now) {
        recent.add(new long[] { x, z, blocks, hostile ? 0 : 1, now, now, 2 });
        while (recent.size() > WARN_KEEP) recent.remove(0);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public List<long[]> recent() { return List.copyOf(recent); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.EWA_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.early_warning_array", String.format("%,d", Balance.EWA_RANGE)));
    }

    @Override
    protected String detail() {
        return (isRunning() ? "watching" : "no power") + ", range " + Balance.EWA_RANGE + ", warnings raised (kept) " + recent.size();
    }

    @Override protected SoundEvent runSound() { return WmdSounds.EWA_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 4); }
    @Override protected float soundVolume() { return 0.2F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean on = isRunning() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        long t = (long) tt;
        boolean lit = on && Math.floorMod(t + (worldPosition.asLong() & 31L), PULSE_TICKS) >= 4;
        model.getBone("glow").ifPresent(b -> b.setHidden(!lit));
        double u = (tt % SWEEP_TICKS) / SWEEP_TICKS;
        final float x = (float) (-SWEEP_PX + 2.0 * SWEEP_PX * u);
        model.getBone("sweep").ifPresent(b -> { b.setHidden(!on); b.setPosX(x); });
    }

    private void writeRecent(CompoundTag tag) {
        ListTag l = new ListTag();
        for (long[] w : recent) l.add(new LongArrayTag(w));
        tag.put("ewaRecent", l);
    }

    private void readRecent(CompoundTag tag) {
        if (!tag.contains("ewaRecent")) return;
        recent.clear();
        for (Tag x : tag.getList("ewaRecent", Tag.TAG_LONG_ARRAY)) {
            long[] w = ((LongArrayTag) x).getAsLongArray();
            if (w.length == 7) recent.add(w);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        writeRecent(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        readRecent(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        writeRecent(tag);
        return tag;
    }
}
