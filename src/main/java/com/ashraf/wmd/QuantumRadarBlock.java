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

public class QuantumRadarBlock extends UnitBlock {

    public static final MapCodec<QuantumRadarBlock> CODEC = simpleCodec(QuantumRadarBlock::new);

    public QuantumRadarBlock(Properties properties) {
        super(properties, QuantumRadarBlockEntity.X0, QuantumRadarBlockEntity.X1, QuantumRadarBlockEntity.Y0, QuantumRadarBlockEntity.Y1,
                QuantumRadarBlockEntity.Z0, QuantumRadarBlockEntity.Z1);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new QuantumRadarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, CloakBlocks.QRADAR_BE.get(), QuantumRadarBlockEntity::clientTickQr)
                : createTickerHelper(type, CloakBlocks.QRADAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof QuantumRadarBlockEntity be && !gate(level, controller, player, be)) return;
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.quantum_radar", String.format("%,d", (int) Balance.QR_RANGE), (int) Balance.QR_PLAYER_RANGE,
                String.format("%,d", Balance.QR_FE)).withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
