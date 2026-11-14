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

public class OrdnanceLoaderBlock extends UnitBlock {

    public static final MapCodec<OrdnanceLoaderBlock> CODEC = simpleCodec(p -> new OrdnanceLoaderBlock(p, false));

    private final boolean heavy;

    public OrdnanceLoaderBlock(Properties properties, boolean heavy) {
        super(properties, 0, heavy ? 2 : 1, 0, 0, 0, heavy ? 3 : 2);
        this.heavy = heavy;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OrdnanceLoaderBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<OrdnanceLoaderBlockEntity> t = heavy ? AirBlocks.HEAVY_LOADER_BE.get() : AirBlocks.ORDNANCE_LOADER_BE.get();
        return level.isClientSide ? createTickerHelper(type, t, ProcessUnitBlockEntity::clientTick) : createTickerHelper(type, t, ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof OrdnanceLoaderBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        interact(level, controller, player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(controller) instanceof OrdnanceLoaderBlockEntity be)) return;
        if (player.getMainHandItem().isEmpty() && player.isShiftKeyDown()) {
            String k = be.demo(player);
            if (k != null) { player.displayClientMessage(Component.translatable(k), true); return; }
        }
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(heavy ? "tooltip.wmd.heavy_loader" : "tooltip.wmd.ordnance_loader",
                String.format("%.1f", heavy ? Balance.HEAVY_LOADER_REACH : Balance.LOADER_REACH), (int) Balance.AD_RANGE).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
