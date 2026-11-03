package com.ashraf.wmd;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BeamTrace {

    private BeamTrace() {}

    public static BlockHitResult hit(ServerLevel level, Vec3 from, Vec3 dir, double range) {
        double r = Math.min(range, Balance.BEAM_RANGE);
        Vec3 d = dir.normalize();
        if (d.lengthSqr() < 1e-6) return null;
        return walk(level, from, from.add(d.scale(r)));
    }

    private static BlockHitResult walk(ServerLevel level, Vec3 from, Vec3 to) {
        return net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, level, (lv, pos) -> {
            if (!level.isLoaded(pos)) return null;
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
            if (st.isAir()) return null;
            return level.clipWithInteractionOverride(from, to, pos, st.getCollisionShape(level, pos, CollisionContext.empty()), st);
        }, lv -> null);
    }

    public static Vec3 trace(ServerLevel level, Vec3 from, Vec3 dir, double range) {
        double r = Math.min(range, Balance.BEAM_RANGE);
        Vec3 d = dir.normalize();
        if (d.lengthSqr() < 1e-6) return null;
        BlockHitResult hit = walk(level, from, from.add(d.scale(r)));
        return hit == null ? null : hit.getLocation();
    }
}
