package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class HullPlug {

    private HullPlug() {}

    static boolean open(BlockEntity be, Direction facing, Direction mount, int[]... cells) {
        Level l = be.getLevel();
        if (l == null) return false;
        for (int[] c : cells) {
            BlockPos p = Machines.toWorld(be.getBlockPos(), facing, mount, c[0], c[1], c[2]);
            if (!l.getBlockState(p).getCollisionShape(l, p).isEmpty()) return false;
        }
        return true;
    }
}
