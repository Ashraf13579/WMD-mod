package com.ashraf.wmd;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class SableEngines {

    private SableEngines() {}

    private static volatile boolean groupBroken;

    static ScrewPropellerBlockEntity screw(BlockPos pos, BlockState state) { return new Screw(pos, state); }

    static PumpJetBlockEntity pumpJet(BlockPos pos, BlockState state) { return new PumpJet(pos, state); }

    static DuctedFanBlockEntity ductedFan(BlockPos pos, BlockState state) { return new DuctedFan(pos, state); }
    static TurbofanBlockEntity turbofan(BlockPos pos, BlockState state) { return new Turbofan(pos, state); }
    static JatoRackBlockEntity jato(BlockPos pos, BlockState state) { return new Jato(pos, state); }
    static LiftFanBlockEntity liftFan(BlockPos pos, BlockState state) { return new LiftFan(pos, state); }
    static HeliRotorBlockEntity heliRotor(BlockPos pos, BlockState state) { return new HeliRotor(pos, state); }
    static HeliTailBlockEntity heliTail(BlockPos pos, BlockState state) { return new HeliTail(pos, state); }
    static CargoHookBlockEntity cargoHook(BlockPos pos, BlockState state) { return new CargoHook(pos, state); }

    static TurbopropBlockEntity turboprop(BlockPos pos, BlockState state) { return new Turboprop(pos, state); }

    static JetEngineBlockEntity jet(BlockPos pos, BlockState state) { return new Jet(pos, state); }
    static TiltrotorBlockEntity tiltrotor(BlockPos pos, BlockState state) { return new Tiltrotor(pos, state); }
    static WaterjetBlockEntity waterjet(BlockPos pos, BlockState state) { return new Waterjet(pos, state); }
    static AzimuthPodBlockEntity azimuthPod(BlockPos pos, BlockState state) { return new AzimuthPod(pos, state); }
    static BowThrusterBlockEntity bowThruster(BlockPos pos, BlockState state) { return new BowThruster(pos, state); }

    static final class CargoHook extends CargoHookBlockEntity implements BlockEntitySubLevelActor {
        CargoHook(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Turboprop extends TurbopropBlockEntity implements BlockEntitySubLevelActor {
        Turboprop(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Tiltrotor extends TiltrotorBlockEntity implements BlockEntitySubLevelActor {
        Tiltrotor(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Waterjet extends WaterjetBlockEntity implements BlockEntitySubLevelActor {
        Waterjet(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class AzimuthPod extends AzimuthPodBlockEntity implements BlockEntitySubLevelActor {
        AzimuthPod(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class BowThruster extends BowThrusterBlockEntity implements BlockEntitySubLevelActor {
        BowThruster(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class HeliRotor extends HeliRotorBlockEntity implements BlockEntitySubLevelActor {
        HeliRotor(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class HeliTail extends HeliTailBlockEntity implements BlockEntitySubLevelActor {
        HeliTail(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class LiftFan extends LiftFanBlockEntity implements BlockEntitySubLevelActor {
        LiftFan(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class DuctedFan extends DuctedFanBlockEntity implements BlockEntitySubLevelActor {
        DuctedFan(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Jet extends JetEngineBlockEntity implements BlockEntitySubLevelActor {
        Jet(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Turbofan extends TurbofanBlockEntity implements BlockEntitySubLevelActor {
        Turbofan(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Jato extends JatoRackBlockEntity implements BlockEntitySubLevelActor {
        Jato(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class PumpJet extends PumpJetBlockEntity implements BlockEntitySubLevelActor {
        PumpJet(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static final class Screw extends ScrewPropellerBlockEntity implements BlockEntitySubLevelActor {
        Screw(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { push(this, sub, handle, dt); }
    }

    static void push(ShipEngine engine, ServerSubLevel sub, RigidBodyHandle handle, double dt) {
        if (!SableBridge.active() || sub == null || sub.isRemoved() || !(dt > 0.0) || dt > 1.0) return;
        double[] f = engine.engineForce();
        if (f == null || f.length < 6) return;
        for (double v : f) if (!Double.isFinite(v)) return;
        if (!groupBroken) {
            try {
                sub.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(new Vector3d(f[0], f[1], f[2]), new Vector3d(f[3] * dt, f[4] * dt, f[5] * dt));
                return;
            } catch (Throwable t) {
                groupBroken = true;
                Wmd.LOGGER.warn("WMD engines: Sable's propulsion force group failed, pushing through the rigid body handle instead", t);
            }
        }
        try {
            if (handle == null || !handle.isValid()) return;
            handle.applyImpulseAtPoint(new Vec3(f[0], f[1], f[2]), new Vec3(f[3] * dt, f[4] * dt, f[5] * dt));
        } catch (Throwable t) {
            SableBridge.fail(t);
        }
    }
}
