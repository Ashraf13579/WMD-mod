package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PadFillerBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty STEP = BooleanProperty.create("step");

    private static final VoxelShape LOW_SOUTH_HALF = Block.box(0, 0, 8, 16, 8, 16);
    private static final VoxelShape HIGH_NORTH_HALF = Block.box(0, 0, 0, 16, 16, 8);

    public PadFillerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STEP, false).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STEP, MachineBlock.MOUNT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    private static VoxelShape shape(BlockState state) {
        if (!state.getValue(STEP)) return Shapes.block();
        return Machines.mountShape(stepShape(state), Machines.mount(state));
    }

    private static VoxelShape stepShape(BlockState state) {

        return switch (state.getValue(FACING)) {
            case NORTH -> Shapes.or(LOW_SOUTH_HALF, HIGH_NORTH_HALF);
            case SOUTH -> Shapes.or(Block.box(0, 0, 0, 16, 8, 8), Block.box(0, 0, 8, 16, 16, 16));
            case WEST -> Shapes.or(Block.box(8, 0, 0, 16, 8, 16), Block.box(0, 0, 0, 8, 16, 16));
            default -> Shapes.or(Block.box(0, 0, 0, 8, 8, 16), Block.box(8, 0, 0, 16, 16, 16));
        };
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        LaunchTerminalBlockEntity be = lift(level, pos, state);
        if (be == null) return net.minecraft.world.InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.network.chat.Component why = be.board(sp);
            if (why != null) sp.displayClientMessage(why.copy().withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    static LaunchTerminalBlockEntity lift(Level level, BlockPos pos, BlockState state) {
        if (Machines.mount(state) != Direction.UP) return null;
        BlockPos owner = null;
        for (int dy = 1; dy <= 3 && owner == null; dy++) owner = PadStructure.findOwner(level, pos.below(dy), Direction.UP);
        if (owner == null || !(level.getBlockEntity(owner) instanceof LaunchTerminalBlockEntity be) || be.getTier() < 4) return null;
        net.minecraft.world.phys.Vec3 s = PadCrew.shaft(be);
        double dx = pos.getX() + 0.5 - s.x, dz = pos.getZ() + 0.5 - s.z;
        double r = Balance.CREW_LIFT_R + 0.5;
        return dx * dx + dz * dz <= r * r ? be : null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && !PadStructure.unforming()) {
            BlockPos owner = null;
            Direction down = Machines.mount(state).getOpposite();
            for (int dy = 1; dy <= 3 && owner == null; dy++) owner = PadStructure.findOwner(level, pos.relative(down, dy), Machines.mount(state));
            if (owner != null) {
                BlockState t = level.getBlockState(owner);
                if (t.getBlock() instanceof LaunchTerminalBlock block) {
                    PadStructure.unform(level, owner, t.getValue(LaunchTerminalBlock.FACING), Machines.mount(t), block.getTier());
                    level.setBlock(owner, t.setValue(LaunchTerminalBlock.FORMED, false), 3);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
