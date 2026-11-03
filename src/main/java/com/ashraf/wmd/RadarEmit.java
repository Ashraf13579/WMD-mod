package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RadarEmit {

    private RadarEmit() {}

    private static final Set<BlockEntity> RADARS = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    public static synchronized void note(BlockEntity be) { if (be.getLevel() != null && !be.getLevel().isClientSide) RADARS.add(be); }

    static boolean emitting(BlockEntity be) {
        if (be.isRemoved() || !(be.getLevel() instanceof ServerLevel sl) || EmpField.jammed(sl, be.getBlockPos())) return false;
        if (be instanceof RadarBlockEntity r) return r.isOnline();
        if (be instanceof DecoyBlockEntity.Radar d) return !d.popped();
        if (be instanceof ProcessUnitBlockEntity p) return p.isRunning();
        if (be instanceof CiwsBlockEntity c) return c.energy() >= Balance.CIWS_MOVE_FE;
        return false;
    }

    static UUID ownerOf(BlockEntity be) {
        if (be instanceof Owned o) return o.getOwner();
        if (be instanceof RadarBlockEntity r) return r.getOwner();
        return null;
    }

    public static synchronized BlockPos nearest(ServerLevel level, double x, double z, double r, UUID notOwner) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockEntity be : RADARS) {
            if (be.getLevel() != level || !emitting(be)) continue;
            UUID o = ownerOf(be);
            if (o != null && o.equals(notOwner)) continue;
            BlockPos p = be.getBlockPos();
            net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, p);
            double dx = w.x - x, dz = w.z - z, d = dx * dx + dz * dz;
            if (d > r * r) continue;
            double score = Math.sqrt(d) - (be instanceof DecoyBlockEntity.Radar ? Balance.DECOY_ARM_PULL : 0.0);
            if (score <= bd) { bd = score; best = p; }
        }
        return best;
    }

    public static synchronized List<BlockEntity> near(ServerLevel level, double x, double z, double r) {
        List<BlockEntity> l = new ArrayList<>();
        for (BlockEntity be : RADARS) if (be.getLevel() == level && !be.isRemoved() && Math.hypot(be.getBlockPos().getX() - x, be.getBlockPos().getZ() - z) <= r) l.add(be);
        return l;
    }
}
