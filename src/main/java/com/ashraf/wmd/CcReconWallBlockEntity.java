package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CcReconWallBlockEntity extends CcModuleBlockEntity {

    public CcReconWallBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_RECON_WALL_BE.get(), pos, state, Balance.CC_RECON_WALL_FE); }

    @Override public CcModule kind() { return CcModule.RECON_WALL; }
}
