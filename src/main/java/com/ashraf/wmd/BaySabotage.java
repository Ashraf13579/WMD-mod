package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;

final class BaySabotage {

    private BaySabotage() {}

    static void check(ServerLevel level, FlightManager mgr, int tx, int ty, int tz, WarheadType type) {
        if (!Balance.BAY_SABOTAGE || type == WarheadType.INTERCEPTOR || type.reach <= 0.0) return;
        double r = Missiles.craterRadius(type.tier) * Math.max(type.crater, 0.3) * type.reach;
        int cr = (int) Math.ceil(r / 16.0) + 1;
        List<BayModuleBlockEntity> hit = new ArrayList<>();
        for (int dx = -cr; dx <= cr; dx++) for (int dz = -cr; dz <= cr; dz++) {
            LevelChunk ch = level.getChunkSource().getChunkNow((tx >> 4) + dx, (tz >> 4) + dz);
            if (ch == null) continue;
            for (BlockEntity be : new ArrayList<>(ch.getBlockEntities().values())) {
                if (!(be instanceof BayModuleBlockEntity core) || core.kind != BayKind.CORE || core.stage() != BayModuleBlockEntity.Stage.WARHEAD) continue;
                BlockPos at = warheadModule(core);
                double ex = at.getX() + 0.5 - (tx + 0.5), ey = at.getY() - ty, ez = at.getZ() + 0.5 - (tz + 0.5);
                if (Math.sqrt(ex * ex + ey * ey + ez * ez) <= r) hit.add(core);
            }
        }
        for (BayModuleBlockEntity core : hit) blowUp(level, mgr, core);
    }

    private static BlockPos warheadModule(BayModuleBlockEntity core) {
        for (BayModuleBlockEntity m : BayLine.chain(core)) if (m.kind == BayKind.WARHEAD) return m.getBlockPos();
        return core.getBlockPos();
    }

    private static void blowUp(ServerLevel level, FlightManager mgr, BayModuleBlockEntity core) {
        BlockPos at = warheadModule(core);
        WarheadType held = WarheadType.of(core.build().warheadType());
        WarheadType blast = switch (held) {
            case NUKE, EMP, CLUSTER, DECOY, CHAFF, INTERCEPTOR -> WarheadType.HE;
            default -> held;
        };
        Wmd.LOGGER.info("Bay sabotaged: the warhead stage of the bay at {} was hit, it blows up with a {} warhead", core.getBlockPos(), held.id);
        List<BayModuleBlockEntity> modules = new ArrayList<>(BayLine.chain(core));
        for (int side = 0; side < BayLine.RACKS; side++) {
            BayModuleBlockEntity rack = BayLine.rackOf(core, side);
            if (rack != null && !modules.contains(rack)) modules.add(rack);
        }
        Forcefield.inside(() -> WarheadFx.detonate(level, mgr, at.getX(), at.getY(), at.getZ(), 1, blast, 0.0, 1.0, false));
        for (BayModuleBlockEntity m : modules) level.destroyBlock(m.getBlockPos(), false);
    }
}
