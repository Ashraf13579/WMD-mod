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

public class AirSearchRadarBlock extends UnitBlock {

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof AirSearchRadarBlockEntity be ? be.alarmSignal() : 0;
    }

    public static final MapCodec<AirSearchRadarBlock> CODEC = simpleCodec(AirSearchRadarBlock::new);

    public AirSearchRadarBlock(Properties properties) {
        super(properties, AirSearchRadarBlockEntity.X0, AirSearchRadarBlockEntity.X1, AirSearchRadarBlockEntity.Y0, AirSearchRadarBlockEntity.Y1,
                AirSearchRadarBlockEntity.Z0, AirSearchRadarBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirSearchRadarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, AirBlocks.AIR_SEARCH_RADAR_BE.get(), AirSearchRadarBlockEntity::clientTickRadar)
                : createTickerHelper(type, AirBlocks.AIR_SEARCH_RADAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof AirSearchRadarBlockEntity be) {
            if (!gate(level, controller, player, be)) return;
            if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.air_search_radar", String.format("%,d", (int) Balance.ASR_RANGE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
