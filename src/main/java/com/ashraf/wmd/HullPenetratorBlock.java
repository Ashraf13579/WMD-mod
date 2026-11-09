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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HullPenetratorBlock extends BusbarBlock {

    public static final MapCodec<HullPenetratorBlock> CODEC = simpleCodec(HullPenetratorBlock::new);

    public HullPenetratorBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return linkedAt(context.getLevel(), context.getClickedPos()).setValue(WATERLOGGED, false);
    }

    @Override
    protected FluidState getFluidState(BlockState state) { return Fluids.EMPTY.defaultFluidState(); }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) { return false; }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) { return false; }

    @Override
    public ItemStack pickupBlock(Player player, LevelAccessor level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return super.updateShape(state.setValue(WATERLOGGED, false), direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.hull_penetrator").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.deep_hull.rating", Balance.DEEP_FITTING_RATING).withStyle(ChatFormatting.GRAY));
    }
}
