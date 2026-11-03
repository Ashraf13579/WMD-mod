package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public interface Hardened {

    int rating();

    static int rating(BlockState state) { return state.getBlock() instanceof Hardened h ? h.rating() : 0; }

    static int rating(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof MachinePartBlock) {
            if (level.getBlockEntity(pos) instanceof MachinePartBlockEntity part && part.getController() != null && level.getBlockEntity(part.getController()) instanceof HardenedSite s) return s.blastRating();
            return 0;
        }
        if (state.getBlock() instanceof MachineBlock && level.getBlockEntity(pos) instanceof HardenedSite s) return s.blastRating();
        return rating(state);
    }

    static boolean survives(int rating, double normalisedDistance) { return normalisedDistance >= Math.pow(Balance.HARDENED_FALLOFF, rating); }

    static int layers(Level level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return 0;
        int steps = (int) Math.min(400, Math.ceil(len / 0.5));
        Vec3 step = d.scale(1.0 / steps);
        BlockPos last = null;
        int sum = 0;
        Vec3 p = from;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= steps; i++, p = p.add(step)) {
            pos.set(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
            if (last != null && last.equals(pos)) continue;
            last = pos.immutable();
            if (!level.isLoaded(pos)) continue;
            sum += rating(level, pos, level.getBlockState(pos));
        }
        return sum;
    }

    static double shield(int layers) { return layers <= 0 ? 1.0 : Math.pow(Balance.SHIELD_PER_POINT, layers); }
}
