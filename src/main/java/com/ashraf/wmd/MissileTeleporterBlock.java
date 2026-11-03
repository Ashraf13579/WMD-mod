package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MissileTeleporterBlock extends MachineBlock {

    public static final MapCodec<MissileTeleporterBlock> CODEC = simpleCodec(MissileTeleporterBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 23; y++) for (int z = 0; z <= 6; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public MissileTeleporterBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof MissileTeleporterBlockEntity be) {
            if (!Access.mayUse(be, player)) { Access.deny(be, player); return; }
            TeleporterMenu.open(player, be);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level.getBlockEntity(pos) instanceof MissileTeleporterBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MissileTeleporterBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.MISSILE_TELEPORTER_BE.get(), MissileTeleporterBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.MISSILE_TELEPORTER_BE.get(), MissileTeleporterBlockEntity::serverTick);
    }
}
