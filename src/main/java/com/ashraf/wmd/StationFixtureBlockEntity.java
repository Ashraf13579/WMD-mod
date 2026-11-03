package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StationFixtureBlockEntity extends BlockEntity {

    private int charge;
    private boolean draining;

    public StationFixtureBlockEntity(BlockPos pos, BlockState state) { super(Wmd.STATION_FIXTURE_BE.get(), pos, state); }

    public int charge() { return charge; }
    public boolean draining() { return draining; }

    void setCharge(int cells, boolean drain) {
        if (cells == charge && drain == draining) return;
        charge = Math.max(0, Math.min(12, cells));
        draining = drain;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
        tag.putBoolean("drain", draining);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Math.max(0, Math.min(12, tag.getInt("charge")));
        draining = tag.getBoolean("drain");
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("charge", charge);
        tag.putBoolean("drain", draining);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
