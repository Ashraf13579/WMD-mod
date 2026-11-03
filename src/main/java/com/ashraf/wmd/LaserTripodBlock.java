package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaserTripodBlock extends MachineBlock {

    public static final MapCodec<LaserTripodBlock> CODEC = simpleCodec(LaserTripodBlock::new);
    private static final List<BlockPos> FOOTPRINT = List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));

    public LaserTripodBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(controller) instanceof LaserTripodBlockEntity be) {
            PacketDistributor.sendToPlayer(sp, new TripodScopePayload(controller));
            sp.displayClientMessage(be.info(), true);
        }
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaserTripodBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.LASER_TRIPOD_BE.get(), LaserTripodBlockEntity::serverTick);
    }
}
