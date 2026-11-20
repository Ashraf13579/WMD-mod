package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WardenState extends SavedData {

    static final String NAME = "wmd_warden";
    public static final int NONE = 0, SEARCH = 1, LAY = 2, ARMED = 3, FIGHT = 4, DOWN = 5;
    static final String[] PHASES = {"no bunker", "looking for a spot", "building / repairing", "armed (the Warden sleeps until the uplink is hacked)",
            "fighting", "beaten (waiting for the respawn)"};

    private static final long OWNER = 0x5741_5244_454EL;
    private static final int UNMEASURED = Integer.MIN_VALUE;

    int phase = NONE;
    boolean placed, gaveUp, repair;
    int ox, oy = UNMEASURED, oz;
    long lastKillMs;
    int tries;

    private final Set<Long> held = new HashSet<>();
    private boolean cand, haveBest;
    private int candX, candZ, bestX, bestY, bestZ, bestRough = Integer.MAX_VALUE;
    private int cursor, written;

    static WardenState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WardenState::new, WardenState::load, null), NAME);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;
        get(event.getServer()).tick(level);
    }

    void tick(ServerLevel level) {
        switch (phase) {
            case NONE -> {
                if (!placed && !gaveUp && Config.wardenBunker() && level.getGameTime() % 20 == 0) { phase = SEARCH; tries = 0; setDirty(); }
            }
            case SEARCH -> search(level);
            case LAY -> lay(level);
            case ARMED, FIGHT -> {
                if (level.getGameTime() % 20 == 0) WardenFight.keep(level, this);
            }
            case DOWN -> {
                if (level.getGameTime() % 20 == 0 && System.currentTimeMillis() >= lastKillMs + Balance.WB_RESPAWN_MS) startLay(true);
            }
            default -> { }
        }
    }

    private void search(ServerLevel level) {
        if (!cand) pick(level);
        if (!loaded3x3(level, candX, candZ)) return;
        int[] ys = new int[9];
        boolean wet = false;
        int i = 0;
        for (int sx = -14; sx <= 14; sx += 14)
            for (int sz = -14; sz <= 14; sz += 14) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candX + sx, candZ + sz) - 1;
                ys[i++] = y;
                if (!level.getFluidState(new BlockPos(candX + sx, y, candZ + sz)).isEmpty()) wet = true;
            }
        Arrays.sort(ys);
        int rough = ys[8] - ys[0], median = ys[4];
        releaseAll(level);
        cand = false;
        tries++;
        setDirty();
        boolean fits = fits(level, median);
        if (!wet && fits && rough <= Balance.WB_ROUGH_MAX) { site(candX, median, candZ, level); return; }
        if (!wet && fits && rough < bestRough) { haveBest = true; bestRough = rough; bestX = candX; bestY = median; bestZ = candZ; }
        if (tries >= Balance.WB_TRIES && haveBest) { site(bestX, bestY, bestZ, level); return; }
        if (tries >= Balance.WB_TRIES * 4) {
            gaveUp = true;
            phase = NONE;
            Wmd.LOGGER.warn("[warden] no dry ground for the bunker after {} tries; an admin can place it with /wmd boss place", tries);
        }
    }

    private void pick(ServerLevel level) {
        BlockPos sp = level.getSharedSpawnPos();
        RandomSource r = RandomSource.create(level.getSeed() ^ 0x5741_5244_454EL ^ (long) tries * 0x9E37_79B9_7F4A_7C15L);
        int range = Balance.WB_RANGE, min = Balance.WB_MIN_FROM_SPAWN;
        for (int n = 0; n < 64; n++) {
            int dx = r.nextInt(2 * range + 1) - range, dz = r.nextInt(2 * range + 1) - range;
            if (Math.abs(dx) < min && Math.abs(dz) < min) continue;
            candX = sp.getX() + dx;
            candZ = sp.getZ() + dz;
            cand = true;
            return;
        }
        candX = sp.getX() + range;
        candZ = sp.getZ();
        cand = true;
    }

    private static boolean fits(ServerLevel level, int sy) {
        return sy > level.getSeaLevel() && sy + WardenBunker.H0 > level.getMinBuildHeight() + 2 && sy + WardenBunker.H1 < level.getMaxBuildHeight() - 2;
    }

    private void site(int x, int y, int z, ServerLevel level) {
        ox = x; oy = y; oz = z;
        Wmd.LOGGER.info("[warden] bunker site at {} {} {} (after {} tries)", x, y, z, tries);
        startLay(false);
    }

    void startLay(boolean asRepair) {
        phase = LAY;
        repair = asRepair;
        cursor = 0;
        written = 0;
        setDirty();
    }

    void placeAt(ServerLevel level, int x, int z) {
        releaseAll(level);
        cand = false;
        ox = x; oz = z; oy = UNMEASURED;
        gaveUp = false;
        startLay(false);
    }

    private void lay(ServerLevel level) {
        if (oy == UNMEASURED) {
            if (!loaded3x3(level, ox, oz)) return;
            int[] ys = new int[9];
            int i = 0;
            for (int sx = -14; sx <= 14; sx += 14)
                for (int sz = -14; sz <= 14; sz += 14) ys[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox + sx, oz + sz) - 1;
            Arrays.sort(ys);
            oy = Math.max(level.getMinBuildHeight() - WardenBunker.H0 + 3, Math.min(level.getMaxBuildHeight() - WardenBunker.H1 - 3, ys[4]));
            releaseAll(level);
            setDirty();
        }

        boolean all = true;
        for (int cx = (ox + WardenBunker.X0) >> 4; cx <= (ox + WardenBunker.X1) >> 4; cx++)
            for (int cz = (oz + WardenBunker.Z0) >> 4; cz <= (oz + WardenBunker.Z1) >> 4; cz++) {
                if (held.add(ChunkPos.asLong(cx, cz))) AsyncChunks.ticket(level, OWNER, cx, cz, true);
                if (AsyncChunks.now(level, cx, cz) == null) all = false;
            }
        if (!all) return;
        int w = WardenBunker.X1 - WardenBunker.X0 + 1, n = w * (WardenBunker.Z1 - WardenBunker.Z0 + 1);
        List<AABB> people = people(level);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long t0 = WorkBudget.start();
        int cols = 0;
        while (cursor < n && (cols < Balance.WB_LAY_MIN_COLUMNS || WorkBudget.more(t0))) {
            int x = WardenBunker.X0 + cursor % w, z = WardenBunker.Z0 + cursor / w;
            for (int h = WardenBunker.H0; h <= WardenBunker.H1; h++) {
                BlockState s = WardenBunker.at(x, h, z);
                if (s == null) continue;
                p.set(ox + x, oy + h, oz + z);
                if (level.getBlockState(p) == s) continue;
                if (!s.isAir() && occupied(people, p)) continue;
                level.setBlock(p, s, 2 | 16);
                written++;
            }
            cursor++;
            cols++;
        }
        WorkBudget.done(t0);
        if (cursor < n) return;
        releaseAll(level);
        HangarDoorBlockEntity door = WardenFight.door(level, this);
        if (door != null) door.bunkerLock(WardenFight.WARDEN_OWNER);
        Wmd.LOGGER.info("[warden] bunker {} at {} {} {}: {} blocks written", repair ? "repaired" : "built", ox, oy, oz, written);
        placed = true;
        phase = ARMED;
        setDirty();
    }

    double progress() {
        int n = (WardenBunker.X1 - WardenBunker.X0 + 1) * (WardenBunker.Z1 - WardenBunker.Z0 + 1);
        return phase == LAY ? Math.min(1.0, cursor / (double) n) : phase >= ARMED ? 1.0 : 0.0;
    }

    private List<AABB> people(ServerLevel level) {
        List<AABB> out = new ArrayList<>();
        AABB box = new AABB(ox + WardenBunker.X0, oy + WardenBunker.H0, oz + WardenBunker.Z0,
                ox + WardenBunker.X1 + 1, oy + WardenBunker.H1 + 1, oz + WardenBunker.Z1 + 1).inflate(1.0);
        for (ServerPlayer pl : level.players()) if (pl.getBoundingBox().intersects(box)) out.add(pl.getBoundingBox().inflate(0.3));
        return out;
    }

    private static boolean occupied(List<AABB> people, BlockPos p) {
        for (AABB a : people) if (a.intersects(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1)) return true;
        return false;
    }

    private boolean loaded3x3(ServerLevel level, int x, int z) {
        boolean all = true;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int cx = (x >> 4) + dx, cz = (z >> 4) + dz;
                if (held.add(ChunkPos.asLong(cx, cz))) AsyncChunks.ticket(level, OWNER, cx, cz, true);
                if (AsyncChunks.now(level, cx, cz) == null) all = false;
            }
        return all;
    }

    private void releaseAll(ServerLevel level) {
        for (long k : held) AsyncChunks.ticket(level, OWNER, ChunkPos.getX(k), ChunkPos.getZ(k), false);
        held.clear();
    }

    void beaten(long nowMs) {
        lastKillMs = nowMs;
        phase = DOWN;
        setDirty();
    }

    long msToRespawn() {
        return phase == DOWN ? Math.max(0L, lastKillMs + Balance.WB_RESPAWN_MS - System.currentTimeMillis()) : 0L;
    }

    BlockPos baySpot() { return new BlockPos(ox, oy + WardenBunker.F + 1, oz + WardenBunker.BAY_SPAWN_Z); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("phase", phase);
        tag.putBoolean("placed", placed);
        tag.putBoolean("gaveUp", gaveUp);
        tag.putBoolean("repair", repair);
        tag.putInt("ox", ox);
        tag.putInt("oy", oy);
        tag.putInt("oz", oz);
        tag.putLong("lastKillMs", lastKillMs);
        tag.putInt("tries", tries);
        return tag;
    }

    static WardenState load(CompoundTag tag, HolderLookup.Provider registries) {
        WardenState d = new WardenState();
        d.phase = Math.max(NONE, Math.min(DOWN, tag.getInt("phase")));
        d.placed = tag.getBoolean("placed");
        d.gaveUp = tag.getBoolean("gaveUp");
        d.repair = tag.getBoolean("repair");
        d.ox = tag.getInt("ox");
        d.oy = tag.contains("oy") ? tag.getInt("oy") : UNMEASURED;
        d.oz = tag.getInt("oz");
        d.lastKillMs = tag.getLong("lastKillMs");
        d.tries = tag.getInt("tries");

        return d;
    }
}
