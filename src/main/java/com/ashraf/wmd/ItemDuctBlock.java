package com.ashraf.wmd;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ItemDuctBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<ItemDuctBlock> CODEC = simpleCodec(ItemDuctBlock::new);

    public enum Conn implements StringRepresentable {
        NONE, DUCT, INSERT, EXTRACT, OFF;
        @Override public String getSerializedName() { return name().toLowerCase(); }
        public boolean inventory() { return this == INSERT || this == EXTRACT || this == OFF; }
    }

    public static final EnumProperty<Conn> NORTH = EnumProperty.create("north", Conn.class), EAST = EnumProperty.create("east", Conn.class),
            SOUTH = EnumProperty.create("south", Conn.class), WEST = EnumProperty.create("west", Conn.class),
            UP = EnumProperty.create("up", Conn.class), DOWN = EnumProperty.create("down", Conn.class);

    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape[] ARMS = new VoxelShape[6];

    static {
        ARMS[Direction.NORTH.ordinal()] = Block.box(4, 4, 0, 12, 12, 4);
        ARMS[Direction.SOUTH.ordinal()] = Block.box(4, 4, 12, 12, 12, 16);
        ARMS[Direction.EAST.ordinal()] = Block.box(12, 4, 4, 16, 12, 12);
        ARMS[Direction.WEST.ordinal()] = Block.box(0, 4, 4, 4, 12, 12);
        ARMS[Direction.UP.ordinal()] = Block.box(4, 12, 4, 12, 16, 12);
        ARMS[Direction.DOWN.ordinal()] = Block.box(4, 0, 4, 12, 4, 12);
    }

    public ItemDuctBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any().setValue(Waterlog.W, false);
        for (Direction d : Direction.values()) s = s.setValue(prop(d), Conn.NONE);
        registerDefaultState(s);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, Waterlog.W); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    static UUID ductOwner(Level level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof ItemDuctBlockEntity d) return d.getOwner();
        if (be instanceof SorterBlockEntity s) return s.getOwner();
        return null;
    }

    static boolean isWmd(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(Wmd.MODID); }

    static Conn connection(Level level, BlockPos pos, Direction d, UUID owner, Conn old) {
        BlockPos np = pos.relative(d);
        BlockState ns = level.getBlockState(np);
        if (ns.getBlock() instanceof ItemDuctBlock || ns.getBlock() instanceof SorterBlock) {
            UUID o = ductOwner(level, np);
            return owner == null || o == null || owner.equals(o) ? Conn.DUCT : Conn.NONE;
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, np, d.getOpposite()) == null) return Conn.NONE;
        if (old != null && old.inventory()) return old;
        if (ns.getBlock() instanceof SupplyInterfaceBlock) return Conn.EXTRACT;
        return isWmd(ns) ? Conn.INSERT : Conn.OFF;
    }

    private BlockState withConnections(Level level, BlockPos pos, BlockState state, UUID owner) {
        for (Direction d : Direction.values()) state = state.setValue(prop(d), connection(level, pos, d, owner, state.getValue(prop(d))));
        return state;
    }

    private static UUID ownerOf(Level level, BlockPos pos) { return level.getBlockEntity(pos) instanceof ItemDuctBlockEntity d ? d.getOwner() : null; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), Waterlog.placed(defaultBlockState(), context.getLevel(), context.getClickedPos()), context.getPlayer() != null ? context.getPlayer().getUUID() : null);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player p) || !(level.getBlockEntity(pos) instanceof ItemDuctBlockEntity be)) return;
        be.claim(p);
        BlockState next = withConnections(level, pos, state, p.getUUID());
        if (next != state) level.setBlock(pos, next, 3);
        next.updateNeighbourShapes(level, pos, 3);
        DuctNet.invalidateAround(level, pos);
    }

    @Override
    protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        net.minecraft.world.level.material.FluidState f = Waterlog.fluid(state);
        return f != null ? f : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        if (level instanceof Level l) {
            BlockState next = withConnections(l, pos, state, ownerOf(l, pos));
            if (next != state && !l.isClientSide) DuctNet.invalidateAround(l, pos);
            return next;
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        BlockState next = withConnections(level, pos, state, ownerOf(level, pos));
        if (next != state) level.setBlock(pos, next, 2);
        if (next != state && !level.isClientSide) DuctNet.invalidateAround(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            if (!ShipRelink.shipMove(level, movedByPiston) && level.getBlockEntity(pos) instanceof ItemDuctBlockEntity be)
                for (ItemStack s : be.takeTransit()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            DuctNet.invalidateAround(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static Direction clickedSide(BlockState state, BlockPos pos, Vec3 hit) {
        Vec3 r = hit.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction best = null;
        double bestDot = 0.05;
        for (Direction d : Direction.values()) {
            if (!state.getValue(prop(d)).inventory()) continue;
            double dot = r.x * d.getStepX() + r.y * d.getStepY() + r.z * d.getStepZ();
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Wmd.WRENCH.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ItemDuctBlockEntity be && !Access.mayUse(be, player)) { Access.deny(be, player); return ItemInteractionResult.SUCCESS; }
        Direction d = clickedSide(state, pos, hit.getLocation());
        if (d == null) { player.displayClientMessage(Component.translatable("message.wmd.duct.no_side"), true); return ItemInteractionResult.SUCCESS; }
        Conn next = switch (state.getValue(prop(d))) { case INSERT -> Conn.EXTRACT; case EXTRACT -> Conn.OFF; default -> Conn.INSERT; };
        level.setBlock(pos, state.setValue(prop(d), next), 3);
        DuctNet.invalidateAround(level, pos);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, next == Conn.OFF ? 0.8F : 1.2F);
        player.displayClientMessage(Component.translatable("message.wmd.duct.mode." + next.getSerializedName(), d.getName()), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ItemDuctBlockEntity be) player.displayClientMessage(be.info(), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ItemDuctBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.ITEM_DUCT_BE.get(), ItemDuctBlockEntity::serverTick);
    }
}
