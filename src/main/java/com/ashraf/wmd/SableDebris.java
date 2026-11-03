package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

final class SableDebris {

    private SableDebris() {}

    static final String NAME = "wmd_debris";
    static final String TAG = "wmd_debris_until";

    private static boolean spawning;

    static boolean isDebris(SubLevel s) {
        if (s == null) return false;
        if (spawning || NAME.equals(s.getName())) return true;
        if (s instanceof ServerSubLevel ss) {
            CompoundTag t = ss.getUserDataTag();
            if (t != null && t.contains(TAG)) return true;
        }
        return false;
    }

    static UUID spawn(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, long until) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        ServerSubLevel s;
        spawning = true;
        try {
            s = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, new BoundingBox3i(x0, y0, z0, x1, y1, z1));
        } finally {
            spawning = false;
        }
        if (s == null || s.isRemoved()) return null;
        mark(s, until);
        wake(level, s);
        return s.getUniqueId();
    }

    private static void mark(ServerSubLevel s, long until) {
        s.setName(NAME);
        CompoundTag old = s.getUserDataTag();
        CompoundTag t = old == null ? new CompoundTag() : old.copy();
        t.putLong(TAG, until);
        s.setUserDataTag(t);
    }

    private static boolean push(ServerLevel level, ServerSubLevel s, Vec3 vel, Vec3 offset) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return false;
        RigidBodyHandle h = c.physicsSystem().getPhysicsHandle(s);
        if (h == null || !h.isValid()) return false;
        MassData m = s.getMassTracker();
        if (m == null || m.isInvalid() || !(m.getMass() > 0.0)) return false;
        Quaterniondc q = s.logicalPose().orientation();
        Vector3d j = q.transformInverse(new Vector3d(vel.x, vel.y, vel.z), new Vector3d()).mul(m.getMass());
        Vector3d off = q.transformInverse(new Vector3d(offset.x, offset.y, offset.z), new Vector3d());
        Vector3dc com = m.getCenterOfMass();
        h.applyImpulseAtPoint(new Vec3(com.x() + off.x, com.y() + off.y, com.z() + off.z), new Vec3(j.x, j.y, j.z));
        return true;
    }

    private static void wake(ServerLevel level, ServerSubLevel s) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c != null) c.physicsSystem().getPipeline().wakeUp(s);
    }

    static boolean wake(ServerLevel level, UUID id) {
        ServerSubLevel s = get(level, id);
        if (s == null) return false;
        wake(level, s);
        return true;
    }

    private static ServerSubLevel get(ServerLevel level, UUID id) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return null;
        SubLevel s = c.getSubLevel(id);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() ? ss : null;
    }

    static boolean push(ServerLevel level, UUID id, Vec3 vel, Vec3 spin) {
        ServerSubLevel s = get(level, id);
        return s != null && push(level, s, vel, spin);
    }

    static AABB bounds(ServerLevel level, UUID id) {
        ServerSubLevel s = get(level, id);
        if (s == null) return null;
        BoundingBox3dc b = s.boundingBox();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    static boolean adopt(ServerLevel level, UUID id, long until) {
        ServerSubLevel s = get(level, id);
        if (s == null) return false;
        mark(s, until);
        return true;
    }

    static int crack(ServerLevel level, UUID id, int spacing) {
        ServerSubLevel s = get(level, id);
        if (s == null || s.getPlot() == null) return 0;
        dev.ryanhcode.sable.companion.math.BoundingBox3ic b = s.getPlot().getBoundingBox();
        if (b == null) return 0;
        net.minecraft.util.RandomSource r = level.random;
        int[] lo = { b.minX(), b.minY(), b.minZ() }, hi = { b.maxX(), b.maxY(), b.maxZ() }, q = new int[3];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int removed = 0;
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            for (int c = lo[axis] + spacing / 2 + r.nextInt(spacing / 2 + 1); c < hi[axis] - 1; c += spacing - 1 + r.nextInt(3)) {
                for (int a = lo[u]; a <= hi[u]; a++) for (int e = lo[v]; e <= hi[v]; e++) {
                    int patch = Math.floorDiv(a, 3) * 73856093 ^ Math.floorDiv(e, 3) * 19349663 ^ c * 83492791;
                    q[axis] = c + (patch & 1);
                    q[u] = a;
                    q[v] = e;
                    p.set(q[0], q[1], q[2]);
                    if (level.getBlockState(p).isAir()) continue;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
            }
        }
        return removed;
    }

    static boolean remove(ServerLevel level, UUID id) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return false;
        SubLevel s = c.getSubLevel(id);
        if (!(s instanceof ServerSubLevel ss) || ss.isRemoved()) return false;
        c.removeSubLevel(ss, SubLevelRemovalReason.REMOVED);
        return true;
    }

    static List<SableBridge.DebrisScan> scan(ServerLevel level) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return List.of();
        List<ServerSubLevel> all = c.getAllSubLevels();
        List<SableBridge.DebrisScan> out = new ArrayList<>(all.size());
        for (ServerSubLevel s : all) {
            if (s.isRemoved()) continue;
            CompoundTag t = s.getUserDataTag();
            long until = t != null && t.contains(TAG) ? t.getLong(TAG) : (isDebris(s) ? 0L : -1L);
            BoundingBox3dc b = s.boundingBox();
            out.add(new SableBridge.DebrisScan(s.getUniqueId(), until, s.getSplitFromSubLevel(),
                    new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())));
        }
        return out;
    }
}
