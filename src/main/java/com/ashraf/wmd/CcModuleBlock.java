package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public abstract class CcModuleBlock extends UnitBlock {

    protected CcModuleBlock(Properties properties, int x0, int x1, int y0, int y1, int z0, int z1, boolean wall) {
        super(properties, x0, x1, y0, y1, z0, z1);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide && context.getPlayer() != null)
            context.getPlayer().displayClientMessage(Component.translatable("message.wmd.ccb.install_here"), true);
        return null;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CcModuleBlockEntity m && m.isManaged()) return List.of();
        return super.getDrops(state, params);
    }

    protected abstract BlockEntityType<? extends CcModuleBlockEntity> beType();

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, beType(), level.isClientSide);
    }

    private static <T extends BlockEntity, E extends CcModuleBlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> type, BlockEntityType<E> mine, boolean client) {
        if (client) return createTickerHelper(type, mine, CcModuleBlockEntity::clientTickModule);
        return createTickerHelper(type, mine, CcModuleBlockEntity::serverTickModule);
    }

    public static class WarningBoard extends CcModuleBlock {
        public static final MapCodec<WarningBoard> CODEC = simpleCodec(WarningBoard::new);
        public static final BooleanProperty ALERT = BooleanProperty.create("alert");

        public WarningBoard(Properties p) {
            super(p, -2, 2, 0, 2, 0, 0, true);
            registerDefaultState(defaultBlockState().setValue(ALERT, false));
        }

        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcWarningBoardBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_WARNING_BOARD_BE.get(); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(ALERT);
        }

        @Override protected boolean isSignalSource(BlockState state) { return true; }
        @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return state.getValue(ALERT) ? 15 : 0; }
    }

    public static class ReconWall extends CcModuleBlock {
        public static final MapCodec<ReconWall> CODEC = simpleCodec(ReconWall::new);
        public ReconWall(Properties p) { super(p, -2, 2, 0, 2, 0, 0, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcReconWallBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_RECON_WALL_BE.get(); }
    }

    public static class AirDefence extends CcModuleBlock {
        public static final MapCodec<AirDefence> CODEC = simpleCodec(AirDefence::new);
        public AirDefence(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcAirDefenceBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_AIR_DEFENCE_BE.get(); }
    }
}
