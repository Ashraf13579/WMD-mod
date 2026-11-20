package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class ModelFootprint {

    private ModelFootprint() {}

    static List<BlockPos> extend(List<BlockPos> base, String mesh, int x0, int x1, int yMin, int z0, int z1) {
        List<BlockPos> out = new ArrayList<>(base);
        Set<BlockPos> have = new HashSet<>(base);
        int added = 0;
        for (BlockPos p : MachineShapes.cells(mesh)) {
            if (p.getY() < yMin || p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1 || !have.add(p)) continue;
            out.add(p);
            added++;
        }
        if (added == 0) Wmd.LOGGER.warn("[footprint] {}: no model cells added (data/wmd/machine_shapes/{}.json missing?)", mesh, mesh);
        return List.copyOf(out);
    }

    static int heal(Level level, BlockPos ctrl, BlockState st, List<BlockPos> fp, int yMin, String what) {
        if (level == null || level.isClientSide) return 0;
        Direction facing = st.getValue(MachineBlock.FACING), mount = Machines.mount(st);
        int n = 0;
        for (BlockPos l : fp) {
            if (l.getY() < yMin) continue;
            BlockPos p = Machines.toWorld(ctrl, facing, mount, l.getX(), l.getY(), l.getZ());
            if (p.equals(ctrl) || !level.hasChunkAt(p) || !level.getBlockState(p).isAir() || PlaceGuard.blocker(level, p) != null) continue;
            level.setBlock(p, Waterlog.part(level, p), 3);
            if (level.getBlockEntity(p) instanceof MachinePartBlockEntity pe) pe.setController(ctrl);
            n++;
        }
        if (n > 0) Wmd.LOGGER.info("[footprint] {} at {}: added {} missing part block(s) (walk-through parts now have a hitbox)", what, ctrl.toShortString(), n);
        return n;
    }
}
