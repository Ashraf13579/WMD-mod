package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class CctvDeviceEntity extends ProcessUnitBlockEntity implements Owned, CctvBlocks.CctvDevice {

    private final OwnerData ownerData = new OwnerData();
    protected int frequency = 1;
    private boolean joined;

    protected CctvDeviceEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick) {
        super(type, pos, state, fePerTick);
    }

    @Override
    public net.neoforged.neoforge.energy.IEnergyStorage guiEnergy() { return guiFeUse() <= 0 ? null : super.guiEnergy(); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
        changed();
    }

    public int getFrequency() { return frequency; }

    public void setFrequency(int f) {
        frequency = Mth.clamp(f, 1, 9999);
        setChanged();
        sync();
        changed();
    }

    protected void changed() {}

    protected void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!joined) { CctvNet.join(this); joined = true; }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CctvNet.leave(this);
        joined = false;
    }

    protected Vec3 modelPoint(double x, double y, double z) {
        double[] w = worldPoint(x / 16.0, y / 16.0, z / 16.0);
        return new Vec3(w[0], w[1], w[2]);
    }

    @Override protected boolean work() { return true; }
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected SoundEvent runSound() { return WmdSounds.ST_HAB_HUM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("freq", frequency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("freq")) frequency = Math.max(1, tag.getInt("freq"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("freq", frequency);
        return tag;
    }
}
