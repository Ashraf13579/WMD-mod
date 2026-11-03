package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public final class SalvageSites {

    private SalvageSites() {}

    public enum Kind {
        RADAR_STATION("radar_station"), SILO("silo"), SATELLITE("satellite");

        public final String id;

        Kind(String id) { this.id = id; }

        public static Kind byId(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }
    }

    public record Site(Kind kind, int chunkX, int chunkZ) {
        public int centreX() { return chunkX * 16 + 8; }
        public int centreZ() { return chunkZ * 16 + 8; }
    }

    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "has_salvage_sites"));

    static final int CANDIDATES = 4;
    static final int MARGIN = 8;

    static int regionChunks() { return Math.max(4 * MARGIN, Balance.SALVAGE_REGION_BLOCKS / 16); }

    public static int regionOf(int chunk) { return Math.floorDiv(chunk, regionChunks()); }

    private static int[] candidates(long seed, int rx, int rz, Kind[] kindOut) {
        int rc = regionChunks();
        RandomSource r = RandomSource.create(seed ^ (rx * 341873128712L) ^ (rz * 132897987541L) ^ 0x5A1_7A6E_0B3L);
        kindOut[0] = Kind.values()[r.nextInt(Kind.values().length)];
        int[] c = new int[CANDIDATES * 2];
        for (int i = 0; i < CANDIDATES; i++) {
            c[2 * i] = rx * rc + MARGIN + r.nextInt(rc - 2 * MARGIN);
            c[2 * i + 1] = rz * rc + MARGIN + r.nextInt(rc - 2 * MARGIN);
        }
        return c;
    }

    private static Holder<Biome> noiseBiome(ServerLevel level, int x, int y, int z) {
        var cs = level.getChunkSource();
        return cs.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), cs.randomState().sampler());
    }

    static boolean suitable(ServerLevel level, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        if ((double) x * x + (double) z * z < (double) Balance.SALVAGE_SPAWN_CLEAR * Balance.SALVAGE_SPAWN_CLEAR) return false;
        return noiseBiome(level, x, level.getSeaLevel() + 4, z).is(BIOMES);
    }

    public static Site siteOf(ServerLevel level, long seed, int rx, int rz) {
        Kind[] k = new Kind[1];
        int[] c = candidates(seed, rx, rz, k);
        for (int i = 0; i < CANDIDATES; i++) if (suitable(level, c[2 * i], c[2 * i + 1])) return new Site(k[0], c[2 * i], c[2 * i + 1]);
        return null;
    }

    public static Site siteForChunk(ServerLevel level, long seed, int chunkX, int chunkZ) {
        int rx = regionOf(chunkX), rz = regionOf(chunkZ);
        Kind[] k = new Kind[1];
        int[] c = candidates(seed, rx, rz, k);
        int mine = -1;
        for (int i = 0; i < CANDIDATES; i++) if (c[2 * i] == chunkX && c[2 * i + 1] == chunkZ) { mine = i; break; }
        if (mine < 0) return null;
        for (int i = 0; i <= mine; i++) {
            if (suitable(level, c[2 * i], c[2 * i + 1])) return i == mine ? new Site(k[0], chunkX, chunkZ) : null;
        }
        return null;
    }

    public static Site nearest(ServerLevel level, BlockPos from, int rings) {
        int rx0 = regionOf(from.getX() >> 4), rz0 = regionOf(from.getZ() >> 4);
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -rings; dx <= rings; dx++) for (int dz = -rings; dz <= rings; dz++) {
            Site s = siteOf(level, level.getSeed(), rx0 + dx, rz0 + dz);
            if (s == null) continue;
            double d = Math.hypot(s.centreX() - from.getX(), s.centreZ() - from.getZ());
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }
}
