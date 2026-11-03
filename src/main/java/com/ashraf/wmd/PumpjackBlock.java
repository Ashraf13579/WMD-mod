package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PumpjackBlock extends MachineBlock {

    public static final MapCodec<PumpjackBlock> CODEC = simpleCodec(PumpjackBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -1; x <= 1; x++) for (int z = 0; z <= 7; z++) FOOTPRINT.add(new BlockPos(x, 0, z));
    }

    public PumpjackBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PumpjackBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.PUMPJACK_BE.get(), PumpjackBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.PUMPJACK_BE.get(), PumpjackBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown()) { MachineMenu.open(player, level.getBlockEntity(controller)); return; }
        if (!(level.getBlockEntity(controller) instanceof PumpjackBlockEntity be) || !(level instanceof ServerLevel sl)) return;
        List<BlockPos> cluster = OilFields.clusterBelow(sl, controller);
        if (cluster.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.wmd.pumpjack.no_oil"), true);
            return;
        }
        OilFields fields = OilFields.get(sl);
        int fieldMb = 0;
        for (BlockPos p : cluster) fieldMb += fields.remaining(p);
        player.displayClientMessage(Component.translatable("message.wmd.pumpjack.status",
                String.format("%.1f", be.getStoredMb() / 1000.0), String.format("%.1f", Balance.PUMPJACK_TANK_MB / 1000.0),
                String.format("%.0f", fieldMb / 1000.0), cluster.size(),
                Component.translatable(be.hasPower() ? "message.wmd.pumpjack.power_ok" : "message.wmd.pumpjack.no_power")), true);
    }
}
