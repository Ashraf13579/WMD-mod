package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class LinkCardItem extends Item {

    public LinkCardItem(Properties properties) { super(properties.stacksTo(16)); }

    private static CompoundTag tag(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos p = ctx.getClickedPos();
        var level = ctx.getLevel();
        if (level.getBlockEntity(p) instanceof MachinePartBlockEntity part && part.getController() != null) p = part.getController();
        var be = level.getBlockEntity(p);
        if (!(be instanceof MissileTeleporterBlockEntity) && !(be instanceof LaunchTerminalBlockEntity)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        ItemStack card = ctx.getItemInHand();
        if (be instanceof MissileTeleporterBlockEntity tp) {
            if (!Access.mayUse(tp, player)) { Access.deny(tp, player); return InteractionResult.FAIL; }
            final BlockPos at = p;
            CustomData.update(DataComponents.CUSTOM_DATA, card, t -> { t.putLong("tp", at.asLong()); t.putString("tpDim", sl.dimension().location().toString()); });
            sl.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("message.wmd.link_card.set", p.getX(), p.getY(), p.getZ()), true);
            return InteractionResult.SUCCESS;
        }
        CompoundTag t = tag(card);
        if (!t.contains("tp")) { player.displayClientMessage(Component.translatable("message.wmd.link_card.blank"), true); return InteractionResult.FAIL; }
        if (!t.getString("tpDim").equals(sl.dimension().location().toString())) { player.displayClientMessage(Component.translatable("message.wmd.link_card.dimension"), true); return InteractionResult.FAIL; }
        if (be instanceof Owned o && !Access.mayUse(o, player)) { Access.deny(o, player); return InteractionResult.FAIL; }
        BlockPos tpPos = BlockPos.of(t.getLong("tp"));
        if (!sl.isLoaded(tpPos) || !(sl.getBlockEntity(tpPos) instanceof MissileTeleporterBlockEntity tp)) {
            player.displayClientMessage(Component.translatable("message.wmd.link_card.no_tp"), true);
            return InteractionResult.FAIL;
        }
        int r = tp.addLink(p);
        player.displayClientMessage(Component.translatable("message.wmd.link_card.link." + r, Balance.TP_MAX_LINKS), true);
        if (r == 0) {
            if (!player.getAbilities().instabuild) card.shrink(1);
            sl.playSound(null, p, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.2F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = tag(stack);
        if (t.contains("tp")) {
            BlockPos p = BlockPos.of(t.getLong("tp"));
            tooltip.add(Component.translatable("tooltip.wmd.link_card.linked", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.AQUA));
        } else tooltip.add(Component.translatable("tooltip.wmd.link_card.blank").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.link_card", Balance.TP_MAX_LINKS).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) { return tag(stack).contains("tp"); }
}
