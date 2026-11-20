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

public class PulseEmpBlock extends UnitBlock {

    public static final MapCodec<PulseEmpBlock> CODEC = simpleCodec(PulseEmpBlock::new);

    public PulseEmpBlock(Properties properties) {
        super(properties, PulseEmpBlockEntity.X0, PulseEmpBlockEntity.X1, PulseEmpBlockEntity.Y0, PulseEmpBlockEntity.Y1, PulseEmpBlockEntity.Z0, PulseEmpBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PulseEmpBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, PulseEmpBlocks.PULSE_EMP_BE.get(), PulseEmpBlockEntity::clientTickEmp)
                : createTickerHelper(type, PulseEmpBlocks.PULSE_EMP_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof PulseEmpBlockEntity be) {
            if (!gate(level, controller, player, be)) return;
            if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.pulse_emp", (int) Balance.PEMP_RANGE, String.format("%,d", Balance.PEMP_CHARGE_FE / 1_000_000L), Balance.PEMP_COOLDOWN / 1200)
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
