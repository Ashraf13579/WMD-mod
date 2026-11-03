package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ReactorRepairKitItem extends Item {

    public ReactorRepairKitItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos controller = level.getBlockEntity(pos) instanceof MachinePartBlockEntity part ? part.getController() : pos;

        if (controller != null && level.getBlockEntity(controller) instanceof NuclearReactorBlockEntity nr) {
            if (!level.isClientSide && ctx.getPlayer() != null && nr.repair(ctx.getPlayer()) && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (controller == null || !(level.getBlockEntity(controller) instanceof WarheadReactorBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide && ctx.getPlayer() != null && be.repair(ctx.getPlayer()) && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
