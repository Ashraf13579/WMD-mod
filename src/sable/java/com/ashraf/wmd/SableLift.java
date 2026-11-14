package com.ashraf.wmd;

import java.util.Map;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

final class SableLift {

    private SableLift() {}

    private static final BlockState SLAB = Blocks.IRON_BLOCK.defaultBlockState();

    static Object add(ServerLevel sl, DeckLiftBlockEntity lift, int sx, int sz) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
        if (c == null || lift.floor() == null) return null;
        Platform p = new Platform(sl, lift, sx, sz);
        if (p.mass == null || p.mass.isInvalid() || p.mass.getCenterOfMass() == null) return null;
        c.physicsSystem().getPipeline().add(p);
        return p;
    }

    static void remove(ServerLevel sl, Object handle) {
        if (!(handle instanceof Platform p)) return;
        p.live = false;
        ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
        if (c != null) c.physicsSystem().getPipeline().remove(p);
    }

    static final class Platform implements KinematicContraption {
        private final ServerLevel level;
        private final DeckLiftBlockEntity lift;
        private final int sx, sz;
        private final BlockGetter blocks;
        private final MassTracker mass;
        private final FloatingClusterContainer floats = new FloatingClusterContainer();
        private final Vector3d pos = new Vector3d();
        volatile boolean live = true;
        private double lastTop = Double.NaN;

        Platform(ServerLevel level, DeckLiftBlockEntity lift, int sx, int sz) {
            this.level = level;
            this.lift = lift;
            this.sx = sx;
            this.sz = sz;
            this.blocks = new BlockGetter() {
                @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
                @Override public BlockState getBlockState(BlockPos p) {
                    return p.getY() == 0 && p.getX() >= 0 && p.getX() < sx && p.getZ() >= 0 && p.getZ() < sz ? SLAB : Blocks.AIR.defaultBlockState();
                }
                @Override public FluidState getFluidState(BlockPos p) { return Fluids.EMPTY.defaultFluidState(); }
                @Override public int getHeight() { return 16; }
                @Override public int getMinBuildHeight() { return 0; }
            };
            this.mass = MassTracker.build(blocks, new BoundingBox3i(0, 0, 0, sx - 1, 0, sz - 1));
        }

        @Override public void sable$getLocalBounds(BoundingBox3i bounds) { bounds.set(0, 0, 0, sx - 1, 0, sz - 1); }
        @Override public BlockGetter sable$blockGetter() { return blocks; }
        @Override public MassTracker sable$getMassTracker() { return mass; }

        @Override
        public Vector3dc sable$getPosition(double partialTick) {
            DeckLiftBlockEntity.Floor f = lift.floor();
            BlockPos h = lift.getBlockPos();
            double top = f == null ? (Double.isNaN(lastTop) ? h.getY() + 1.0 : lastTop) : f.top(level.getGameTime() - 1.0 + partialTick);
            if (f != null && partialTick >= 1.0) lastTop = top;
            Vector3dc com = mass.getCenterOfMass();
            return pos.set(h.getX() + com.x(), top - 1.0 + com.y(), h.getZ() + com.z());
        }

        @Override public Quaterniond sable$getOrientation(double partialTick) { return new Quaterniond(); }
        @Override public Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviders() { return Map.of(); }
        @Override public FloatingClusterContainer sable$getFloatingClusterContainer() { return floats; }
        @Override public boolean sable$shouldCollide() { return true; }
        @Override public boolean sable$isValid() { return live && !lift.isRemoved(); }
    }
}
