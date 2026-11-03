package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class BusbarBlock extends BaseEntityBlock {

    public static final MapCodec<BusbarBlock> CODEC = simpleCodec(BusbarBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST, UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;

    public static final BooleanProperty PLUG_N = BooleanProperty.create("plug_north"), PLUG_E = BooleanProperty.create("plug_east"), PLUG_S = BooleanProperty.create("plug_south"),
            PLUG_W = BooleanProperty.create("plug_west"), PLUG_U = BooleanProperty.create("plug_up"), PLUG_D = BooleanProperty.create("plug_down");

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape CORE = Block.box(4.3, 0, 4.3, 11.7, 5.4, 11.7);
    private static final VoxelShape[] ARMS = new VoxelShape[6];

    static {
        ARMS[Direction.NORTH.ordinal()] = Block.box(5, 0, 0, 11, 4.5, 4.3);
        ARMS[Direction.SOUTH.ordinal()] = Block.box(5, 0, 11.7, 11, 4.5, 16);
        ARMS[Direction.EAST.ordinal()] = Block.box(11.7, 0, 5, 16, 4.5, 11);
        ARMS[Direction.WEST.ordinal()] = Block.box(0, 0, 5, 4.3, 4.5, 11);
        ARMS[Direction.UP.ordinal()] = Block.box(5, 4, 5, 11, 16, 11);
        ARMS[Direction.DOWN.ordinal()] = Block.box(5.5, 0, 5.5, 10.5, 0.5, 10.5);
    }

    public BusbarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false)
                .setValue(PLUG_N, false).setValue(PLUG_E, false).setValue(PLUG_S, false).setValue(PLUG_W, false).setValue(PLUG_U, false).setValue(PLUG_D, false));
    }

    public static BooleanProperty plugProp(Direction d) {
        return switch (d) {
            case NORTH -> PLUG_N;
            case EAST -> PLUG_E;
            case SOUTH -> PLUG_S;
            case WEST -> PLUG_W;
            case UP -> PLUG_U;
            case DOWN -> PLUG_D;
        };
    }

    public static BooleanProperty prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, NORTH, EAST, SOUTH, WEST, UP, DOWN, PLUG_N, PLUG_E, PLUG_S, PLUG_W, PLUG_U, PLUG_D);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int m = 0;
        for (Direction d : Direction.values()) if (state.getValue(prop(d))) m |= 1 << d.ordinal();
        VoxelShape shape = SHAPES[m];
        if (shape == null) {
            shape = CORE;
            for (Direction d : Direction.values()) if ((m & 1 << d.ordinal()) != 0) shape = Shapes.or(shape, ARMS[d.ordinal()]);
            SHAPES[m] = shape = shape.optimize();
        }
        return shape;
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    private static boolean connects(LevelReader level, BlockPos pos, Direction d) {
        BlockPos np = pos.relative(d);
        if (level.getBlockState(np).getBlock() instanceof BusbarBlock) return true;
        return level instanceof Level l && l.getCapability(Capabilities.EnergyStorage.BLOCK, np, d.getOpposite()) != null;
    }

    private BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) state = link(level, pos, state, d);
        return state;
    }

    private static BlockState link(LevelReader level, BlockPos pos, BlockState state, Direction d) {
        boolean c = connects(level, pos, d);
        boolean plug = c && !(level.getBlockState(pos.relative(d)).getBlock() instanceof BusbarBlock);
        return state.setValue(prop(d), c).setValue(plugProp(d), plug);
    }

    public BlockState linkedAt(LevelReader level, BlockPos pos) { return withConnections(level, pos, defaultBlockState()); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level) return link(level, pos, state, direction);
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        BlockState next = withConnections(level, pos, state);
        if (next != state) level.setBlock(pos, next, 2);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BusbarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.BUSBAR_BE.get(), BusbarBlockEntity::serverTick);
    }
}
