package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class LabLine {

    private LabLine() {}

    public static BlockEntity at(Level level, BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LabModuleBlockEntity || be instanceof WarheadReactorBlockEntity) {
            if (be.getBlockState().getValue(MachineBlock.FACING) == facing) return be;
        }
        return null;
    }

    public static LabKind kindOf(BlockEntity be) {
        if (be instanceof LabModuleBlockEntity m) return m.kind();
        if (be instanceof WarheadReactorBlockEntity) return LabKind.REACTOR;
        return null;
    }

    public static LabModuleBlockEntity coreOf(BlockEntity be) {
        LabKind k = kindOf(be);
        if (k == null || be.getLevel() == null) return null;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        BlockPos p = Machines.toWorld(be.getBlockPos(), f, Machines.mount(be.getBlockState()), LabKind.STEP * k.ordinal(), 0, 0);
        return at(be.getLevel(), p, f) instanceof LabModuleBlockEntity c && c.kind() == LabKind.CORE
                && (!(be instanceof Owned o) || Access.joins(c, o)) ? c : null;
    }

    public static BlockEntity module(LabModuleBlockEntity core, LabKind kind) {
        Direction f = core.getBlockState().getValue(MachineBlock.FACING);
        BlockEntity be = at(core.getLevel(), Machines.toWorld(core.getBlockPos(), f, Machines.mount(core.getBlockState()), -LabKind.STEP * kind.ordinal(), 0, 0), f);
        return be != null && kindOf(be) == kind && (!(be instanceof Owned o) || Access.joins(core, o)) ? be : null;
    }

    public static boolean complete(LabModuleBlockEntity core) {
        for (LabKind k : LabKind.values()) if (module(core, k) == null) return false;
        return true;
    }

    public static int mask(LabModuleBlockEntity core) {
        int m = 0;
        for (LabKind k : LabKind.values()) if (module(core, k) != null) m |= 1 << k.ordinal();
        return m;
    }

    public static boolean near(LabModuleBlockEntity core, net.minecraft.world.entity.player.Player pl) {
        for (LabKind k : LabKind.values()) {
            BlockEntity m = module(core, k);
            if (m == null) continue;
            BlockPos c = m.getBlockPos();
            double r = 11.0;
            if (pl.distanceToSqr(SableBridge.worldPos(core.getLevel(), c)) <= r * r) return true;
        }
        return false;
    }
}
