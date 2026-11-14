package com.ashraf.wmd;

import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class AnchorHold {

    private AnchorHold() {}

    private static volatile boolean jointBroken;

    static final class Joint {
        private final Object handle;
        private final UUID ship;
        private double tx = Double.NaN, tz = Double.NaN, stiff = -1.0, cap = -1.0;

        private Joint(Object handle, UUID ship) { this.handle = handle; this.ship = ship; }

        UUID ship() { return ship; }
    }

    static boolean jointOk() { return !jointBroken && SableBridge.active(); }

    private static void broke(Throwable t) {
        if (!jointBroken) Wmd.LOGGER.warn("WMD anchor: Sable's world joint failed, anchors hold with the spring push instead", t);
        jointBroken = true;
    }

    private static ServerSubLevel sub(ServerLevel sl, UUID id) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
        if (c == null) return null;
        SubLevel s = c.getSubLevel(id);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() ? ss : null;
    }

    static double mass(ServerLevel sl, UUID ship) {
        if (!SableBridge.active() || sl == null || ship == null) return Double.NaN;
        try {
            ServerSubLevel s = sub(sl, ship);
            MassData m = s == null ? null : s.getMassTracker();
            return m == null || m.isInvalid() || !(m.getMass() > 0.0) ? Double.NaN : m.getMass();
        } catch (Throwable t) { SableBridge.fail(t); return Double.NaN; }
    }

    static Joint attach(ServerLevel sl, UUID ship, Vec3 anchor, Vec3 hawsePlot) {
        if (!jointOk() || sl == null || ship == null || anchor == null || hawsePlot == null) return null;
        try {
            ServerSubLevelContainer c = SubLevelContainer.getContainer(sl);
            ServerSubLevel s = sub(sl, ship);
            if (c == null || s == null) return null;
            Quaterniond q2 = new Quaterniond(s.logicalPose().orientation()).conjugate();
            GenericConstraintConfiguration cfg = new GenericConstraintConfiguration(new Vector3d(anchor.x, anchor.y, anchor.z),
                    new Vector3d(hawsePlot.x, hawsePlot.y, hawsePlot.z), new Quaterniond(), q2);
            GenericConstraintHandle h = c.physicsSystem().getPipeline().addConstraint(null, s, cfg);
            if (h == null) { broke(new IllegalStateException("addConstraint returned null (no Rapier pipeline?)")); return null; }
            h.setContactsEnabled(false);
            c.physicsSystem().getPipeline().wakeUp(s);
            return new Joint(h, ship);
        } catch (Throwable t) { broke(t); return null; }
    }

    static boolean valid(Joint j) {
        if (j == null || jointBroken) return false;
        try { return ((GenericConstraintHandle) j.handle).isValid(); } catch (Throwable t) { broke(t); return false; }
    }

    static void release(Joint j) {
        if (j == null) return;
        try { GenericConstraintHandle h = (GenericConstraintHandle) j.handle; if (h.isValid()) h.remove(); } catch (Throwable t) { broke(t); }
    }

    static void moveAnchor(Joint j, Vec3 anchor) {
        if (j == null || anchor == null) return;
        try { ((GenericConstraintHandle) j.handle).setFrame1(new Vector3d(anchor.x, anchor.y, anchor.z), new Quaterniond()); } catch (Throwable t) { broke(t); }
    }

    static void motors(Joint j, double tx, double tz, double stiff, double damp, double cap, double yaw) {
        if (j == null) return;
        if (Math.abs(tx - j.tx) < 0.02 && Math.abs(tz - j.tz) < 0.02 && Math.abs(stiff - j.stiff) <= 1.0E-6 * Math.max(1.0, stiff) && Math.abs(cap - j.cap) <= 1.0E-6 * Math.max(1.0, cap)) return;
        try {
            GenericConstraintHandle h = (GenericConstraintHandle) j.handle;
            h.setMotor(ConstraintJointAxis.LINEAR_X, tx, stiff, damp, true, cap);
            h.setMotor(ConstraintJointAxis.LINEAR_Z, tz, stiff, damp, true, cap);
            h.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, 0.0, yaw, true, cap * 4.0);
            j.tx = tx; j.tz = tz; j.stiff = stiff; j.cap = cap;
        } catch (Throwable t) { broke(t); }
    }

    static double pull(Joint j) {
        if (j == null) return Double.NaN;
        try {
            Vector3d lin = new Vector3d(), ang = new Vector3d();
            ((GenericConstraintHandle) j.handle).getJointImpulses(lin, ang);
            return lin.length();
        } catch (Throwable t) { broke(t); return Double.NaN; }
    }
}
