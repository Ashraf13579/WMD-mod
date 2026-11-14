package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class FluidValveBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<FluidValveBlock> CODEC = simpleCodec(FluidValveBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty ONE_WAY = BooleanProperty.create("one_way");
    public static final EnumProperty<FluidPipeBlock.Conn> FRONT = EnumProperty.create("front", FluidPipeBlock.Conn.class), BACK = EnumProperty.create("back", FluidPipeBlock.Conn.class);

    public FluidValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Waterlog.W, false).setValue(FACING, Direction.NORTH).setValue(OPEN, true).setValue(ONE_WAY, false)
                .setValue(FRONT, FluidPipeBlock.Conn.NONE).setValue(BACK, FluidPipeBlock.Conn.NONE));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN, ONE_WAY, FRONT, BACK, Waterlog.W); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    static Direction.Axis axisOf(BlockState s) { return s.getValue(FACING).getAxis(); }

    static FluidPipeBlock.Conn conn(BlockState s, Direction d) {
        Direction f = s.getValue(FACING);
        return d == f ? s.getValue(FRONT) : d == f.getOpposite() ? s.getValue(BACK) : FluidPipeBlock.Conn.NONE;
    }

    private static FluidPipeBlock.Conn connection(LevelReader level, BlockPos pos, Direction d) {
        BlockPos np = pos.relative(d);
        BlockState ns = level.getBlockState(np);
        if (ns.getBlock() instanceof FluidPipeBlock)
            return FluidPipeBlock.cut(level, np, d.getOpposite()) || !FluidPipeBlockEntity.sameLine(level, pos, np) ? FluidPipeBlock.Conn.NONE : FluidPipeBlock.Conn.PIPE;
        if (ns.getBlock() instanceof FluidValveBlock) return axisOf(ns) == d.getAxis() && FluidPipeBlockEntity.sameLine(level, pos, np) ? FluidPipeBlock.Conn.PIPE : FluidPipeBlock.Conn.NONE;
        return level instanceof Level l && l.getCapability(Capabilities.FluidHandler.BLOCK, np, d.getOpposite()) != null ? FluidPipeBlock.Conn.PORT : FluidPipeBlock.Conn.NONE;
    }

    static BlockState withConnections(Level level, BlockPos pos, BlockState s) {
        Direction f = s.getValue(FACING);
        return s.setValue(FRONT, connection(level, pos, f)).setValue(BACK, connection(level, pos, f.getOpposite())).setValue(OPEN, !level.hasNeighborSignal(pos));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), Waterlog.placed(defaultBlockState(), context.getLevel(), context.getClickedPos()).setValue(FACING, context.getNearestLookingDirection()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        return level instanceof Level l ? withConnections(l, pos, state) : state;
    }

    @Override
    protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        net.minecraft.world.level.material.FluidState f = Waterlog.fluid(state);
        return f != null ? f : super.getFluidState(state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        BlockState next = withConnections(level, pos, state);
        if (next != state) {
            level.setBlock(pos, next, 2);
            if (next.getValue(OPEN) != state.getValue(OPEN))
                level.playSound(null, pos, next.getValue(OPEN) ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Wmd.WRENCH.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        boolean one = !state.getValue(ONE_WAY);
        level.setBlock(pos, state.setValue(ONE_WAY, one), 3);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, one ? 1.2F : 0.8F);
        player.displayClientMessage(Component.translatable(one ? "message.wmd.valve.one_way" : "message.wmd.valve.two_way", state.getValue(FACING).getName()), true);
        return ItemInteractionResult.SUCCESS;
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[3];
    static {
        VoxelShape wheel = Block.box(5, 11, 5, 11, 15, 11);
        SHAPES[0] = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(4.5, 4, 4, 11.5, 12, 12), wheel);
        SHAPES[1] = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(4, 4.5, 4, 12, 11.5, 12), Block.box(5, 5, 0.5, 11, 11, 4));
        SHAPES[2] = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(4, 4, 4.5, 12, 12, 11.5), wheel);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[axisOf(state).ordinal()];
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FluidPipeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.FLUID_PIPE_BE.get(), FluidPipeBlockEntity::serverTick);
    }
}
