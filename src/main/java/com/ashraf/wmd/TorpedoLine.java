package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class TorpedoLine {

    private TorpedoLine() {}

    public static TorpedoLineBlockEntity at(Level level, BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TorpedoLineBlockEntity m && m.getBlockState().getValue(MachineBlock.FACING) == facing ? m : null;
    }

    public static TorpedoLineBlockEntity coreOf(TorpedoLineBlockEntity be) {
        if (be.getLevel() == null) return null;
        if (be.kind() == TorpedoLineKind.SHOP) return be;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        TorpedoLineBlockEntity c = at(be.getLevel(), Machines.toWorld(be.getBlockPos(), f, Machines.mount(be.getBlockState()), 0, 0, -be.kind().offset()), f);
        return c != null && c.kind() == TorpedoLineKind.SHOP && Access.joins(c, be) ? c : null;
    }

    public static TorpedoLineBlockEntity module(TorpedoLineBlockEntity core, TorpedoLineKind kind) {
        if (kind == TorpedoLineKind.SHOP) return core;
        Direction f = core.getBlockState().getValue(MachineBlock.FACING);
        TorpedoLineBlockEntity m = at(core.getLevel(), Machines.toWorld(core.getBlockPos(), f, Machines.mount(core.getBlockState()), 0, 0, kind.offset()), f);
        return m != null && m.kind() == kind && Access.joins(core, m) ? m : null;
    }

    public static boolean complete(TorpedoLineBlockEntity core) {
        for (TorpedoLineKind k : TorpedoLineKind.values()) if (module(core, k) == null) return false;
        return true;
    }

    public static int mask(TorpedoLineBlockEntity core) {
        int m = 0;
        for (TorpedoLineKind k : TorpedoLineKind.values()) if (module(core, k) != null) m |= 1 << k.ordinal();
        return m;
    }

    public static boolean near(TorpedoLineBlockEntity core, net.minecraft.world.entity.player.Player pl) {
        for (TorpedoLineKind k : TorpedoLineKind.values()) {
            TorpedoLineBlockEntity m = module(core, k);
            if (m == null) continue;
            if (pl.distanceToSqr(SableBridge.worldPos(core.getLevel(), m.getBlockPos())) <= 12.0 * 12.0) return true;
        }
        return false;
    }
}
