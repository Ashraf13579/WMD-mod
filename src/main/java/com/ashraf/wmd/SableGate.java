package com.ashraf.wmd;

import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SableGate {

    private SableGate() {}

    private static ServerSubLevel sub(ServerLevel level, UUID id) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return null;
        return c.getSubLevel(id) instanceof ServerSubLevel s && !s.isRemoved() ? s : null;
    }

    static Vec3 position(ServerLevel level, UUID id) {
        ServerSubLevel s = sub(level, id);
        if (s == null) return null;
        Vector3dc p = s.logicalPose().position();
        return new Vec3(p.x(), p.y(), p.z());
    }

    static boolean jump(ServerLevel level, UUID id, Vec3 from, Vec3 to, double theta) {
        ServerSubLevel s = sub(level, id);
        if (s == null) return false;
        RigidBodyHandle h = RigidBodyHandle.of(s);
        if (h == null || !h.isValid()) return false;
        Pose3dc pose = s.logicalPose();
        Vector3dc p = pose.position();
        Quaterniond r = new Quaterniond().rotationY(theta);
        Vector3d rel = r.transform(new Vector3d(p.x() - from.x, p.y() - from.y, p.z() - from.z));
        Vector3d dest = new Vector3d(to.x + rel.x, to.y + rel.y, to.z + rel.z);
        Quaterniond q = new Quaterniond(r).mul(pose.orientation());
        Vector3d v = h.getLinearVelocity(new Vector3d()), w = h.getAngularVelocity(new Vector3d());
        h.addLinearAndAngularVelocity(new Vector3d(v).negate(), new Vector3d(w).negate());
        h.teleport(dest, q);
        h.addLinearAndAngularVelocity(r.transform(new Vector3d(v)), r.transform(new Vector3d(w)));
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c != null) c.physicsSystem().getPipeline().wakeUp(s);
        return true;
    }
}
