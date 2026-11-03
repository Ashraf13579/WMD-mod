package com.ashraf.wmd;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class SalvageSiteFeature extends Feature<NoneFeatureConfiguration> {

    public SalvageSiteFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!Config.salvageSites()) return false;
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        if (server.dimension() != Level.OVERWORLD) return false;
        ChunkPos cp = new ChunkPos(context.origin());
        SalvageSites.Site site = SalvageSites.siteForChunk(server, level.getSeed(), cp.x, cp.z);
        if (site == null) return false;

        BlockPos spawn = level.getLevelData().getSpawnPos();
        if (Math.hypot(site.centreX() - spawn.getX(), site.centreZ() - spawn.getZ()) < Balance.SALVAGE_SPAWN_CLEAR) return false;
        RandomSource random = RandomSource.create(level.getSeed() ^ ((long) cp.x * 0x9E3779B97F4A7C15L) ^ ((long) cp.z * 0xC2B2AE3D27D4EB4FL));
        return SalvageBuilder.build(level, site.kind(), site.centreX(), site.centreZ(), random);
    }
}
