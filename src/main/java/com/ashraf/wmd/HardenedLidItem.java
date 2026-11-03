package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class HardenedLidItem extends Item {

    public HardenedLidItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos c = level.getBlockEntity(pos) instanceof MachinePartBlockEntity part ? part.getController() : pos;
        if (c == null) return InteractionResult.PASS;
        var be = level.getBlockEntity(c);
        if (!(be instanceof InterceptorSiloBlockEntity) && !(be instanceof InterceptorSiloT4BlockEntity)) return InteractionResult.PASS;
        if (!level.isClientSide && ctx.getPlayer() != null) {
            boolean ok = be instanceof InterceptorSiloBlockEntity s ? s.harden() : ((InterceptorSiloT4BlockEntity) be).harden();
            if (ok) {
                if (!ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
                ctx.getPlayer().displayClientMessage(Component.translatable("msg.wmd.hardened_lid.fitted"), true);
            } else ctx.getPlayer().displayClientMessage(Component.translatable("msg.wmd.hardened_lid.already"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
