package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class BombLine {

    private BombLine() {}

    public static BombLineBlockEntity at(Level level, BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof BombLineBlockEntity m && m.getBlockState().getValue(MachineBlock.FACING) == facing ? m : null;
    }

    public static BombLineBlockEntity coreOf(BombLineBlockEntity be) {
        if (be.getLevel() == null) return null;
        if (be.kind() == BombLineKind.CORE) return be;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        BombLineBlockEntity c = at(be.getLevel(), Machines.toWorld(be.getBlockPos(), f, Machines.mount(be.getBlockState()), be.kind().offset(), 0, 0), f);
        return c != null && c.kind() == BombLineKind.CORE && Access.joins(c, be) ? c : null;
    }

    public static BombLineBlockEntity module(BombLineBlockEntity core, BombLineKind kind) {
        if (kind == BombLineKind.CORE) return core;
        Direction f = core.getBlockState().getValue(MachineBlock.FACING);
        BombLineBlockEntity m = at(core.getLevel(), Machines.toWorld(core.getBlockPos(), f, Machines.mount(core.getBlockState()), -kind.offset(), 0, 0), f);
        return m != null && m.kind() == kind && Access.joins(core, m) ? m : null;
    }

    public static boolean complete(BombLineBlockEntity core, BombType t) {
        for (BombLineKind k : BombLineKind.values()) {
            if (k == BombLineKind.CRADLE && !t.heavy) continue;
            if (module(core, k) == null) return false;
        }
        return true;
    }

    public static int mask(BombLineBlockEntity core) {
        int m = 0;
        for (BombLineKind k : BombLineKind.values()) if (module(core, k) != null) m |= 1 << k.ordinal();
        return m;
    }

    public static boolean near(BombLineBlockEntity core, net.minecraft.world.entity.player.Player pl) {
        for (BombLineKind k : BombLineKind.values()) {
            BombLineBlockEntity m = module(core, k);
            if (m == null) continue;
            BlockPos c = m.getBlockPos();
            double r = k == BombLineKind.CRADLE ? 13.0 : 11.0;
            if (pl.distanceToSqr(SableBridge.worldPos(core.getLevel(), c)) <= r * r) return true;
        }
        return false;
    }
}
