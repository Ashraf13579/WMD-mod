package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CargoRampBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<CargoRampBlock> CODEC = simpleCodec(CargoRampBlock::new);
    public static final BooleanProperty HEAD = BooleanProperty.create("head"), LOW = BooleanProperty.create("low"), OPEN = BooleanProperty.create("open");

    private static final double[] SLAB = { 0, 0, 0, 16, 16, 4 }, SILL = { 0, 0, 0, 16, 4, 16 };
    private static final VoxelShape[] CLOSED = new VoxelShape[4], CLOSED_LOW = new VoxelShape[4], OPEN_LOW = new VoxelShape[4];
    static {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            int i = f.get2DDataValue();
            CLOSED[i] = box(SLAB, f);
            CLOSED_LOW[i] = Shapes.or(box(SLAB, f), box(new double[] { 0, 0, 4, 16, 4, 16 }, f));
            OPEN_LOW[i] = box(SILL, f);
        }
    }

    static VoxelShape box(double[] b, Direction f) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public CargoRampBlock(Properties properties) {
        super(properties, 0, 0, 0, 0, 0, 0);
        registerDefaultState(defaultBlockState().setValue(HEAD, true).setValue(LOW, true).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAD, LOW, OPEN);
    }

    @Override
    public boolean faceAway() { return true; }

    static boolean same(BlockGetter level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof CargoRampBlock && s.getValue(FACING) == facing;
    }

    static BlockPos left(BlockPos p, Direction facing) { return Machines.toWorld(p, facing, -1, 0, 0); }

    static boolean lowAt(BlockGetter level, BlockPos pos, Direction facing) { return !same(level, pos.below(), facing); }

    static boolean headAt(BlockGetter level, BlockPos pos, Direction facing) { return lowAt(level, pos, facing) && !same(level, left(pos, facing), facing); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        if (s == null) return null;
        if (Machines.mount(s) != Direction.UP) return null;
        Direction f = s.getValue(FACING);
        BlockPos p = context.getClickedPos();
        boolean open = false;
        for (BlockPos q : new BlockPos[] { left(p, f), Machines.toWorld(p, f, 1, 0, 0), p.below(), p.above() }) {
            BlockState n = context.getLevel().getBlockState(q);
            if (n.getBlock() instanceof CargoRampBlock && n.getValue(FACING) == f && n.getValue(OPEN)) open = true;
        }
        return s.setValue(HEAD, headAt(context.getLevel(), p, f)).setValue(LOW, lowAt(context.getLevel(), p, f)).setValue(OPEN, open);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState s = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (!(s.getBlock() instanceof CargoRampBlock)) return s;
        Direction f = s.getValue(FACING);
        return s.setValue(LOW, lowAt(level, pos, f)).setValue(HEAD, headAt(level, pos, f));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(FACING).get2DDataValue();
        if (state.getValue(OPEN)) return state.getValue(LOW) ? OPEN_LOW[i] : Shapes.empty();
        return state.getValue(LOW) ? CLOSED_LOW[i] : CLOSED[i];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getCollisionShape(state, level, pos, context);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CargoRampBlockEntity(pos, state); }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        CargoRampBlockEntity h = headOf(level, pos);
        return h == null ? 0 : h.comparatorOutput();
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(HEAD)) return null;
        return level.isClientSide ? createTickerHelper(type, CargoBlocks.CARGO_RAMP_BE.get(), (l, p, s, be) -> { ProcessUnitBlockEntity.clientTick(l, p, s, be); be.clientLoops(l); })
                : createTickerHelper(type, CargoBlocks.CARGO_RAMP_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    static CargoRampBlockEntity headOf(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof CargoRampBlock)) return null;
        Direction f = st.getValue(FACING);
        BlockPos p = pos;
        for (int i = 0; i < Balance.RAMP_W_MAX && same(level, left(p, f), f); i++) p = left(p, f);
        for (int i = 0; i < Balance.RAMP_H_MAX && same(level, p.below(), f); i++) p = p.below();
        return level.getBlockEntity(p) instanceof CargoRampBlockEntity h && h.getBlockState().getValue(HEAD) ? h : null;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        CargoRampBlockEntity be = headOf(level, controller);
        if (be == null) { player.displayClientMessage(Component.translatable("message.wmd.ramp.no_head"), true); return; }
        if (!gate(level, be.getBlockPos(), player, be)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        if (player.getMainHandItem().is(Wmd.WRENCH.get())) { player.displayClientMessage(be.cycleRows(), true); return; }
        if (player.isShiftKeyDown()) { player.displayClientMessage(be.status(), true); return; }
        MachineMenu.open(player, be);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        CargoRampBlockEntity be = headOf(level, controller);
        if (be != null) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.cargo_ramp", Balance.RAMP_W_MIN, Balance.RAMP_W_MAX, Balance.RAMP_H_MIN, Balance.RAMP_H_MAX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.cargo_ramp.use").withStyle(ChatFormatting.GRAY));
    }
}
