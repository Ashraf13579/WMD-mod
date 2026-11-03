package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SwarmDronePackItem extends Item {

    public SwarmDronePackItem(Properties properties) { super(properties.stacksTo(16)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.getBlockEntity(pos) instanceof MachinePartBlockEntity part && part.getController() != null) pos = part.getController();
        if (!(level.getBlockEntity(pos) instanceof SwarmLauncherBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        int took = be.load(ctx.getPlayer() != null && ctx.getPlayer().isCreative() ? Balance.SW_CELLS / Balance.SW_PACK : stack.getCount());
        if (ctx.getPlayer() != null) {
            if (took > 0 && !ctx.getPlayer().isCreative()) stack.shrink(took);
            ctx.getPlayer().displayClientMessage(took > 0 ? Component.translatable("message.wmd.swarm.loaded", be.loaded(), Balance.SW_CELLS).withStyle(ChatFormatting.AQUA)
                    : Component.translatable("message.wmd.swarm.full").withStyle(ChatFormatting.GRAY), true);
        }
        if (took > 0) level.playSound(null, pos, WmdSounds.SW_HATCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.swarm_drone_pack", Balance.SW_PACK).withStyle(ChatFormatting.GRAY));
    }
}
