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

public class MachinePartBlockEntity extends BlockEntity implements ShipRelink.Holder {

    private BlockPos controller;

    public MachinePartBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.MACHINE_PART_BE.get(), pos, state);
    }

    public BlockPos getController() { return controller; }

    private record Chk(BlockPos c, BlockState s, boolean ok) {}
    private volatile Chk chk;

    public BlockPos validController(net.minecraft.world.level.BlockGetter g) {
        BlockPos c = controller;
        if (c == null || g == null) return null;
        BlockState cs = g.getBlockState(c);
        Chk k = chk;
        if (k != null && k.s() == cs && k.c().equals(c)) return k.ok() ? c : null;
        boolean ok = false;
        if (cs.getBlock() instanceof MachineBlock mb && cs.hasProperty(MachineBlock.FACING)) {
            BlockPos local = Machines.toLocal(c, cs.getValue(MachineBlock.FACING), Machines.mount(cs), worldPosition);
            ok = mb.footprint().contains(local);
        }
        chk = new Chk(c, cs, ok);
        return ok ? c : null;
    }

    public BlockPos validController() { return validController(level); }

    public void setController(BlockPos c) {
        controller = c;
        setChanged();

        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int relink(net.minecraft.server.level.ServerLevel level, java.util.function.LongUnaryOperator map) {
        if (controller == null) return 0;
        long to = map.applyAsLong(controller.asLong());
        if (to == controller.asLong()) return 0;
        setController(BlockPos.of(to));
        return 1;
    }

    public IFluidHandler fluid(Direction side) {
        BlockPos vc = validController(); return vc != null && level.getBlockEntity(vc) instanceof MachinePorts p ? p.fluidPort(worldPosition, side) : null;
    }

    public IEnergyStorage energy(Direction side) {
        BlockPos vc = validController(); return vc != null && level.getBlockEntity(vc) instanceof MachinePorts p ? p.energyPort(worldPosition, side) : null;
    }

    public IItemHandler item(Direction side) {
        BlockPos vc = validController(); return vc != null && level.getBlockEntity(vc) instanceof MachinePorts p ? p.itemPort(worldPosition, side) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) tag.putLong("controller", controller.asLong());
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (controller != null) tag.putLong("controller", controller.asLong());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }
}
