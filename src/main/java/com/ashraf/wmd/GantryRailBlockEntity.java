package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class GantryRailBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host {

    private final CopycatState copycat = new CopycatState();

    public GantryRailBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.GANTRY_RAIL_BE.get(), pos, state, 0, 1, 0);
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Direction along() { return Machines.dirToWorld(facing(), Direction.UP, Direction.EAST); }

    static boolean railAt(Level level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof GantryRailBlock && s.getValue(MachineBlock.FACING) == facing;
    }

    @Override protected boolean work() { return false; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (level == null) return;
        Direction e = along();
        boolean west = railAt(level, worldPosition.relative(e.getOpposite()), facing()), east = railAt(level, worldPosition.relative(e), facing());
        model.getBone("stop_w").ifPresent(b -> b.setHidden(west));
        model.getBone("stop_e").ifPresent(b -> b.setHidden(east));
    }

    @Override protected void clientEffects(Level lv) {}
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.T4_CRANE_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return "-"; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        copycat.putSync(tag);
        return tag;
    }
}
