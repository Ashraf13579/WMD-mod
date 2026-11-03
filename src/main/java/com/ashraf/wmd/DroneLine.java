package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class DroneLine {

    private DroneLine() {}

    public static DroneModuleBlockEntity at(Level level, BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof DroneModuleBlockEntity m && m.getBlockState().getValue(MachineBlock.FACING) == facing ? m : null;
    }

    public static DroneModuleBlockEntity coreOf(DroneModuleBlockEntity be) {
        if (be.getLevel() == null) return null;
        if (be.kind() == DroneKind.CORE) return be;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        BlockPos p = Machines.toWorld(be.getBlockPos(), f, Machines.mount(be.getBlockState()), be.kind().offset(), 0, 0);
        DroneModuleBlockEntity c = at(be.getLevel(), p, f);
        return c != null && c.kind() == DroneKind.CORE && Access.joins(c, be) ? c : null;
    }

    public static DroneModuleBlockEntity module(DroneModuleBlockEntity core, DroneKind kind) {
        if (kind == DroneKind.CORE) return core;
        Direction f = core.getBlockState().getValue(MachineBlock.FACING);
        DroneModuleBlockEntity m = at(core.getLevel(), Machines.toWorld(core.getBlockPos(), f, Machines.mount(core.getBlockState()), -kind.offset(), 0, 0), f);
        return m != null && m.kind() == kind && Access.joins(core, m) ? m : null;
    }

    public static boolean complete(DroneModuleBlockEntity core, DroneType t) {
        for (DroneKind k : DroneKind.values()) {
            if (k == DroneKind.HANGAR && !t.big) continue;
            if (module(core, k) == null) return false;
        }
        return true;
    }

    public static int mask(DroneModuleBlockEntity core) {
        int m = 0;
        for (DroneKind k : DroneKind.values()) if (module(core, k) != null) m |= 1 << k.ordinal();
        return m;
    }

    public static boolean near(DroneModuleBlockEntity core, net.minecraft.world.entity.player.Player pl) {
        for (DroneKind k : DroneKind.values()) {
            DroneModuleBlockEntity m = module(core, k);
            if (m == null) continue;
            BlockPos c = m.getBlockPos();
            double r = k == DroneKind.HANGAR ? 15.0 : 11.0;
            if (pl.distanceToSqr(SableBridge.worldPos(core.getLevel(), c)) <= r * r) return true;
        }
        return false;
    }
}
