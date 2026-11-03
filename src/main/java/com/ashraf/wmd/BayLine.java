package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class BayLine {

    private BayLine() {}

    static BayModuleBlockEntity at(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) return null;
        if (level.getBlockEntity(pos) instanceof BayModuleBlockEntity m && m.getBlockState().getValue(MachineBlock.FACING) == facing) return m;
        return null;
    }

    public static BayModuleBlockEntity next(BayModuleBlockEntity m) {
        Direction f = m.getBlockState().getValue(MachineBlock.FACING);
        BayModuleBlockEntity n = at(m.getLevel(), Machines.toWorld(m.getBlockPos(), f, Machines.mount(m.getBlockState()), 0, 0, m.kind.length), f);
        return n != null && Access.joins(m, n) ? n : null;
    }

    public static BayModuleBlockEntity previous(BayModuleBlockEntity m) {
        Direction f = m.getBlockState().getValue(MachineBlock.FACING);
        for (BayKind k : BayKind.values()) {
            BayModuleBlockEntity p = at(m.getLevel(), Machines.toWorld(m.getBlockPos(), f, Machines.mount(m.getBlockState()), 0, 0, -k.length), f);
            if (p != null && p.kind == k) return Access.joins(p, m) ? p : null;
        }
        return null;
    }

    public static final int RACK_X = 5;

    public static BayModuleBlockEntity firstExtension(BayModuleBlockEntity core) {
        for (BayModuleBlockEntity m : chain(core)) if (m.kind == BayKind.EXTEND) return m;
        return null;
    }

    public static final int RACKS = 4;

    public static BayModuleBlockEntity extension(BayModuleBlockEntity core, int n) {
        int k = 0;
        for (BayModuleBlockEntity m : chain(core)) if (m.kind == BayKind.EXTEND && ++k == n) return m;
        return null;
    }

    public static int extensionNumber(BayModuleBlockEntity ext) {
        BayModuleBlockEntity core = coreOf(ext);
        if (core == null) return 0;
        int k = 0;
        for (BayModuleBlockEntity m : chain(core)) if (m.kind == BayKind.EXTEND) { k++; if (m == ext) return k; }
        return 0;
    }

    public static int rackIndex(BayModuleBlockEntity rack) {
        if (!rack.kind.isRack() || rack.getLevel() == null) return -1;
        Direction f = rack.getBlockState().getValue(MachineBlock.FACING);
        BayModuleBlockEntity e = at(rack.getLevel(), Machines.toWorld(rack.getBlockPos(), f, Machines.mount(rack.getBlockState()), rack.kind == BayKind.RACK ? -RACK_X : RACK_X, 0, 0), f);
        if (e == null || e.kind != BayKind.EXTEND) return -1;
        int n = extensionNumber(e);
        if (n != 1 && n != 4) return -1;
        return (n == 1 ? 0 : 2) + (rack.kind == BayKind.RACK_R ? 1 : 0);
    }

    public static BayModuleBlockEntity rackOf(BayModuleBlockEntity core) { return rackOf(core, 0); }

    public static BayModuleBlockEntity rackOf(BayModuleBlockEntity core, int side) {
        int pair = side / 2;
        if (side < 0 || side >= RACKS || extensions(core) < 3 * (pair + 1)) return null;
        BayModuleBlockEntity e = extension(core, 3 * pair + 1);
        if (e == null) return null;
        side = side % 2;
        Direction f = e.getBlockState().getValue(MachineBlock.FACING);
        BayModuleBlockEntity r = at(e.getLevel(), Machines.toWorld(e.getBlockPos(), f, Machines.mount(e.getBlockState()), side == 0 ? RACK_X : -RACK_X, 0, 0), f);
        return r != null && r.kind == (side == 0 ? BayKind.RACK : BayKind.RACK_R) && Access.joins(e, r) ? r : null;
    }

    public static BayModuleBlockEntity coreOf(BayModuleBlockEntity m) {
        if (m.kind.isRack()) {
            Direction f = m.getBlockState().getValue(MachineBlock.FACING);
            BayModuleBlockEntity e = at(m.getLevel(), Machines.toWorld(m.getBlockPos(), f, Machines.mount(m.getBlockState()), m.kind == BayKind.RACK ? -RACK_X : RACK_X, 0, 0), f);
            if (e == null || e.kind != BayKind.EXTEND || !Access.joins(e, m)) return null;
            m = e;
        }
        BayModuleBlockEntity cur = m;
        for (int i = 0; i < 16 && cur != null; i++) {
            if (cur.kind == BayKind.CORE) return cur;
            cur = previous(cur);
        }
        return null;
    }

    public static List<BayModuleBlockEntity> chain(BayModuleBlockEntity core) {
        List<BayModuleBlockEntity> list = new ArrayList<>();
        BayModuleBlockEntity cur = core;
        for (int i = 0; i < 16 && cur != null; i++) { list.add(cur); cur = next(cur); }
        return list;
    }

    public static int lengthBlocks(BayModuleBlockEntity core) {
        int n = 0;
        for (BayModuleBlockEntity m : chain(core)) n += m.kind.length;
        return n;
    }

    public static int extensions(BayModuleBlockEntity core) {
        int n = 0;
        for (BayModuleBlockEntity m : chain(core)) if (m.kind == BayKind.EXTEND) n++;
        return n;
    }

    public static boolean complete(BayModuleBlockEntity core) {
        List<BayModuleBlockEntity> c = chain(core);
        int basic = BayKind.WARHEAD.ordinal() + 1;
        if (c.size() < basic) return false;
        for (int i = 0; i < basic; i++) if (c.get(i).kind != BayKind.values()[i]) return false;
        return true;
    }

    public static int stations(BayModuleBlockEntity core) {
        List<BayModuleBlockEntity> c = chain(core);
        int n = 0;
        BayKind[] order = { BayKind.CLEAN, BayKind.PAINT, BayKind.CRYO };
        for (int i = 0; i < order.length; i++) {
            int idx = BayKind.WARHEAD.ordinal() + 1 + i;
            if (idx < c.size() && c.get(idx).kind == order[i]) n++; else break;
        }
        return n;
    }
}
