package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class TorpedoLineItem extends BlockItem {

    public TorpedoLineItem(TorpedoLineBlock block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (((TorpedoLineBlock) getBlock()).kind == TorpedoLineKind.SHOP) return super.place(ctx);
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.torpedo_line.use_line_tab"), true);
        return InteractionResult.FAIL;
    }
}
