package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SatelliteFacilityBlock extends ItemUnitBlock {

    public static final MapCodec<SatelliteFacilityBlock> CODEC = simpleCodec(SatelliteFacilityBlock::new);

    public SatelliteFacilityBlock(Properties properties) {
        super(properties, SatelliteFacilityBlockEntity.X0, SatelliteFacilityBlockEntity.X1, SatelliteFacilityBlockEntity.Y0, SatelliteFacilityBlockEntity.Y1,
                SatelliteFacilityBlockEntity.Z0, SatelliteFacilityBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SatelliteFacilityBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SATELLITE_FACILITY_BE.get(), SatelliteFacilityBlockEntity::clientTickFacility)
                : createTickerHelper(type, Wmd.SATELLITE_FACILITY_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        if (g == null) g = Shapes.block();
        if (local.getZ() != 0 || Math.abs(local.getX()) > 2 || local.getY() > 3) return g;
        final VoxelShape mesh = g;
        return WINDOW.computeIfAbsent(local.getX() + "," + local.getY() + "|" + facing, k -> Shapes.or(mesh, windowWall(facing)).optimize());
    }

    private static final java.util.Map<String, VoxelShape> WINDOW = new java.util.concurrent.ConcurrentHashMap<>();

    private static VoxelShape windowWall(Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(6, 0, 0, 8, 16, 16);
            case SOUTH -> Block.box(0, 0, 6, 16, 16, 8);
            case WEST -> Block.box(8, 0, 0, 10, 16, 16);
            default -> Block.box(0, 0, 8, 16, 16, 10);
        };
    }
}
