package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

public class OilFields extends SavedData {

    private static final String NAME = "wmd_oil_fields";
    private final Map<Long, Integer> left = new HashMap<>();

    public static OilFields get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OilFields::new, OilFields::load, null), NAME);
    }

    public int remaining(BlockPos p) { return left.getOrDefault(p.asLong(), Balance.DEPOSIT_MB); }

    public int take(ServerLevel level, BlockPos p, int max) {
        int have = remaining(p);
        int got = Math.min(max, have);
        have -= got;
        if (have <= 0) {
            left.remove(p.asLong());
            level.setBlock(p, p.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
        } else {
            left.put(p.asLong(), have);
        }
        setDirty();
        return got;
    }

    public static List<BlockPos> clusterBelow(net.minecraft.world.level.Level level, BlockPos well) { return clusterUnder(level, List.of(well)); }

    public static List<BlockPos> clusterUnder(net.minecraft.world.level.Level level, List<BlockPos> tops) {
        BlockPos start = null;
        for (BlockPos top : tops) {
            BlockPos.MutableBlockPos c = top.below().mutable();
            int stop = start == null ? level.getMinBuildHeight() : start.getY() + 1;
            while (c.getY() >= stop && !level.getBlockState(c).is(Wmd.CRUDE_OIL_DEPOSIT.get())) c.move(Direction.DOWN);
            if (c.getY() >= stop) start = c.immutable();
        }
        if (start == null) return List.of();
        BlockPos.MutableBlockPos c = start.mutable();
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(c.immutable());
        seen.add(c.immutable());
        while (!todo.isEmpty() && out.size() < 64) {
            BlockPos p = todo.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && level.getBlockState(n).is(Wmd.CRUDE_OIL_DEPOSIT.get())) todo.add(n);
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Integer> e : left.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey());
            t.putInt("mb", e.getValue());
            list.add(t);
        }
        tag.put("left", list);
        tag.putInt("full", Balance.DEPOSIT_MB);
        return tag;
    }

    public static OilFields load(CompoundTag tag, HolderLookup.Provider registries) {
        OilFields f = new OilFields();

        int was = tag.contains("full") ? tag.getInt("full") : 200_000, more = Math.max(0, Balance.DEPOSIT_MB - was);
        for (Tag t : tag.getList("left", Tag.TAG_COMPOUND)) f.left.put(((CompoundTag) t).getLong("p"), ((CompoundTag) t).getInt("mb") + more);
        if (more > 0) f.setDirty();
        return f;
    }
}
