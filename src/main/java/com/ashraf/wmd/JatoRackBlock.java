package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class JatoRackBlock extends AirEngineBlock {

    public static final MapCodec<JatoRackBlock> CODEC = simpleCodec(JatoRackBlock::new);

    public JatoRackBlock(Properties properties) { super(properties, "jato", 0, 0, 1, 3); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return Wmd.JATO_RACK_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newJato(pos, state); }
}
