package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StorageTankBlock extends MachineBlock {

    public static final MapCodec<StorageTankBlock> CODEC = simpleCodec(StorageTankBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 7; y++) for (int z = 0; z <= 4; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public StorageTankBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public VoxelShape machineShape() { return FULL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageTankBlockEntity(pos, state); }

    @Override
    public float getExplosionResistance(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        return Config.Server.tankDamage() ? 3_600_000.0F : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.STORAGE_TANK_BE.get(), StorageTankBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.STORAGE_TANK_BE.get(), StorageTankBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown()) { MachineMenu.open(player, level.getBlockEntity(controller)); return; }
        if (!(level.getBlockEntity(controller) instanceof StorageTankBlockEntity be)) return;
        double mb = be.getStoredMb(), cap = Balance.STORAGE_TANK_MB;
        player.displayClientMessage(Component.translatable("message.wmd.storage_tank.status",
                be.fluidName(), String.format("%.1f", mb / 1000.0), String.format("%.0f", cap / 1000.0), String.format("%.0f", 100.0 * mb / cap)), true);
    }
}
