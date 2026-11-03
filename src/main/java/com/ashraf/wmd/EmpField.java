package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class EmpField {

    private EmpField() {}

    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Map<Long, long[]>> CACHE = new HashMap<>();

    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Map<Long, long[]>> HEAT = new HashMap<>();

    static void clearStatics() { CACHE.clear(); HEAT.clear(); }

    public static final int REBOOT_SPREAD = 200;
    public static int rebootDelay(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return Math.floorMod(h, REBOOT_SPREAD);
    }

    public static boolean handheldDead(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level instanceof ServerLevel sl) || !jammed(sl, player.blockPosition())) return false;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.no_signal"), true);
        return true;
    }

    public static boolean jammed(ServerLevel level, BlockPos pos) {
        FlightManager mgr = FlightManager.get(level);
        if (mgr.tasks.isEmpty()) return false;
        boolean inside = false;
        net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, pos);
        for (WarheadFx.Task k : mgr.tasks) {
            if (k.kind != WarheadFx.EMP && k.kind != WarheadFx.HEMP_FIELD && k.kind != WarheadFx.CRYO_FIELD) continue;
            if (k.kind == WarheadFx.CRYO_FIELD && k.total - k.ticks > Balance.CRYO_THAW_HEAT && heatNear(level, pos)) continue;
            double dx = w.x - k.x, dz = w.z - k.z;
            if (dx * dx + dz * dz <= k.r * k.r && (k.kind != WarheadFx.HEMP_FIELD || k.ticks > rebootDelay(pos.getX(), pos.getZ()))) { inside = true; break; }
        }
        if (!inside) return false;
        return !shielded(level, pos);
    }

    public static boolean heatNear(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime(), key = pos.asLong();
        Map<Long, long[]> cache = HEAT.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long[] c = cache.get(key);
        if (c != null && c[0] > now && c[0] - now <= 20) return c[1] != 0L;
        boolean hot = scanHeat(level, pos);
        if (cache.size() > 16000) cache.clear();
        cache.put(key, new long[] { now + 20, hot ? 1L : 0L });
        return hot;
    }

    private static boolean scanHeat(ServerLevel level, BlockPos pos) {
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
            var s = level.getBlockState(q);
            if (s.is(net.minecraft.tags.BlockTags.FIRE) || s.is(net.minecraft.world.level.block.Blocks.LAVA) || s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) return true;
            if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && (s.is(net.minecraft.tags.BlockTags.CAMPFIRES) || s.is(net.minecraft.world.level.block.Blocks.FURNACE) || s.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE))) return true;
        }
        return false;
    }

    static boolean shielded(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime(), key = pos.asLong();
        Map<Long, long[]> cache = CACHE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long[] c = cache.get(key);
        if (c != null && c[0] > now && c[0] - now <= 20) return c[1] == 0L;
        boolean shielded = Hardened.layers(level, Vec3.atCenterOf(pos).add(0.0, 0.6, 0.0), Vec3.atCenterOf(pos).add(0.0, 24.0, 0.0)) >= 3;
        if (cache.size() > 16000) cache.clear();
        cache.put(key, new long[] { now + 20, shielded ? 0L : 1L });
        return shielded;
    }
}
