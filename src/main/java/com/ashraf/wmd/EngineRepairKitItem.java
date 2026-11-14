package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EngineRepairKitItem extends Item {

    public EngineRepairKitItem(Item.Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = level.getBlockEntity(part.getController());
        var p = ctx.getPlayer();
        if (be instanceof TankDamage.Holder t) {
            if (!t.tankAtRest(level)) {
                if (p != null) p.displayClientMessage(Component.translatable("message.wmd.engine.repair_moving").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            if (!t.tankRepair(level)) {
                if (p != null) p.displayClientMessage(Component.translatable("message.wmd.tank.repair_none"), true);
                return InteractionResult.FAIL;
            }
            if (p != null) {
                p.displayClientMessage(Component.translatable("message.wmd.tank.repaired").withStyle(ChatFormatting.GREEN), true);
                if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            }
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
            return InteractionResult.SUCCESS;
        }
        if (!(be instanceof AirEngineBlockEntity e)) return InteractionResult.PASS;
        if (!e.atRest()) {
            if (p != null) p.displayClientMessage(Component.translatable("message.wmd.engine.repair_moving").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!e.repair()) {
            if (p != null) p.displayClientMessage(Component.translatable("message.wmd.engine.repair_none"), true);
            return InteractionResult.FAIL;
        }
        if (p != null) {
            p.displayClientMessage(Component.translatable("message.wmd.engine.repaired").withStyle(ChatFormatting.GREEN), true);
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.engine_repair_kit").withStyle(ChatFormatting.GRAY));
    }
}
