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

public class BusbarBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<BusbarBlock> CODEC = simpleCodec(BusbarBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST, UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;

    public static final BooleanProperty PLUG_N = BooleanProperty.create("plug_north"), PLUG_E = BooleanProperty.create("plug_east"), PLUG_S = BooleanProperty.create("plug_south"),
            PLUG_W = BooleanProperty.create("plug_west"), PLUG_U = BooleanProperty.create("plug_up"), PLUG_D = BooleanProperty.create("plug_down");

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final BooleanProperty WALLRUN = BooleanProperty.create("wallrun");

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
                .setValue(PLUG_N, false).setValue(PLUG_E, false).setValue(PLUG_S, false).setValue(PLUG_W, false).setValue(PLUG_U, false).setValue(PLUG_D, false)
                .setValue(WATERLOGGED, false).setValue(WALLRUN, false));
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
        builder.add(LIT, NORTH, EAST, SOUTH, WEST, UP, DOWN, PLUG_N, PLUG_E, PLUG_S, PLUG_W, PLUG_U, PLUG_D, WATERLOGGED, WALLRUN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(WALLRUN)) {
            java.util.List<Part> parts = parts(level, pos, state);
            if (parts != null) return wallShape(parts);
        }
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
        return withRun(level, pos, state);
    }

    private static boolean horizontal(BlockState s) { return s.getValue(NORTH) || s.getValue(EAST) || s.getValue(SOUTH) || s.getValue(WEST); }

    private static boolean plain(BlockState s, Direction d) { return s.getValue(prop(d)) && !s.getValue(plugProp(d)); }

    private static boolean sturdy(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        BlockState s = level.getBlockState(n);
        return !(s.getBlock() instanceof BusbarBlock) && s.isFaceSturdy(level, n, d.getOpposite());
    }

    private static boolean plainBar(BlockState s) { return s.getBlock() instanceof BusbarBlock && !(s.getBlock() instanceof HullPenetratorBlock); }

    public static Direction mount(BlockGetter level, BlockPos pos, BlockState s) {
        if (!plainBar(s) || s.getValue(PLUG_U) || s.getValue(PLUG_D)) return null;
        boolean h = horizontal(s);
        if (!h && !s.getValue(UP) && !s.getValue(DOWN)) return null;
        if (h && sturdy(level, pos, Direction.DOWN)) return null;
        for (Direction w : Direction.Plane.HORIZONTAL) if (!s.getValue(prop(w.getOpposite())) && sturdy(level, pos, w)) return w;
        return null;
    }

    static Direction[] inner(BlockGetter level, BlockPos pos, BlockState s) {
        if (!plainBar(s) || s.getValue(PLUG_U) || s.getValue(PLUG_D) || sturdy(level, pos, Direction.DOWN)) return null;
        for (Direction w1 : Direction.Plane.HORIZONTAL) {
            if (s.getValue(prop(w1)) || !plain(s, w1.getOpposite()) || !sturdy(level, pos, w1)) continue;
            for (Direction w2 : new Direction[] { w1.getClockWise(), w1.getCounterClockWise() })
                if (!s.getValue(prop(w2)) && plain(s, w2.getOpposite()) && sturdy(level, pos, w2)) return new Direction[] { w1, w2 };
        }
        return null;
    }

    static Direction[] outer(BlockGetter level, BlockPos pos, BlockState s) {
        if (!plainBar(s) || s.getValue(UP) || s.getValue(DOWN) || sturdy(level, pos, Direction.DOWN)) return null;
        Direction a = null, b = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!s.getValue(prop(d))) continue;
            if (!plain(s, d)) return null;
            if (a == null) a = d; else if (b == null) b = d; else return null;
        }
        if (b == null || b.getAxis() == a.getAxis()) return null;
        BlockPos na = pos.relative(a), nb = pos.relative(b);
        if (mount(level, na, level.getBlockState(na)) != b || mount(level, nb, level.getBlockState(nb)) != a) return null;
        return new Direction[] { a, b };
    }

    private static Direction wallOf(BlockGetter level, BlockPos np, Direction toUs) {
        BlockState ns = level.getBlockState(np);
        Direction m = mount(level, np, ns);
        if (m != null) return m;
        Direction[] in = inner(level, np, ns);
        if (in != null) return toUs == in[0].getOpposite() ? in[1] : in[0];
        return null;
    }

    public record Part(int model, Direction mount, Direction point, float t0, float t1) {}
    public static final int P_CORE = 0, P_ARM = 1, P_PLUG = 2, P_ARM_UP = 3, P_ARM_DOWN = 4, P_PLUG_UP = 5, P_PLUG_DOWN = 6, P_CORE_SHORT = 7;

    private static final float SHORT_CORE = 4.3F;
    private static final float ARM_IN = 11.7F;

    private static void add(java.util.List<Part> l, int model, Direction mount, Direction point) { add(l, model, mount, point, ARM_IN, 16.0F); }

    private static void add(java.util.List<Part> l, int model, Direction mount, Direction point, float t0, float t1) {
        Part p = new Part(model, mount, point, t0, t1);
        if (!l.contains(p)) l.add(p);
    }

    public static java.util.List<Part> parts(BlockGetter level, BlockPos pos, BlockState s) {
        if (!plainBar(s)) return null;
        java.util.List<Part> l = new java.util.ArrayList<>(8);
        Direction w = mount(level, pos, s);
        if (w != null) {
            add(l, P_CORE, w, null);
            for (Direction d : Direction.values()) {
                if (!s.getValue(prop(d))) continue;
                if (d == w) add(l, P_PLUG_DOWN, w, null);
                else add(l, s.getValue(plugProp(d)) ? P_PLUG : P_ARM, w, d);
            }
            return l;
        }
        Direction[] in = inner(level, pos, s);
        if (in != null) {
            Direction w1 = in[0], w2 = in[1];
            add(l, P_CORE, w1, null);
            for (Direction d : new Direction[] { Direction.UP, Direction.DOWN, w2.getOpposite() }) if (s.getValue(prop(d))) add(l, s.getValue(plugProp(d)) ? P_PLUG : P_ARM, w1, d);
            add(l, P_ARM, w1, w2);
            add(l, P_ARM, w2, w1.getOpposite());
            add(l, P_ARM, w2, w1.getOpposite(), 4.0F, ARM_IN);
            return l;
        }
        Direction[] out = outer(level, pos, s);
        if (out != null) {
            add(l, P_ARM, out[1], out[0]);
            return l;
        }

        Direction[] bendWall = new Direction[6];
        boolean any = false;
        for (Direction d : Direction.values()) {
            if (!plain(s, d)) continue;
            Direction nw = wallOf(level, pos.relative(d), d.getOpposite());
            if (nw == null) continue;
            if (d.getAxis().isHorizontal() && (nw.getAxis() == d.getAxis() || !sturdy(level, pos, nw))) continue;
            bendWall[d.ordinal()] = nw;
            any = true;
        }
        if (!any) return null;
        add(l, P_CORE, Direction.DOWN, null);
        for (Direction d : Direction.Plane.HORIZONTAL) if (s.getValue(prop(d)) && bendWall[d.ordinal()] == null) add(l, s.getValue(plugProp(d)) ? P_PLUG : P_ARM, Direction.DOWN, d);
        for (Direction d : Direction.values()) {
            Direction nw = bendWall[d.ordinal()];
            if (nw == null) continue;
            if (!s.getValue(prop(nw))) add(l, P_ARM, Direction.DOWN, nw);
            if (d == Direction.DOWN) continue;
            add(l, P_CORE_SHORT, nw, null);
            add(l, P_ARM, nw, d);
        }
        if (s.getValue(PLUG_U)) add(l, P_PLUG_UP, Direction.DOWN, null);
        else if (s.getValue(UP) && bendWall[Direction.UP.ordinal()] == null) add(l, P_ARM_UP, Direction.DOWN, null);
        if (s.getValue(PLUG_D)) add(l, P_PLUG_DOWN, Direction.DOWN, null);
        else if (s.getValue(DOWN) && bendWall[Direction.DOWN.ordinal()] == null) add(l, P_ARM_DOWN, Direction.DOWN, null);
        return l;
    }

    private static BlockState withRun(BlockGetter level, BlockPos pos, BlockState s) {
        if (!s.hasProperty(WALLRUN)) return s;
        boolean r = parts(level, pos, s) != null;
        return s.getValue(WALLRUN) == r ? s : s.setValue(WALLRUN, r);
    }

    static float yaw(Direction d) {
        return d == null ? 0.0F : switch (d) { case EAST -> -90.0F; case SOUTH -> 180.0F; case WEST -> 90.0F; default -> 0.0F; };
    }

    static Direction floorPoint(Direction mount, Direction d) {
        if (d == null) return Direction.NORTH;
        if (mount == Direction.DOWN) return d.getAxis().isHorizontal() ? d : Direction.NORTH;
        if (d == Direction.UP) return Direction.NORTH;
        if (d == Direction.DOWN) return Direction.SOUTH;
        if (d.getAxis() == mount.getAxis()) return Direction.NORTH;
        Direction r = d;
        for (int i = (mount.get2DDataValue() + 2) & 3; i > 0; i--) r = r.getCounterClockWise();
        return r;
    }

    public static org.joml.Matrix4f partMatrix(Part p) {
        org.joml.Matrix4f m = new org.joml.Matrix4f().translate(0.5F, 0.5F, 0.5F);
        if (p.mount() != Direction.DOWN) {
            m.rotateY((float) Math.toRadians(yaw(p.mount())));
            m.rotateX((float) (Math.PI / 2));
        }
        m.rotateY((float) Math.toRadians(yaw(floorPoint(p.mount(), p.point()))));
        m.translate(-0.5F, -0.5F, -0.5F);
        if (p.model() == P_ARM && (p.t0() != ARM_IN || p.t1() != 16.0F)) {
            m.translate(0.0F, 0.0F, (16.0F - p.t1()) / 16.0F);
            m.scale(1.0F, 1.0F, (p.t1() - p.t0()) / (16.0F - ARM_IN));
        }
        if (p.model() == P_CORE_SHORT) m.scale(1.0F, SHORT_CORE / 5.4F, 1.0F);
        return m;
    }

    private static final float[][] PART_BOX = {
            { 4.3F, 0, 4.3F, 11.7F, 5.4F, 11.7F },
            { 5, 0, 0, 11, 4.5F, 4.3F },
            { 4, 0, 0.8F, 12, 12, 11.4F },
            { 5, 4.6F, 5, 11, 16, 11 },
            { 4, 0, 4, 12, 0.5F, 12 },
            { 4, 4, 4, 12, 16, 12 },
            { 4, 0, 4, 12, 11.4F, 12 },
            { 4.3F, 0, 4.3F, 11.7F, 5.4F, 11.7F } };

    private static final java.util.Map<java.util.List<Part>, VoxelShape> WALL_SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    private static VoxelShape wallShape(java.util.List<Part> parts) {
        return WALL_SHAPES.computeIfAbsent(java.util.List.copyOf(parts), ps -> {
            VoxelShape sh = Shapes.empty();
            for (Part p : ps) {
                float[] b = PART_BOX[p.model()];
                org.joml.Matrix4f m = partMatrix(p);
                org.joml.Vector3f c0 = m.transformPosition(new org.joml.Vector3f(b[0] / 16.0F, b[1] / 16.0F, b[2] / 16.0F));
                org.joml.Vector3f c1 = m.transformPosition(new org.joml.Vector3f(b[3] / 16.0F, b[4] / 16.0F, b[5] / 16.0F));
                sh = Shapes.or(sh, Shapes.box(clamp(Math.min(c0.x, c1.x)), clamp(Math.min(c0.y, c1.y)), clamp(Math.min(c0.z, c1.z)),
                        clamp(Math.max(c0.x, c1.x)), clamp(Math.max(c0.y, c1.y)), clamp(Math.max(c0.z, c1.z))));
            }
            return sh.optimize();
        });
    }

    private static double clamp(float v) { return Math.max(0.0, Math.min(1.0, Math.round(v * 1600.0F) / 1600.0)); }

    private static BlockState link(LevelReader level, BlockPos pos, BlockState state, Direction d) {
        boolean c = connects(level, pos, d);
        boolean plug = c && !(level.getBlockState(pos.relative(d)).getBlock() instanceof BusbarBlock);
        return state.setValue(prop(d), c).setValue(plugProp(d), plug);
    }

    public BlockState linkedAt(LevelReader level, BlockPos pos) { return withConnections(level, pos, defaultBlockState()); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean wet = context.getLevel().getFluidState(context.getClickedPos()).getType() == net.minecraft.world.level.material.Fluids.WATER;
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(WATERLOGGED, wet));
    }

    @Override
    protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        if (level instanceof Level) return withRun(level, pos, link(level, pos, state, direction));
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
