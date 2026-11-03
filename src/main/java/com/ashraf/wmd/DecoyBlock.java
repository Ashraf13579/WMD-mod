package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class DecoyBlock extends UnitBlock {

    protected DecoyBlock(Properties properties, int x0, int x1, int y0, int y1, int z0, int z1) { super(properties, x0, x1, y0, y1, z0, z1); }

    protected abstract BlockEntityType<? extends DecoyBlockEntity> beType();

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, beType(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, beType(), ProcessUnitBlockEntity::serverTick);
    }

    public static class Launcher extends DecoyBlock {
        public static final MapCodec<Launcher> CODEC = simpleCodec(Launcher::new);
        public Launcher(Properties p) { super(p, DecoyBlockEntity.Launcher.X0, DecoyBlockEntity.Launcher.X1, DecoyBlockEntity.Launcher.Y0, DecoyBlockEntity.Launcher.Y1,
                DecoyBlockEntity.Launcher.Z0, DecoyBlockEntity.Launcher.Z1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DecoyBlockEntity.Launcher(pos, state); }
        @Override protected BlockEntityType<? extends DecoyBlockEntity> beType() { return Wmd.DECOY_LAUNCHER_BE.get(); }
    }

    public static class Radar extends DecoyBlock {
        public static final MapCodec<Radar> CODEC = simpleCodec(Radar::new);
        public Radar(Properties p) { super(p, DecoyBlockEntity.Radar.X0, DecoyBlockEntity.Radar.X1, DecoyBlockEntity.Radar.Y0, DecoyBlockEntity.Radar.Y1,
                DecoyBlockEntity.Radar.Z0, DecoyBlockEntity.Radar.Z1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DecoyBlockEntity.Radar(pos, state); }
        @Override protected BlockEntityType<? extends DecoyBlockEntity> beType() { return Wmd.DECOY_RADAR_BE.get(); }
    }
}
