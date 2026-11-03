package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WarheadReactorBlock extends MachineBlock {

    public static final MapCodec<WarheadReactorBlock> CODEC = simpleCodec(WarheadReactorBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static final double[] VALVE_X = { -30.0, -10.0, 10.0, 30.0 };
    static final double VALVE_Y = 9.5, KNOB_X = -30.0, BUTTON_X = 30.0;

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 3; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public WarheadReactorBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WarheadReactorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.WARHEAD_REACTOR_BE.get(), WarheadReactorBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.WARHEAD_REACTOR_BE.get(), WarheadReactorBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        if (g != null) return g;
        int x = local.getX(), y = local.getY(), z = local.getZ();
        if (LabModuleBlock.corridor(local) != null) return LabModuleBlock.corridor(local);
        if (y == 0) return Shapes.block();
        boolean tower = x == 2 && z == 2;
        if (z == 0) return y == 1 ? Shapes.block() : Block.box(0, 0, 0, 16, 9, 16);
        if (Math.abs(x) <= 1) return Shapes.block();
        return tower ? turned(7, 0, 7, 15, 16, 15, facing) : Shapes.empty();
    }

    private static VoxelShape turned(double x0, double y0, double z0, double x1, double y1, double z1, Direction facing) {
        double ax, az, bx, bz;
        switch (facing) {
            case EAST -> { ax = 16 - z0; az = x0; bx = 16 - z1; bz = x1; }
            case SOUTH -> { ax = 16 - x0; az = 16 - z0; bx = 16 - x1; bz = 16 - z1; }
            case WEST -> { ax = z0; az = 16 - x0; bx = z1; bz = 16 - x1; }
            default -> { ax = x0; az = z0; bx = x1; bz = z1; }
        }
        return Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof WarheadReactorBlockEntity be) be.status(player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof WarheadReactorBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        Direction facing = level.getBlockState(controller).getValue(FACING);
        Vec3 w = hit.getLocation();
        double dx = w.x - (controller.getX() + 0.5), dz = w.z - (controller.getZ() + 0.5);
        double lx, lz;
        switch (facing) {
            case EAST -> { lx = dz; lz = -dx; }
            case SOUTH -> { lx = -dx; lz = -dz; }
            case WEST -> { lx = -dz; lz = dx; }
            default -> { lx = dx; lz = dz; }
        }
        double px = lx * 16, py = (w.y - controller.getY()) * 16, pz = lz * 16;
        boolean lower = player.isShiftKeyDown();

        if (py >= 20.0 && pz < 0) { if (px < 0) be.cycleType(player, lower); else be.start(player); return; }

        if (py < 20.0 && pz < 0) {
            for (int i = 0; i < 4; i++) {
                if (Math.abs(px - VALVE_X[i]) <= 8.5 && Math.abs(py - VALVE_Y) <= 8.5) { be.valve(player, i, lower); return; }
            }
        }

        if (pz >= 10.0 && py >= 12.0) {
            if (Math.abs(px - KNOB_X) <= 9.0) { be.cycleType(player, lower); return; }
            if (Math.abs(px - BUTTON_X) <= 9.0) { be.start(player); return; }
        }
        be.load(player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof WarheadReactorBlockEntity be) be.refund();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
