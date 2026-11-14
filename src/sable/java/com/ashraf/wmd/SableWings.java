package com.ashraf.wmd;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class SableWings {

    private SableWings() {}

    static WingSectionBlock section(BlockBehaviour.Properties p) { return new Section(p); }

    static ControlSurfaceBlock control(BlockBehaviour.Properties p) { return new Control(p); }

    static final class Section extends WingSectionBlock implements BlockSubLevelLiftProvider {
        Section(BlockBehaviour.Properties p) { super(p); }
        @Override public Direction sable$getNormal(BlockState state) { return WingParts.normal(state); }
        @Override public float sable$getLiftScalar() { return Balance.WING_LIFT_SCALAR; }
        @Override public float sable$getParallelDragScalar() { return Balance.WING_PLATE_SCALAR; }
    }

    static final class Control extends ControlSurfaceBlock implements BlockSubLevelLiftProvider {
        Control(BlockBehaviour.Properties p) { super(p); }
        @Override public float sable$getLiftScalar() { return Balance.WING_LIFT_SCALAR; }
        @Override public float sable$getParallelDragScalar() { return Balance.WING_PLATE_SCALAR; }

        @Override public Direction sable$getNormal(BlockState state) { return WingParts.normal(state); }

        @Override
        public void sable$contributeLiftAndDrag(LiftProviderContext ctx, ServerSubLevel subLevel, Pose3d localPose, double timeStep, Vector3dc linearVelocity,
                                                 Vector3dc angularVelocity, Vector3d linearImpulse, Vector3d angularImpulse, LiftProviderGroup group) {
            float d = ControlSurfaceBlock.deflection(subLevel.getLevel().dimension(), ctx.pos());
            LiftProviderContext c = ctx;
            if (Math.abs(d) > 0.05F) {
                double a = Math.toRadians(d) * Balance.WING_CS_SIGN;
                Vec3 n = ctx.dir(), f = WingParts.forward(ctx.state());
                c = new LiftProviderContext(ctx.pos(), ctx.state(), n.scale(Math.cos(a)).subtract(f.scale(Math.sin(a))));
            }
            BlockSubLevelLiftProvider.super.sable$contributeLiftAndDrag(c, subLevel, localPose, timeStep, linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);
        }
    }
}
