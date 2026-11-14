package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class SableGear {

    private SableGear() {}

    private static volatile boolean broken;

    static LandingGearBlockEntity gear(BlockPos pos, BlockState state) { return new Gear(pos, state); }

    static final class Gear extends LandingGearBlockEntity implements BlockEntitySubLevelActor {
        Gear(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double dt) { step(this, sub, handle, dt); }
    }

    static void step(LandingGearBlockEntity g, ServerSubLevel sub, RigidBodyHandle handle, double dt) {
        if (broken || !AirGate.on() || !SableBridge.active() || sub == null || sub.isRemoved() || !(dt > 0.0) || dt > 1.0) return;
        if (!g.locked() || !(g.getLevel() instanceof ServerLevel sl) || handle == null || !handle.isValid()) return;
        try {
            if (!push(g, sl, sub, handle, dt)) { g.wow = false; g.comp = 0.0F; g.rollSpeed = 0.0F; }
        } catch (Throwable t) {
            broken = true;
            Wmd.LOGGER.error("WMD landing gear: the physics step failed, gear wheels switched off for this session", t);
        }
    }

    private static boolean push(LandingGearBlockEntity g, ServerLevel sl, ServerSubLevel sub, RigidBodyHandle handle, double dt) {
        Pose3dc pose = sub.logicalPose();
        Direction leg = g.legDir(), roll = g.rollDir();
        BlockPos bp = g.getBlockPos();
        Direction sd = g.sideDir();
        double so = g.sideOffset();
        Direction ad = g.aftDir();
        double ao = g.aftOffset();
        Vector3d attach = pose.transformPosition(new Vector3d(bp.getX() + 0.5 + leg.getStepX() * 0.5 + sd.getStepX() * so + ad.getStepX() * ao,
                bp.getY() + 0.5 + leg.getStepY() * 0.5 + sd.getStepY() * so + ad.getStepY() * ao, bp.getZ() + 0.5 + leg.getStepZ() * 0.5 + sd.getStepZ() * so + ad.getStepZ() * ao));
        Vector3d legW = pose.transformNormal(new Vector3d(leg.getStepX(), leg.getStepY(), leg.getStepZ()));
        if (legW.lengthSquared() < 1.0E-9) return false;
        legW.normalize();
        double reach = g.reach(), probe = reach + 0.25;
        Vec3 a = new Vec3(attach.x, attach.y, attach.z), dir = new Vec3(legW.x, legW.y, legW.z);

        double now = DeckLiftBlockEntity.physicsNow(sl, partialTick(sl));
        DeckLiftBlockEntity.Floor[] lift = new DeckLiftBlockEntity.Floor[1];
        TurntableBlockEntity.Plate[] plate = new TurntableBlockEntity.Plate[1];
        double hitT = terrain(sl, a, dir, probe, SableBridge.shipIdAt(sl, bp), now, lift, plate);
        UUID own = g.ship(), deck = null;
        List<SableBridge.ShipInfo> cands = new java.util.ArrayList<>();
        for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, a, a.add(dir.scale(probe)))) if (!s.id().equals(own)) cands.add(s);
        ProjectilePath.PathHit h = cands.isEmpty() ? null : SableBridge.exactHit(sl, a, a.add(dir.scale(probe)), cands);
        if (h != null && h.shipId() != null) {
            double t = h.pos().distanceTo(a);
            if (Double.isNaN(hitT) || t < hitT) { hitT = t; deck = h.shipId(); lift[0] = null; plate[0] = null; }
        }
        if (Double.isNaN(hitT)) return false;
        double c = reach - hitT;
        if (c <= 0.0) return false;
        c = Math.min(c, Balance.GEAR_STROKE * 1.5);

        MassData md = sub.getMassTracker();
        double m = md == null ? 0.0 : md.getMass();
        if (!(m > 0.0) || !Double.isFinite(m)) return false;
        double mi = m * Math.max(0.0, Math.min(1.0, g.share)), weight = mi * Balance.GEAR_G;
        double k = weight / Balance.GEAR_REST_COMP, damp = 2.0 * Balance.GEAR_ZETA * Math.sqrt(k * mi);
        Vector3d p = new Vector3d(attach).fma(hitT, legW);
        Vector3d com = pose.transformPosition(new Vector3d(md.getCenterOfMass()));
        Vector3d r = new Vector3d(p).sub(com);
        Vector3d v = new Vector3d(handle.getLinearVelocity()).add(new Vector3d(handle.getAngularVelocity()).cross(r));
        if (deck != null) {
            SableBridge.ShipInfo di = SableBridge.shipInfo(sl, deck);
            Vec3 dv = di == null ? Vec3.ZERO : ShipDefence.velocity(sl, di).scale(20.0);
            v.sub(dv.x, dv.y, dv.z);
        } else if (lift[0] != null) {
            Vec3 dv = lift[0].velocity(sl, now);
            v.sub(dv.x, dv.y, dv.z);
        } else if (plate[0] != null) {
            Vec3 dv = plate[0].velocity(sl, now, new Vec3(p.x, p.y, p.z));
            v.sub(dv.x, dv.y, dv.z);
        }
        double vn = v.dot(legW);
        double fn = Math.max(0.0, k * c + damp * vn);
        fn = Math.min(fn, weight * Balance.GEAR_MAX_G);
        Vector3d f = new Vector3d(legW).mul(-fn);

        boolean nose = g.role() == LandingGearBlockEntity.NOSE;
        Vector3d bar = null;
        double barLen = 0.0;
        Vec3 hand = nose ? g.towHand : null;
        if (hand != null) {
            Vec3 hw = g.towOnShip ? SableBridge.toWorld(sl, hand) : hand;
            bar = new Vector3d(hw.x - p.x, hw.y - p.y, hw.z - p.z);
            bar.fma(-bar.dot(legW), legW);
            barLen = bar.length();
            if (barLen < 1.0E-3) bar = null;
            else { bar.div(barLen); g.steer = (float) (castorDeg(pose, roll, legW, bar) / Balance.GEAR_STEER_DEG); }
        }

        Vector3d fwdL = new Vector3d(roll.getStepX(), roll.getStepY(), roll.getStepZ());
        if (g.role() == LandingGearBlockEntity.NOSE && g.steer != 0.0F)
            fwdL.rotateAxis(Math.toRadians(Balance.GEAR_STEER_DEG * g.steer), -leg.getStepX(), -leg.getStepY(), -leg.getStepZ());
        Vector3d fwd = pose.transformNormal(fwdL);
        fwd.fma(-fwd.dot(legW), legW);
        if (fwd.lengthSquared() < 1.0E-9) return false;
        fwd.normalize();
        Vector3d side = new Vector3d(legW).cross(fwd).normalize();
        double vf = v.dot(fwd), vs = v.dot(side), mu = Balance.GEAR_MU * fn;
        double stick = Balance.GEAR_STICK / dt;
        double fs = clamp(-vs * mi * Math.max(Balance.GEAR_SIDE_GRIP, stick), mu);
        double ff = -vf * mi * Balance.GEAR_ROLL;

        Vec3 held = plate[0] == null ? null : plate[0].hold(sl, now, g.ship(), bp);
        boolean chock = held != null;
        if (g.braking() || chock || (g.role() != LandingGearBlockEntity.NOSE && Math.abs(vf) < Balance.GEAR_PARK_SPEED && g.parked)) ff += -vf * mi * Math.max(Balance.GEAR_BRAKE, stick);
        if (chock) {
            Vector3d e = new Vector3d(held.x - p.x, held.y - p.y, held.z - p.z);
            e.fma(-e.dot(legW), legW);
            if (e.length() < Balance.TT_HOLD_MAX) {
                double w = 2.0 * Math.PI * Balance.TT_HOLD_HZ, kh = mi * w * w;
                fs = clamp(fs + kh * e.dot(side), mu);
                ff += kh * e.dot(fwd);
            }
        }
        ff = clamp(ff, (chock ? Balance.GEAR_MU : Balance.GEAR_BRAKE_MU) * fn);
        f.fma(fs, side).fma(ff, fwd);

        if (bar != null) {
            double want = clamp((barLen - g.towLen) * Balance.TOW_GAIN, Balance.TOW_SPEED_MAX);
            f.fma(clamp((want - v.dot(bar)) * m / Balance.TOW_TAU, m * Balance.TOW_ACCEL), bar);
        } else if (nose && g.reversing() && g.reverseNose != null) {
            Vec3 n = g.reverseNose;
            double along = roll.getStepX() * n.x + roll.getStepY() * n.y + roll.getStepZ() * n.z;
            if (Math.abs(along) > 0.5) {
                double want = along > 0.0 ? -Balance.REVERSE_SPEED : Balance.REVERSE_SPEED;
                f.fma(clamp((want - vf) * m / Balance.TOW_TAU, m * Balance.REVERSE_ACCEL), fwd);
            }
        }
        if (!f.isFinite()) return false;

        Vector3d pl = pose.transformPositionInverse(new Vector3d(p)), fl = pose.transformNormalInverse(new Vector3d(f)).mul(dt);
        sub.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(pl, fl);
        g.wow = true;
        g.comp = (float) Math.max(0.0, Math.min(1.0, c / Balance.GEAR_STROKE));
        g.rollSpeed = (float) vf;
        return true;
    }

    private static double partialTick(ServerLevel sl) {
        try { return SubLevelPhysicsSystem.require(sl).getPartialPhysicsTick(); } catch (RuntimeException e) { return 1.0; }
    }

    private static double clamp(double x, double cap) { return Math.max(-cap, Math.min(cap, x)); }

    static double castorDeg(Pose3dc pose, Direction roll, Vector3d legW, Vector3d bar) {
        Vector3d f0 = pose.transformNormal(new Vector3d(roll.getStepX(), roll.getStepY(), roll.getStepZ()));
        f0.fma(-f0.dot(legW), legW);
        if (f0.lengthSquared() < 1.0E-9) return 0.0;
        f0.normalize();
        double a = Math.toDegrees(Math.atan2(-legW.dot(new Vector3d(f0).cross(bar)), f0.dot(bar)));
        if (a > 90.0) a -= 180.0;
        else if (a < -90.0) a += 180.0;
        return clamp(a, Balance.TOW_STEER_DEG);
    }

    private static double terrain(ServerLevel sl, Vec3 from, Vec3 dir, double max, java.util.UUID own, double now, DeckLiftBlockEntity.Floor[] lift, TurntableBlockEntity.Plate[] plate) {
        for (double t = 0.0; t <= max; t += 0.05) {
            Vec3 q = from.add(dir.scale(t));
            DeckLiftBlockEntity.Floor lf = DeckLiftBlockEntity.floorAt(sl, q, now);
            if (lf != null) { lift[0] = lf; return t; }
            TurntableBlockEntity.Plate tp = TurntableBlockEntity.plateAt(sl, q, now);
            if (tp != null) { plate[0] = tp; return t; }
            if (CargoRampBlockEntity.floorAt(sl, q, own)) return t;
            BlockPos b = BlockPos.containing(q);
            if (b.getY() < sl.getMinBuildHeight() || b.getY() >= sl.getMaxBuildHeight()) continue;
            if (sl.getChunkSource().getChunkNow(b.getX() >> 4, b.getZ() >> 4) == null) return Double.NaN;
            BlockState s = sl.getBlockState(b);
            if (s.isAir()) continue;

            net.minecraft.world.phys.shapes.VoxelShape sh = s.getCollisionShape(sl, b);
            if (sh.isEmpty()) continue;
            double lx = q.x - b.getX(), ly = q.y - b.getY(), lz = q.z - b.getZ();
            for (net.minecraft.world.phys.AABB box : sh.toAabbs())
                if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) return t;
        }
        return Double.NaN;
    }
}
