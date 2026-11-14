package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CargoHookBlock extends P6DriveBlock {

    public static final MapCodec<CargoHookBlock> LIGHT_CODEC = simpleCodec(p -> new CargoHookBlock(p, false)), HEAVY_CODEC = simpleCodec(p -> new CargoHookBlock(p, true));
    private final boolean heavy;

    public CargoHookBlock(Properties properties, boolean heavy) {
        super(properties, heavy ? "cargo_hook_heavy" : "cargo_hook_light", heavy ? 2 : 1, 1, heavy ? 2 : 1, false);
        this.heavy = heavy;
    }

    boolean heavy() { return heavy; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return heavy ? HEAVY_CODEC : LIGHT_CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return heavy ? AirBlocks.CARGO_HOOK_HEAVY_BE.get() : AirBlocks.CARGO_HOOK_LIGHT_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newCargoHook(pos, state); }
}
