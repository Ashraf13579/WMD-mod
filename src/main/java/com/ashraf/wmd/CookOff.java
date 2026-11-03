package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

final class CookOff {

    private CookOff() {}

    static void check(ServerLevel level, FlightManager mgr, int tx, int ty, int tz, WarheadType type) {
        if (!Balance.COOK_OFF || type == WarheadType.INTERCEPTOR || type.reach <= 0.0) return;
        double r = Missiles.craterRadius(type.tier) * Math.max(type.crater, 0.3) * type.reach;
        List<BlockEntity> hit = new ArrayList<>();
        for (MissileDepotBlockEntity d : Logistics.depotsIn(level)) if (reaches(level, d.getBlockPos(), tx, ty, tz, r)) hit.add(d);
        for (BayModuleBlockEntity core : Logistics.baysIn(level)) {
            for (int side = 0; side < BayLine.RACKS; side++) {
                BayModuleBlockEntity rack = BayLine.rackOf(core, side);
                if (rack != null && !hit.contains(rack) && reaches(level, rack.getBlockPos(), tx, ty, tz, r)) hit.add(rack);
            }
        }
        for (MissileTeleporterBlockEntity t : Logistics.teleportersIn(level)) if (!t.missile().isEmpty() && reaches(level, t.getBlockPos(), tx, ty, tz, r)) hit.add(t);
        for (BlockEntity be : hit) {
            IItemHandler h = be instanceof MissileDepotBlockEntity d ? d.cells() : be instanceof MissileTeleporterBlockEntity t ? t.chamber() : ((BayModuleBlockEntity) be).rackHandler();
            int weakest = -1;
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < h.getSlots(); i++) {
                int score = score(h.getStackInSlot(i));
                if (score < best) { best = score; weakest = i; }
            }
            if (weakest < 0) continue;
            ItemStack s = h.extractItem(weakest, 1, false);
            WarheadType blast = blastOf(s);
            BlockPos at = be.getBlockPos();
            Wmd.LOGGER.info("Cook-off: the armoury at {} was hit, its weakest missile ({}) goes off as {}", at, s.getHoverName().getString(), blast.id);
            Forcefield.inside(() -> WarheadFx.detonate(level, mgr, at.getX(), at.getY(), at.getZ(), blast.tier, blast, 0.0, 1.0, false));
            if (!be.isRemoved()) level.destroyBlock(at, false);
        }
    }

    private static boolean reaches(ServerLevel level, BlockPos pos, int tx, int ty, int tz, double r) {
        if (pos.distToCenterSqr(tx + 0.5, ty + 0.5, tz + 0.5) > (r + 40.0) * (r + 40.0)) return false;
        for (BlockPos c : Logistics.cells(level, pos)) if (c.distToCenterSqr(tx + 0.5, ty + 0.5, tz + 0.5) <= r * r) return true;
        return false;
    }

    private static int score(ItemStack s) {
        MissileBuild b = Logistics.buildOf(s);
        if (b == null) return Integer.MAX_VALUE;
        WarheadType t = s.getItem() instanceof StageItem st && st.stageKind() != StageItem.UPPER ? WarheadType.HE : WarheadType.of(b.warheadType());
        return t.tier * 10 + b.calibre();
    }

    private static WarheadType blastOf(ItemStack s) {
        MissileBuild b = Logistics.buildOf(s);
        if (b == null || (s.getItem() instanceof StageItem st && st.stageKind() != StageItem.UPPER)) return WarheadType.HE;
        WarheadType t = WarheadType.of(b.warheadType());
        return switch (t) {
            case NUKE, EMP, CLUSTER, DECOY, CHAFF, INTERCEPTOR -> WarheadType.HE;
            default -> t;
        };
    }
}
