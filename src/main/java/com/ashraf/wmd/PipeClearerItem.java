package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PipeClearerItem extends Item {

    private static final int MAX_PIPES = 4096;

    public PipeClearerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos start = ctx.getClickedPos();
        if (!(level.getBlockState(start).getBlock() instanceof FluidPipeBlock)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        int cleared = 0;
        while (!todo.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos p = todo.poll();
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof FluidPipeBlock)) continue;
            if (level.getBlockEntity(p) instanceof FluidPipeBlockEntity be) { be.clear(); cleared++; }
            for (Direction d : Direction.values()) {
                if (st.getValue(FluidPipeBlock.prop(d)) != FluidPipeBlock.Conn.PIPE) continue;
                BlockPos n = p.relative(d);
                if (seen.add(n)) todo.add(n);
            }
        }
        level.playSound(null, start, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 0.7F);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("item.wmd.pipe_clearer.done", cleared), true);
            ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return InteractionResult.CONSUME;
    }
}
