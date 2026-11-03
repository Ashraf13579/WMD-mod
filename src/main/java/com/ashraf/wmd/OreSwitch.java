package com.ashraf.wmd;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class OreSwitch extends PlacementModifier {

    public static final MapCodec<OreSwitch> CODEC = Codec.STRING.fieldOf("ore").xmap(OreSwitch::new, s -> s.ore);

    private final String ore;

    public OreSwitch(String ore) { this.ore = ore; }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return Config.oreGen(ore) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() { return Wmd.ORE_SWITCH.get(); }
}
