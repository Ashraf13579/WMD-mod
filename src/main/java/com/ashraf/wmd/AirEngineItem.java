package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public class AirEngineItem extends BlockItem {

    public AirEngineItem(AirEngineBlock block, Properties properties) { super(block, properties); }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
        int h = getBlock() instanceof AirEngineBlock ab ? ab.height() : 1;
        if (h <= 1) return ctx;
        BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace(), h - 1);
        if (!ctx.getLevel().isInWorldBounds(p)) return null;
        return BlockPlaceContext.at(ctx, p, ctx.getClickedFace());
    }
}
