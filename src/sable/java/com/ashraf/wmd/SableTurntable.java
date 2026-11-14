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

final class SableTurntable {

    private SableTurntable() {}

    private static final BlockState SLAB = Blocks.IRON_BLOCK.defaultBlockState();

    static Object add(ServerLevel sl, TurntableBlockEntity tt, int n) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
        if (c == null || tt.plate() == null || n <= 0) return null;
        Disc d = new Disc(sl, tt, n);
        if (d.mass == null || d.mass.isInvalid() || d.mass.getCenterOfMass() == null) return null;
        c.physicsSystem().getPipeline().add(d);
        return d;
    }

    static void remove(ServerLevel sl, Object handle) {
        if (!(handle instanceof Disc d)) return;
        d.live = false;
        ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
        if (c != null) c.physicsSystem().getPipeline().remove(d);
    }

    static final class Disc implements KinematicContraption {
        private final ServerLevel level;
        private final TurntableBlockEntity tt;
        private final int n;
        private final BlockGetter blocks;
        private final MassTracker mass;
        private final FloatingClusterContainer floats = new FloatingClusterContainer();
        private final Vector3d pos = new Vector3d();
        volatile boolean live = true;
        private TurntableBlockEntity.Plate last;

        Disc(ServerLevel level, TurntableBlockEntity tt, int n) {
            this.level = level;
            this.tt = tt;
            this.n = n;
            this.blocks = new BlockGetter() {
                @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
                @Override public BlockState getBlockState(BlockPos p) {
                    return p.getY() == 0 && p.getX() >= 0 && p.getX() < n && p.getZ() >= 0 && p.getZ() < n && TurntableBlockEntity.inside(n, p.getX(), p.getZ())
                            ? SLAB : Blocks.AIR.defaultBlockState();
                }
                @Override public FluidState getFluidState(BlockPos p) { return Fluids.EMPTY.defaultFluidState(); }
                @Override public int getHeight() { return 16; }
                @Override public int getMinBuildHeight() { return 0; }
            };
            this.mass = MassTracker.build(blocks, new BoundingBox3i(0, 0, 0, n - 1, 0, n - 1));
        }

        private TurntableBlockEntity.Plate plate() {
            TurntableBlockEntity.Plate p = tt.plate();
            if (p != null) last = p;
            return last;
        }

        @Override public void sable$getLocalBounds(BoundingBox3i bounds) { bounds.set(0, 0, 0, n - 1, 0, n - 1); }
        @Override public BlockGetter sable$blockGetter() { return blocks; }
        @Override public MassTracker sable$getMassTracker() { return mass; }

        @Override
        public Vector3dc sable$getPosition(double partialTick) {
            TurntableBlockEntity.Plate p = plate();
            Vector3dc com = mass.getCenterOfMass();
            if (p == null) return pos.set(tt.cx(), tt.top() - 1.0 + com.y(), tt.cz());
            return pos.set(p.cx() + (com.x() - n / 2.0), p.top() - 1.0 + com.y(), p.cz() + (com.z() - n / 2.0));
        }

        @Override
        public Quaterniond sable$getOrientation(double partialTick) {
            TurntableBlockEntity.Plate p = plate();
            double a = p == null ? 0.0 : p.angle(DeckLiftBlockEntity.physicsNow(level, partialTick));
            return new Quaterniond().rotateY(-Math.toRadians(a));
        }

        @Override public Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviders() { return Map.of(); }
        @Override public FloatingClusterContainer sable$getFloatingClusterContainer() { return floats; }
        @Override public boolean sable$shouldCollide() { return true; }
        @Override public boolean sable$isValid() { return live && !tt.isRemoved(); }
    }
}
