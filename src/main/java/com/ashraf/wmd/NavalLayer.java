package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class NavalLayer {

    private NavalLayer() {}

    public static final int NO_WATER = Integer.MIN_VALUE;

    static final int COVER = 8;

    public static int waterTop(Level level, int x, int z) {
        if (level == null || !level.hasChunk(x >> 4, z >> 4)) return NO_WATER;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        for (int k = 0; k <= COVER; k++) {
            p.setY(top - k);
            if (level.getFluidState(p).is(FluidTags.WATER)) return top - k;
        }
        return NO_WATER;
    }

    public static double depthBelowSurface(Level level, Vec3 p) {
        if (p == null) return 0.0;
        int top = waterTop(level, (int) Math.floor(p.x), (int) Math.floor(p.z));
        return top == NO_WATER ? 0.0 : Math.max(0.0, top + 1.0 - p.y);
    }

    public static boolean underwater(Level level, Vec3 p) {
        if (p == null || level == null) return false;
        BlockPos b = BlockPos.containing(p);
        if (!level.hasChunk(b.getX() >> 4, b.getZ() >> 4) || !level.getFluidState(b).is(FluidTags.WATER)) return false;
        return depthBelowSurface(level, p) >= Balance.UNDERWATER_MARGIN;
    }

    public static boolean submerged(Level level, SableBridge.ShipInfo ship) {
        if (ship == null || level == null) return false;
        int top = waterTop(level, (int) Math.floor(ship.worldCenter().x), (int) Math.floor(ship.worldCenter().z));
        return top != NO_WATER && ship.worldBounds().maxY < top + 1.0 - Balance.SUBMERGED_MARGIN;
    }

    public static boolean inWater(Level level, SableBridge.ShipInfo ship) {
        if (ship == null || level == null) return false;
        net.minecraft.world.phys.AABB b = ship.worldBounds();
        double[][] cols = { { ship.worldCenter().x, ship.worldCenter().z }, { b.minX + 0.5, b.minZ + 0.5 }, { b.maxX - 0.5, b.minZ + 0.5 }, { b.minX + 0.5, b.maxZ - 0.5 }, { b.maxX - 0.5, b.maxZ - 0.5 } };
        for (double[] c : cols) {
            int top = waterTop(level, (int) Math.floor(c[0]), (int) Math.floor(c[1]));
            if (top != NO_WATER && b.minY <= top + 1.0 + Balance.WATER_TOUCH) return true;
        }
        return false;
    }

    public static double shipDepth(Level level, SableBridge.ShipInfo ship) {
        if (ship == null) return 0.0;
        int top = waterTop(level, (int) Math.floor(ship.worldCenter().x), (int) Math.floor(ship.worldCenter().z));
        return top == NO_WATER ? 0.0 : Math.max(0.0, top + 1.0 - ship.worldBounds().maxY);
    }

    public static boolean visibleAboveWater(ServerLevel level, SableBridge.ShipInfo ship) {
        return Config.Server.navalRadarSeesSubmerged() || !submerged(level, ship) || Masts.up(level, ship.id());
    }
}
