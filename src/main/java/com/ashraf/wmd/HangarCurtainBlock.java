package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HangarCurtainBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty EDGE = IntegerProperty.create("edge", 0, 2);
    public static final BooleanProperty DRAWN = BooleanProperty.create("drawn"), BOTTOM = BooleanProperty.create("bottom");

    private static final VoxelShape SLAB_NS = Block.box(0, 0, 6, 16, 16, 10), SLAB_EW = Block.box(6, 0, 0, 10, 16, 16);

    public HangarCurtainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EDGE, 0).setValue(DRAWN, true).setValue(BOTTOM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EDGE, DRAWN, BOTTOM);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return state.getValue(DRAWN) ? RenderShape.INVISIBLE : RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SLAB_NS : SLAB_EW;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !holds(neighborState, state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return state;
    }

    static boolean holds(BlockState above, Direction facing) {
        if (above.getBlock() instanceof HangarCurtainBlock) return above.getValue(FACING) == facing;
        return above.getBlock() instanceof HangarDoorBlock && above.getValue(MachineBlock.FACING) == facing;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) { return state.setValue(FACING, rot.rotate(state.getValue(FACING))); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) HangarDoorBlockEntity.click(level, pos, player, hit, true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(HangarDoorBlocks.HANGAR_DOOR_ITEM.get());
    }
}
