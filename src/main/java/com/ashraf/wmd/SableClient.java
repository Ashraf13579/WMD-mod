package com.ashraf.wmd;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SableClient {

    private SableClient() {}

    static Pose3dc pose(Level level, BlockPos pos, float pt) {
        SableCompanion sc = SableCompanion.INSTANCE;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!sc.isInPlotGrid(level, cx, cz)) return null;
        SubLevelAccess a = sc.getContaining(level, cx, cz);
        if (!(a instanceof ClientSubLevelAccess c)) return null;
        return Float.isNaN(pt) ? c.renderPose() : c.renderPose(pt);
    }

    static Vec3 dirToShip(Level level, BlockPos pos, Vec3 worldDir, float pt) {
        Pose3dc p = pose(level, pos, pt);
        return p == null ? null : p.transformNormalInverse(worldDir);
    }

    static Vec3 toWorld(Level level, BlockPos pos, Vec3 plotPoint, float pt) {
        Pose3dc p = pose(level, pos, pt);
        return p == null ? null : p.transformPosition(plotPoint);
    }
}
