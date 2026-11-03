package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class ReactorPortBlockEntity extends BlockEntity implements ShipRelink.Holder {

    private BlockPos controller;

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) { super(Wmd.REACTOR_PORT_BE.get(), pos, state); }

    void link(BlockPos ctrl) {
        controller = ctrl == null ? null : ctrl.immutable();
        setChanged();
    }

    @Override
    public int relink(net.minecraft.server.level.ServerLevel level, java.util.function.LongUnaryOperator map) {
        if (controller == null) return 0;
        long to = map.applyAsLong(controller.asLong());
        if (to == controller.asLong()) return 0;
        link(BlockPos.of(to));
        return 1;
    }

    private NuclearReactorBlockEntity reactor() {
        if (controller == null || level == null || !level.isLoaded(controller)) return null;
        return level.getBlockEntity(controller) instanceof NuclearReactorBlockEntity r && r.isFormed() ? r : null;
    }

    public IItemHandler item(Direction side) { NuclearReactorBlockEntity r = reactor(); return r == null ? null : r.itemPort(worldPosition, side); }
    public IFluidHandler fluid(Direction side) { NuclearReactorBlockEntity r = reactor(); return r == null ? null : r.fluidPort(worldPosition, side); }
    public IEnergyStorage energy(Direction side) { NuclearReactorBlockEntity r = reactor(); return r == null ? null : r.energyPort(worldPosition, side); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) tag.putLong("controller", controller.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }
}
