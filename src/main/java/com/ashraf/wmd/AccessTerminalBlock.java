package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AccessTerminalBlock extends UnitBlock {

    public static final MapCodec<AccessTerminalBlock> CODEC = simpleCodec(AccessTerminalBlock::new);

    public AccessTerminalBlock(Properties properties) {
        super(properties, 0, 0, 0, 1, 0, 0);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AccessTerminalBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) { return null; }

    @Override
    public void interact(Level level, BlockPos controller, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof AccessTerminalBlockEntity be) {
            if (!gate(level, controller, player, be)) return;
            if ((player.getUUID().equals(be.getOwner()) || player.hasPermissions(2)) && CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(controller) instanceof AccessTerminalBlockEntity be)) return;
        if (!sp.getUUID().equals(be.getOwner()) && !sp.hasPermissions(2)) {
            sp.displayClientMessage(Component.translatable("message.wmd.access.owner_only"), true);
            return;
        }
        level.playSound(null, controller, WmdSounds.ASR_SWITCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.2F);
        AccessTerminalPayloads.sendState(sp, controller);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.access_terminal").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.access_terminal.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
