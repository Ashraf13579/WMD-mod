package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class StationModuleBlock extends BaseEntityBlock {

    public static final MapCodec<StationModuleBlock> CODEC = simpleCodec(StationModuleBlock::new);

    public enum Look implements StringRepresentable {
        DECK("deck"), BAND("band"), NONE("none");
        private final String id;
        Look(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
    }

    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);

    public StationModuleBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(LOOK, Look.DECK)); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LOOK); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationModuleBlockEntity(pos, state); }
}
