package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class DroneModuleItem extends BlockItem {

    public DroneModuleItem(DroneModuleBlock block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (((DroneModuleBlock) getBlock()).kind == DroneKind.CORE) return super.place(ctx);
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.drone_line.use_line_tab"), true);
        return InteractionResult.FAIL;
    }
}
