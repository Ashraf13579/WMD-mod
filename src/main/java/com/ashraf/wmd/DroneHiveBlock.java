package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DroneHiveBlock extends MachineBlock {

    @Override public boolean anyFace() { return true; }

    public static final MapCodec<DroneHiveBlock> CODEC = simpleCodec(DroneHiveBlock::new);
    private static final List<BlockPos> CELLS = new ArrayList<>();

    static {
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = -1; z <= 1; z++) CELLS.add(new BlockPos(x, y, z));
    }

    public DroneHiveBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected List<BlockPos> footprint() { return CELLS; }

    @Override public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        return g != null ? g : Shapes.block();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof DroneHiveBlockEntity h) DroneHiveMenu.open(player, h, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DroneHiveBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.DRONE_HIVE_BE.get(), DroneHiveBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof DroneHiveBlockEntity h) h.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
