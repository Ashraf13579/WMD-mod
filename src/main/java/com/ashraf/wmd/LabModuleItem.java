package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class LabModuleItem extends BlockItem {

    public LabModuleItem(LabModuleBlock block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (((LabModuleBlock) getBlock()).kind == LabKind.CORE) return super.place(ctx);
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.lab.use_line_tab"), true);
        return InteractionResult.FAIL;
    }
}
