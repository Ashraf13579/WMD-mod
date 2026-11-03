package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class BayModuleItem extends BlockItem {

    public BayModuleItem(BayModuleBlock block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        BayModuleBlock block = (BayModuleBlock) getBlock();
        if (block.kind == BayKind.CORE) return super.place(ctx);
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.bay.use_line_tab"), true);
        return InteractionResult.FAIL;
    }
}
