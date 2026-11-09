package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class Anechoic {

    private Anechoic() {}

    record Cov(double frac, int tiles, int surface, long time) {}

    private static final Map<UUID, Cov> CACHE = Statics.server(new ConcurrentHashMap<>());
    private static final Cov NONE = new Cov(0.0, 0, 0, 0L);

    public static double coverage(ServerLevel level, UUID ship) {
        return measureCached(level, ship).frac();
    }

    static Cov measureCached(ServerLevel level, UUID ship) {
        if (level == null || ship == null || !SableBridge.active()) return NONE;
        long now = level.getGameTime();
        Cov c = CACHE.get(ship);
        if (c != null && now - c.time() >= 0 && now - c.time() < Balance.ANECHOIC_RECHECK_TICKS) return c;
        c = measure(level, ship, now);
        if (CACHE.size() > 512) CACHE.clear();
        CACHE.put(ship, c);
        return c;
    }

    public static double cached(UUID ship) {
        Cov c = ship == null ? null : CACHE.get(ship);
        return c == null ? 0.0 : c.frac();
    }

    public static double noiseCut(ServerLevel level, UUID ship) { return Balance.ANECHOIC_MAX_DB * coverage(level, ship); }

    public static double pingFactor(ServerLevel level, UUID ship) { return pingFactorOf(coverage(level, ship)); }

    static double pingFactorOf(double coverage) { return 1.0 - Balance.ANECHOIC_PING_CUT * Math.max(0.0, Math.min(1.0, coverage)); }

    static void dirty(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        UUID id = SableBridge.shipIdAt(level, pos);
        if (id != null) CACHE.remove(id);
    }

    static double frac(int tiles, int surface) { return surface <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, tiles / (double) surface)); }

    private static boolean open(BlockState s) { return s.isAir() || s.getBlock() instanceof LiquidBlock; }

    static Cov measure(ServerLevel level, UUID ship, long now) {
        Block tile = Wmd.ANECHOIC_TILE.get();
        if (SableBridge.countBlocks(level, ship, s -> s.is(tile)) <= 0) return new Cov(0.0, 0, 0, now);
        int[] b = SableBridge.plotBox(level, ship);
        if (b == null) return new Cov(0.0, 0, 0, now);
        int x0 = b[0] - 1, y0 = b[1] - 1, z0 = b[2] - 1, sx = b[3] - b[0] + 3, sy = b[4] - b[1] + 3, sz = b[5] - b[2] + 3;
        long vol = (long) sx * sy * sz;
        if (sx <= 0 || sy <= 0 || sz <= 0 || vol > Balance.ANECHOIC_SCAN_MAX) {
            Wmd.LOGGER.debug("Anechoic: ship {} not measured ({} plot cells)", ship, vol);
            return new Cov(0.0, 0, 0, now);
        }
        Map<Long, LevelChunk> chunks = SableBridge.plotChunks(level, ship);
        int n = (int) vol;
        byte[] st = new byte[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            if (x != 0 && y != 0 && z != 0 && x != sx - 1 && y != sy - 1 && z != sz - 1) continue;
            int i = (x * sy + y) * sz + z;
            st[i] = 1;
            queue[tail++] = i;
        }
        int surface = 0, tiles = 0;
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (head < tail) {
            int i = queue[head++];
            int x = i / (sy * sz), y = (i / sz) % sy, z = i % sz;
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                int j = (nx * sy + ny) * sz + nz;
                if (st[j] != 0) continue;
                p.set(x0 + nx, y0 + ny, z0 + nz);
                LevelChunk ch = chunks.get(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
                BlockState s = ch == null ? null : ch.getBlockState(p);
                if (s == null || open(s)) { st[j] = 1; queue[tail++] = j; continue; }
                st[j] = 2;
                surface++;
                if (s.is(tile)) tiles++;
            }
        }
        return new Cov(frac(tiles, surface), tiles, surface, now);
    }

    public static class Tile extends Block {
        public static final MapCodec<Tile> CODEC = simpleCodec(Tile::new);
        public Tile(Properties p) { super(p); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state, level, pos, old, moved);
            if (!old.is(this)) dirty(level, pos);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
            if (!now.is(this)) dirty(level, pos);
            super.onRemove(state, level, pos, now, moved);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.wmd.anechoic_tile.effect", (int) Balance.ANECHOIC_MAX_DB, (int) Math.round(Balance.ANECHOIC_PING_CUT * 100)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.wmd.anechoic_tile.how").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.wmd.deep_hull.rating", Balance.ANECHOIC_RATING).withStyle(ChatFormatting.GRAY));
            if (!DeepSeasBridge.loaded()) tooltip.add(Component.translatable("tooltip.wmd.deep_hull.no_ds").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
