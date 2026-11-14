package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ServiceArmBlock extends UnitBlock {

    public static final MapCodec<ServiceArmBlock> CODEC = simpleCodec(ServiceArmBlock::new);

    public ServiceArmBlock(Properties properties) {
        super(properties, ServiceArmBlockEntity.X0, ServiceArmBlockEntity.X1, ServiceArmBlockEntity.Y0, ServiceArmBlockEntity.Y1, ServiceArmBlockEntity.Z0, ServiceArmBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ServiceArmBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ServiceBlocks.SERVICE_ARM_BE.get(), ServiceArmBlockEntity::clientTickArm)
                : createTickerHelper(type, ServiceBlocks.SERVICE_ARM_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ServiceArmBlockEntity be ? be.comparatorOutput() : 0;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof ServiceArmBlockEntity be && CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        super.interact(level, controller, player, hit);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.service_arm").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.service_arm.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
