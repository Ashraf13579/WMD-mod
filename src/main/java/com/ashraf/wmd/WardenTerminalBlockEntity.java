package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WardenTerminalBlockEntity extends BlockEntity {

    int phase = WardenState.ARMED;
    long secs;

    public WardenTerminalBlockEntity(BlockPos pos, BlockState state) { super(Wmd.WARDEN_TERMINAL_BE.get(), pos, state); }

    void serverTick() {
        if (!(level instanceof ServerLevel sl) || sl.getGameTime() % 20 != 0) return;
        WardenState d = WardenState.get(sl.getServer());
        long s = d.msToRespawn() / 1000;
        if (d.phase == phase && s == secs) return;
        phase = d.phase;
        secs = s;
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    static void clientTick(WardenTerminalBlockEntity be) {
        MachineLoop.ensure(be, WmdSounds.WARDEN_TERMINAL_HUM.get(), () -> !be.isRemoved(), 0.5, Balance.SND_SMALL, false);
    }

    public int phase() { return phase; }
    public long secs() { return secs; }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putInt("phase", phase);
        t.putLong("secs", secs);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider registries) {
        super.loadAdditional(t, registries);
        if (t.contains("phase")) phase = t.getInt("phase");
        secs = t.getLong("secs");
    }
}
