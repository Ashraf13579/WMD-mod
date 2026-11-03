package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CcMapTableBlock extends CcModuleBlock {

    public static final MapCodec<CcMapTableBlock> CODEC = simpleCodec(CcMapTableBlock::new);

    public CcMapTableBlock(Properties properties) {
        super(properties, CcMapTableBlockEntity.X0, CcMapTableBlockEntity.X1, CcMapTableBlockEntity.Y0, CcMapTableBlockEntity.Y1,
                CcMapTableBlockEntity.Z0, CcMapTableBlockEntity.Z1, false);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcMapTableBlockEntity(pos, state); }

    @Override
    protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_MAP_TABLE_BE.get(); }
}
