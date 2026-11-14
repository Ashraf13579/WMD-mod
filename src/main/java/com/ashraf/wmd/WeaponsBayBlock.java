package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WeaponsBayBlock extends UnitBlock {

    public static final MapCodec<WeaponsBayBlock> CODEC = simpleCodec(WeaponsBayBlock::new);

    public WeaponsBayBlock(Properties properties) {
        super(properties, 0, 0, 0, 0, 0, 3);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() ? ctx.getClickedFace().getOpposite() : Direction.UP;
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WeaponsBayBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AirBlocks.WEAPONS_BAY_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, AirBlocks.WEAPONS_BAY_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof WeaponsBayBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        int sk = AirStores.kindOf(held);
        if (sk >= 0) {
            if (be.load(sk, AirStores.freqOf(held))) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, hit.getBlockPos(), WmdSounds.STATION_LOAD.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
            } else player.displayClientMessage(Component.translatable(be.accepts(sk) ? "message.wmd.station.full" : "message.wmd.station.wrong"), true);
            return;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            ItemStack back = be.unload();
            if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof WeaponsBayBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.weapons_bay").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.weapons_bay.join").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.station.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
