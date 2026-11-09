package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PeriscopeBlock extends UnitBlock {

    public static final MapCodec<PeriscopeBlock> CODEC = simpleCodec(PeriscopeBlock::new);

    public PeriscopeBlock(Properties properties) { super(properties, 0, 0, 0, 2, 0, 0); }

    @Override public boolean hullPlug(BlockPos local) { return local.getY() == 2; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PeriscopeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.PERISCOPE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.PERISCOPE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof PeriscopeBlockEntity be && be.canLook()
                && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new PeriscopePayload(controller));
            return;
        }
        super.interact(level, controller, player);
    }
}
