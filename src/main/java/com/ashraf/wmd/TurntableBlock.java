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

public class TurntableBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<TurntableBlock> CODEC = simpleCodec(TurntableBlock::new);
    public static final BooleanProperty HEAD = BooleanProperty.create("head"), OPEN = BooleanProperty.create("open");

    public TurntableBlock(Properties properties) {
        super(properties, 0, 0, 0, 0, 0, 0);
        registerDefaultState(defaultBlockState().setValue(HEAD, true).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAD, OPEN);
    }

    static boolean headAt(BlockGetter level, BlockPos pos) {
        return !(level.getBlockState(pos.west()).getBlock() instanceof TurntableBlock) && !(level.getBlockState(pos.north()).getBlock() instanceof TurntableBlock);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        if (s == null) return null;
        if (Machines.mount(s) != Direction.UP) return null;
        return s.setValue(HEAD, headAt(context.getLevel(), context.getClickedPos())).setValue(OPEN, false);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level.isClientSide || !state.is(this)) return;

        if (headAt(level, pos) != state.getValue(HEAD)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        boolean h = headAt(level, pos);
        if (h != state.getValue(HEAD)) level.setBlock(pos, state.setValue(HEAD, h), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState s = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (direction == Direction.WEST || direction == Direction.NORTH) s = s.setValue(HEAD, headAt(level, pos));
        return s;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (state.getValue(OPEN)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.wmd.turntable.busy"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        return state.getValue(OPEN) ? 3_600_000.0F : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Block.box(0, 14, 0, 16, 16, 16) : Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TurntableBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(HEAD)) return null;
        return level.isClientSide ? createTickerHelper(type, TurntableBlocks.TURNTABLE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, TurntableBlocks.TURNTABLE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    static TurntableBlockEntity headOf(Level level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i <= Balance.TT_MAX && level.getBlockState(p.west()).getBlock() instanceof TurntableBlock; i++) p = p.west();
        for (int i = 0; i <= Balance.TT_MAX && level.getBlockState(p.north()).getBlock() instanceof TurntableBlock; i++) p = p.north();
        return level.getBlockEntity(p) instanceof TurntableBlockEntity h && h.getBlockState().getValue(HEAD) ? h : null;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        TurntableBlockEntity be = headOf(level, controller);
        if (be == null) { player.displayClientMessage(Component.translatable("message.wmd.turntable.no_head"), true); return; }
        if (!gate(level, be.getBlockPos(), player, be)) return;
        if (player.isShiftKeyDown() && !player.getMainHandItem().is(Wmd.WRENCH.get())) { player.displayClientMessage(be.status(), true); return; }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        MachineMenu.open(player, be);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        TurntableBlockEntity be = headOf(level, controller);
        if (be != null) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.turntable", Balance.TT_MIN, Balance.TT_MIN, Balance.TT_MAX, Balance.TT_MAX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.turntable.use").withStyle(ChatFormatting.GRAY));
    }
}
