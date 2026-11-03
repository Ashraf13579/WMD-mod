package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SorterBlock extends BaseEntityBlock {

    public static final MapCodec<SorterBlock> CODEC = simpleCodec(SorterBlock::new);

    public enum Face implements StringRepresentable {
        OFF, INPUT, OUTPUT;
        @Override public String getSerializedName() { return name().toLowerCase(); }
    }

    public static final EnumProperty<Face> NORTH = EnumProperty.create("north", Face.class), EAST = EnumProperty.create("east", Face.class),
            SOUTH = EnumProperty.create("south", Face.class), WEST = EnumProperty.create("west", Face.class),
            UP = EnumProperty.create("up", Face.class), DOWN = EnumProperty.create("down", Face.class);

    private static final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 14, 14);

    public SorterBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any();
        for (Direction d : Direction.values()) s = s.setValue(prop(d), Face.OFF);
        registerDefaultState(s);
    }

    public static EnumProperty<Face> prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof SorterBlockEntity be) be.claim(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SorterBlockEntity be) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!Access.mayUse(be, player)) { Access.deny(be, player); return InteractionResult.SUCCESS; }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new SorterMenu(id, inv, pos), state.getBlock().getName()), buf -> buf.writeBlockPos(pos));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide) DuctNet.invalidateAround(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            if (level.getBlockEntity(pos) instanceof SorterBlockEntity be && !be.buffer().isEmpty())
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.buffer().copy());
            DuctNet.invalidateAround(level, pos);
        } else if (!level.isClientSide) {
            DuctNet.invalidateAround(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SorterBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.SORTER_BE.get(), SorterBlockEntity::serverTick);
    }
}
