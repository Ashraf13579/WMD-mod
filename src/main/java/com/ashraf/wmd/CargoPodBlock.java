package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CargoPodBlock extends UnitBlock {

    public static final MapCodec<CargoPodBlock> CODEC = simpleCodec(CargoPodBlock::new);

    public CargoPodBlock(Properties properties) { super(properties, -1, 1, 0, 2, -1, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CargoPodBlockEntity(pos, state); }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(controller) instanceof CargoPodBlockEntity be)) return;
        if (be.arriving(level.getGameTime())) { sp.displayClientMessage(Component.translatable("message.wmd.cargo.docking").withStyle(ChatFormatting.YELLOW), true); return; }
        CargoPodMenu.openBlock(sp, be, false);
    }
}
