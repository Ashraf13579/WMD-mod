package com.ashraf.wmd;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SableClient {

    private SableClient() {}

    static Pose3dc pose(Level level, BlockPos pos, float pt) {
        SableCompanion sc = SableCompanion.INSTANCE;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!sc.isInPlotGrid(level, cx, cz)) return null;
        SubLevelAccess a = sc.getContaining(level, cx, cz);
        if (!(a instanceof ClientSubLevelAccess c)) return null;
        return Float.isNaN(pt) ? c.renderPose() : c.renderPose(pt);
    }

    static boolean sameShip(Level level, BlockPos a, BlockPos b) {
        SableCompanion sc = SableCompanion.INSTANCE;
        if (!sc.isInPlotGrid(level, a.getX() >> 4, a.getZ() >> 4) || !sc.isInPlotGrid(level, b.getX() >> 4, b.getZ() >> 4)) return false;
        SubLevelAccess x = sc.getContaining(level, a.getX() >> 4, a.getZ() >> 4), y = sc.getContaining(level, b.getX() >> 4, b.getZ() >> 4);
        return x != null && y != null && x.getUniqueId() != null && x.getUniqueId().equals(y.getUniqueId());
    }

    static Vec3 dirToShip(Level level, BlockPos pos, Vec3 worldDir, float pt) {
        Pose3dc p = pose(level, pos, pt);
        return p == null ? null : p.transformNormalInverse(worldDir);
    }

    static net.minecraft.world.phys.BlockHitResult clipSkipping(Level level, BlockPos own, net.minecraft.world.level.ClipContext ctx) {
        SableCompanion sc = SableCompanion.INSTANCE;
        int cx = own.getX() >> 4, cz = own.getZ() >> 4;
        SubLevelAccess a = sc.isInPlotGrid(level, cx, cz) ? sc.getContaining(level, cx, cz) : null;
        UUID id = a == null ? null : a.getUniqueId();
        if (id != null) ((dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension) (Object) ctx).sable$setSubLevelIgnoring(s -> s != null && id.equals(s.getUniqueId()));
        return level.clip(ctx);
    }

    static Vec3 toWorld(Level level, BlockPos pos, Vec3 plotPoint, float pt) {
        Pose3dc p = pose(level, pos, pt);
        return p == null ? null : p.transformPosition(plotPoint);
    }

    static List<UUID> subLevels(Level level) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (!(c instanceof ClientSubLevelContainer cc)) return List.of();
        List<UUID> out = new ArrayList<>();
        for (ClientSubLevel s : cc.getAllSubLevels()) if (!s.isRemoved()) out.add(s.getUniqueId());
        return out;
    }

    static List<UUID> craftsNear(Level level, double x, double y, double z, double r) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (!(c instanceof ClientSubLevelContainer cc)) return List.of();
        List<UUID> out = new ArrayList<>();
        for (ClientSubLevel s : cc.getAllSubLevels()) {
            if (s.isRemoved() || s.boundingBox() == null) continue;
            if (s.boundingBox().intersects(x - r, y - r, z - r, x + r, y + r, z + r)) out.add(s.getUniqueId());
        }
        return out;
    }

    static org.joml.Matrix4d craftMatrix(Level level, UUID id, float pt) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (!(c instanceof ClientSubLevelContainer)) return null;
        SubLevel s = c.getSubLevel(id);
        if (!(s instanceof ClientSubLevel cs) || cs.isRemoved()) return null;
        return cs.renderPose(pt).bakeIntoMatrix(new org.joml.Matrix4d());
    }

    static boolean redraw(Level level, UUID id) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (!(c instanceof ClientSubLevelContainer)) return false;
        SubLevel s = c.getSubLevel(id);
        if (!(s instanceof ClientSubLevel cs) || cs.isRemoved() || cs.getRenderData() == null) return false;
        for (PlotChunkHolder h : cs.getPlot().getLoadedChunks()) {
            LevelChunk ch = h.getChunk();
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values()) be.requestModelDataUpdate();
        }
        cs.getRenderData().rebuild();
        return true;
    }
}
