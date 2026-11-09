package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BatteryTierBlock extends UnitBlock {

    public final int tier;

    protected BatteryTierBlock(Properties properties, int tier, int x0, int x1, int y0, int y1, int z0, int z1) {
        super(properties, x0, x1, y0, y1, z0, z1);
        this.tier = tier;
    }

    abstract BlockEntityType<BatteryTierBlockEntity> type();

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BatteryTierBlockEntity(type(), pos, state, tier); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        return level.isClientSide ? createTickerHelper(t, type(), (l, p, s, be) -> { ProcessUnitBlockEntity.clientTick(l, p, s, be); be.clientFan(); })
                : createTickerHelper(t, type(), ProcessUnitBlockEntity::serverTick);
    }

    public static class T1 extends BatteryTierBlock {
        public static final MapCodec<T1> CODEC = simpleCodec(T1::new);
        public T1(Properties p) { super(p, 1, 0, 0, 0, 0, 0, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override BlockEntityType<BatteryTierBlockEntity> type() { return Wmd.BATTERY_T1_BE.get(); }
    }

    public static class T2 extends BatteryTierBlock {
        public static final MapCodec<T2> CODEC = simpleCodec(T2::new);
        public T2(Properties p) { super(p, 2, 0, 0, 0, 1, 0, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override BlockEntityType<BatteryTierBlockEntity> type() { return Wmd.BATTERY_T2_BE.get(); }
    }

    public static class T3 extends BatteryTierBlock {
        public static final MapCodec<T3> CODEC = simpleCodec(T3::new);
        public T3(Properties p) { super(p, 3, -1, 1, 0, 2, 0, 1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override BlockEntityType<BatteryTierBlockEntity> type() { return Wmd.BATTERY_T3_BE.get(); }
    }

    public static class T4 extends BatteryTierBlock {
        public static final MapCodec<T4> CODEC = simpleCodec(T4::new);
        public T4(Properties p) { super(p, 4, -2, 2, 0, 2, 0, 1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override BlockEntityType<BatteryTierBlockEntity> type() { return Wmd.BATTERY_T4_BE.get(); }
    }
}
