package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class FluidPipeBlock extends BaseEntityBlock {

    public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);

    public enum Conn implements StringRepresentable {
        NONE, PIPE, PORT;
        @Override public String getSerializedName() { return name().toLowerCase(); }
    }

    public enum Form implements StringRepresentable {
        HUB, STRAIGHT_X, STRAIGHT_Y, STRAIGHT_Z,
        ELBOW_NE, ELBOW_ES, ELBOW_SW, ELBOW_WN, ELBOW_UN, ELBOW_UE, ELBOW_US, ELBOW_UW, ELBOW_DN, ELBOW_DE, ELBOW_DS, ELBOW_DW;
        @Override public String getSerializedName() { return name().toLowerCase(); }
    }

    public static final EnumProperty<Conn> NORTH = EnumProperty.create("north", Conn.class), EAST = EnumProperty.create("east", Conn.class),
            SOUTH = EnumProperty.create("south", Conn.class), WEST = EnumProperty.create("west", Conn.class),
            UP = EnumProperty.create("up", Conn.class), DOWN = EnumProperty.create("down", Conn.class);
    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);

    private static final VoxelShape CORE = Block.box(4.5, 4.5, 4.5, 11.5, 11.5, 11.5);
    private static final VoxelShape[] ARMS = new VoxelShape[6];

    static {
        ARMS[Direction.NORTH.ordinal()] = Block.box(5, 5, 0, 11, 11, 4.5);
        ARMS[Direction.SOUTH.ordinal()] = Block.box(5, 5, 11.5, 11, 11, 16);
        ARMS[Direction.EAST.ordinal()] = Block.box(11.5, 5, 5, 16, 11, 11);
        ARMS[Direction.WEST.ordinal()] = Block.box(0, 5, 5, 4.5, 11, 11);
        ARMS[Direction.UP.ordinal()] = Block.box(5, 11.5, 5, 11, 16, 11);
        ARMS[Direction.DOWN.ordinal()] = Block.box(5, 0, 5, 11, 4.5, 11);
    }

    public final int tier;

    public FluidPipeBlock(Properties properties) { this(properties, 1); }

    public FluidPipeBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(NORTH, Conn.NONE).setValue(EAST, Conn.NONE).setValue(SOUTH, Conn.NONE)
                .setValue(WEST, Conn.NONE).setValue(UP, Conn.NONE).setValue(DOWN, Conn.NONE).setValue(FORM, Form.HUB));
    }

    public static EnumProperty<Conn> prop(Direction d) {
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
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, FORM);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    static Conn connOf(BlockState s, Direction d) {
        if (s.getBlock() instanceof FluidPipeBlock) return s.getValue(prop(d));
        if (s.getBlock() instanceof FluidValveBlock) return FluidValveBlock.conn(s, d);
        return Conn.NONE;
    }

    static boolean cut(LevelReader level, BlockPos pos, Direction d) { return level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be && be.isCut(d); }

    static Conn connection(LevelReader level, BlockPos pos, Direction d) {
        if (cut(level, pos, d)) return Conn.NONE;
        BlockPos np = pos.relative(d);
        BlockState ns = level.getBlockState(np);
        if (ns.getBlock() instanceof FluidPipeBlock) return cut(level, np, d.getOpposite()) ? Conn.NONE : Conn.PIPE;
        if (ns.getBlock() instanceof FluidValveBlock) return FluidValveBlock.axisOf(ns) == d.getAxis() ? Conn.PIPE : Conn.NONE;
        return level instanceof Level l && l.getCapability(Capabilities.FluidHandler.BLOCK, np, d.getOpposite()) != null ? Conn.PORT : Conn.NONE;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(Wmd.WRENCH.get()) || !player.isShiftKeyDown()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        net.minecraft.world.phys.Vec3 r = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction d = Direction.getNearest(r.x, r.y, r.z);
        boolean nowCut = !be.isCut(d);
        be.setCut(d, nowCut);
        level.setBlock(pos, withConnections(level, pos, state), 3);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, nowCut ? 0.8F : 1.2F);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(nowCut ? "message.wmd.pipe.cut" : "message.wmd.pipe.joined", d.getName()), true);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }

    static Form formOf(BlockState s) {
        List<Direction> c = new ArrayList<>();
        for (Direction d : Direction.values()) if (s.getValue(prop(d)) != Conn.NONE) c.add(d);
        if (c.size() != 2) return Form.HUB;
        Direction a = c.get(0), b = c.get(1);
        if (a.getOpposite() == b) {
            return switch (a.getAxis()) {
                case X -> Form.STRAIGHT_X;
                case Y -> Form.STRAIGHT_Y;
                case Z -> Form.STRAIGHT_Z;
            };
        }
        boolean n = has(a, b, Direction.NORTH), e = has(a, b, Direction.EAST), so = has(a, b, Direction.SOUTH), w = has(a, b, Direction.WEST),
                u = has(a, b, Direction.UP), dn = has(a, b, Direction.DOWN);
        if (n && e) return Form.ELBOW_NE;
        if (e && so) return Form.ELBOW_ES;
        if (so && w) return Form.ELBOW_SW;
        if (w && n) return Form.ELBOW_WN;
        if (u && n) return Form.ELBOW_UN;
        if (u && e) return Form.ELBOW_UE;
        if (u && so) return Form.ELBOW_US;
        if (u && w) return Form.ELBOW_UW;
        if (dn && n) return Form.ELBOW_DN;
        if (dn && e) return Form.ELBOW_DE;
        if (dn && so) return Form.ELBOW_DS;
        if (dn && w) return Form.ELBOW_DW;
        return Form.HUB;
    }

    private static boolean has(Direction a, Direction b, Direction d) { return a == d || b == d; }

    private BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) state = state.setValue(prop(d), connection(level, pos, d));
        return state.setValue(FORM, formOf(state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level l) return withConnections(l, pos, state);
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        BlockState next = withConnections(level, pos, state);
        if (next != state) level.setBlock(pos, next, 2);
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int m = 0;
        for (Direction d : Direction.values()) if (state.getValue(prop(d)) != Conn.NONE) m |= 1 << d.ordinal();
        VoxelShape shape = SHAPES[m];
        if (shape == null) {
            shape = CORE;
            for (Direction d : Direction.values()) if ((m & 1 << d.ordinal()) != 0) shape = Shapes.or(shape, ARMS[d.ordinal()]);
            SHAPES[m] = shape = shape.optimize();
        }
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FluidPipeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.FLUID_PIPE_BE.get(), FluidPipeBlockEntity::serverTick);
    }
}
