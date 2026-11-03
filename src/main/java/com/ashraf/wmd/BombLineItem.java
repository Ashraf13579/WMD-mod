package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class BombLineItem extends BlockItem {

    public BombLineItem(BombLineBlock block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (((BombLineBlock) getBlock()).kind == BombLineKind.CORE) return super.place(ctx);
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.bomb_line.use_line_tab"), true);
        return InteractionResult.FAIL;
    }
}
