package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class CcModuleBlocksC {

    private CcModuleBlocksC() {}

    public static class SatGlobe extends CcModuleBlock {
        public static final MapCodec<SatGlobe> CODEC = simpleCodec(SatGlobe::new);
        public SatGlobe(Properties p) { super(p, -1, 1, 0, 2, 0, 2, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcSatGlobeBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_SAT_GLOBE_BE.get(); }
    }

    public static class EwRack extends CcModuleBlock {
        public static final MapCodec<EwRack> CODEC = simpleCodec(EwRack::new);
        public EwRack(Properties p) { super(p, -1, 0, 0, 2, 0, 0, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcEwRackBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_EW_RACK_BE.get(); }
    }

    public static class GpsRack extends CcModuleBlock {
        public static final MapCodec<GpsRack> CODEC = simpleCodec(GpsRack::new);
        public GpsRack(Properties p) { super(p, -1, 0, 0, 1, 0, 0, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcGpsRackBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_GPS_RACK_BE.get(); }
    }

    public static class AlarmBoard extends CcModuleBlock {
        public static final MapCodec<AlarmBoard> CODEC = simpleCodec(AlarmBoard::new);
        public static final BooleanProperty ALERT = BooleanProperty.create("alert");

        public static final double WALL_LIFT = 0.25;

        public AlarmBoard(Properties p) {
            super(p, -1, 0, 0, 1, 0, 0, true);
            registerDefaultState(defaultBlockState().setValue(ALERT, false));
        }

        @Override public net.minecraft.world.phys.shapes.VoxelShape machineShape(BlockPos local, Direction facing) {
            return super.machineShape(local, facing).move(0.0, WALL_LIFT, 0.0);
        }

        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcAlarmBoardBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_ALARM_BOARD_BE.get(); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(ALERT);
        }

        @Override protected boolean isSignalSource(BlockState state) { return true; }
        @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return state.getValue(ALERT) ? 15 : 0; }
    }

    public static class MapPrinter extends CcModuleBlock {
        public static final MapCodec<MapPrinter> CODEC = simpleCodec(MapPrinter::new);
        public MapPrinter(Properties p) { super(p, 0, 0, 0, 1, 0, 0, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcMapPrinterBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_MAP_PRINTER_BE.get(); }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof CcMapPrinterBlockEntity be && be.tryInsert(player)) return;
            super.interact(level, controller, player);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
            if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
            if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof CcMapPrinterBlockEntity be) {
                for (ItemStack s : be.takeAll()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    public static class CommsArray extends CcModuleBlock {
        public static final MapCodec<CommsArray> CODEC = simpleCodec(CommsArray::new);
        public CommsArray(Properties p) { super(p, -1, 1, 0, 3, 0, 2, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcCommsArrayBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_COMMS_ARRAY_BE.get(); }
    }
}
