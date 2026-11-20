package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WardenNodeBlockEntity extends BlockEntity {

    public WardenNodeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.WARDEN_NODE_BE.get(), pos, state); }

    static void clientTick(WardenNodeBlockEntity be) {
        MachineLoop.ensure(be, WmdSounds.WARDEN_NODE_HUM.get(), () -> !be.isRemoved(), 1.0, Balance.SND_SMALL, false);
    }
}
