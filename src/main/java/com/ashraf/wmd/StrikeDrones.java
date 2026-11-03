package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class StrikeDrones {

    private StrikeDrones() {}

    static final int HUNT = 6, STRIKE = 7, HOME = 8, DOCK = 9, PILOT = 10;
    static final double CLIMB_UP = 6.0, CLIMB_RATE = 0.35, DOCK_RATE = 0.25, PATROL_R = 12.0, ORBIT_R = 2.5, DIVE_AT = 10.0, BOOST = 1.45;

    static Component launch(ServerLevel level, DroneHiveBlockEntity hive, int bay, DroneType t, ServerPlayer by, boolean fly, int[] out) {
        MinecraftServer server = level.getServer();
        if (level != server.overworld()) return Component.translatable("message.wmd.hive.overworld");
        Drones ds = Drones.get(server);
        Drones.Drone d = new Drones.Drone();
        d.id = ds.newId();
        d.owner = hive.getOwner() != null ? hive.getOwner() : by.getUUID();
        d.ownerName = hive.getOwnerName() != null && !hive.getOwnerName().isEmpty() ? hive.getOwnerName() : by.getGameProfile().getName();
        d.kind = t.tier;
        Vec3 pad = hive.bayWorld(bay);
        d.hx = d.x = pad.x; d.hy = d.y = pad.y; d.hz = d.z = pad.z;
        BlockPos hp = hive.getBlockPos();
        d.hiveX = hp.getX(); d.hiveY = hp.getY(); d.hiveZ = hp.getZ(); d.bay = bay;
        d.huntPlayers = hive.huntPlayers(bay); d.huntMobs = hive.huntMobs(bay);
        d.battery = t == DroneType.T1 ? Balance.DRONE_T1_BATTERY : Balance.DRONE_T2_BATTERY;
        d.health = t == DroneType.T1 ? Balance.DRONE_T1_HEALTH : Balance.DRONE_T2_HEALTH;
        d.yaw = by.getYRot();
        d.phase = Drones.CLIMB;
        if (fly) { d.pilot = by.getUUID(); d.ctlYaw = by.getYRot(); DronePilots.begin(by); }
        ds.add(d);
        out[0] = d.id;
        if (fly) sendFly(by, d, level);
        level.playSound(null, hp, WmdSounds.HIVE_LID.get(), SoundSource.BLOCKS, 1.0F, t == DroneType.T1 ? 1.1F : 0.95F);
        Wmd.LOGGER.info("Hive drone #{} ({}) of {} launched from {} {}{}", d.id, t.id(), d.ownerName, hp.getX(), hp.getZ(), fly ? " (flown)" : "");
        return null;
    }

    static void recall(MinecraftServer server, int id) {
        Drones ds = Drones.get(server);
        Drones.Drone d = ds.byId(id);
        if (d == null || d.kind == 0 || !d.airborne() || d.phase == DOCK) return;
        endPilot(server, d);
        d.target = null;
        d.phase = HOME;
    }

    static void control(ServerPlayer p, int id, float f, float s, float u, float yaw, boolean fire, boolean leave, boolean boost) {
        Drones.Drone d = Drones.get(p.server).byId(id);
        if (d == null || d.pilot == null || !d.pilot.equals(p.getUUID())) return;
        d.ctlF = Float.isFinite(f) ? Mth.clamp(f, -1.0F, 1.0F) : 0.0F; d.ctlS = Float.isFinite(s) ? Mth.clamp(s, -1.0F, 1.0F) : 0.0F; d.ctlU = Float.isFinite(u) ? Mth.clamp(u, -1.0F, 1.0F) : 0.0F;
        if (Float.isFinite(yaw)) d.ctlYaw = Mth.wrapDegrees(yaw);
        d.ctlFire |= fire; d.ctlLeave |= leave; d.ctlBoost = boost;
    }

    static void releasePilot(ServerPlayer sp) {
        for (Drones.Drone d : Drones.get(sp.server).drones())
            if (sp.getUUID().equals(d.pilot)) { endPilot(sp.server, d); if (d.airborne() && d.kind < 3) { d.target = null; d.phase = HOME; } }
    }

    static void endPilot(MinecraftServer server, Drones.Drone d) {
        if (d.pilot == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(d.pilot);
        d.pilot = null;
        d.ctlF = d.ctlS = d.ctlU = 0.0F; d.ctlFire = d.ctlLeave = d.ctlBoost = false;
        if (p != null) { PacketDistributor.sendToPlayer(p, DroneFlyPayload.stop(d.id)); DronePilots.end(p); }
    }

    static double flyRange(MinecraftServer server) { return Balance.DRONE_T2_RANGE; }

    private static void sendFly(ServerPlayer p, Drones.Drone d, ServerLevel level) {
        int g = Drones.groundAt(level, d.x, d.z);
        int full = d.kind == 1 ? Balance.DRONE_T1_BATTERY : Balance.DRONE_T2_BATTERY;
        PacketDistributor.sendToPlayer(p, new DroneFlyPayload(d.id, true, d.x, d.y, d.z, d.yaw, Mth.clamp(d.battery * 100 / full, 0, 100),
                g == Integer.MIN_VALUE ? -1 : (int) Math.round(d.y - g), (int) Math.round(Math.hypot(d.x - d.hx, d.z - d.hz)), (int) Math.round(flyRange(level.getServer()))));
    }

    static boolean step(Drones ds, ServerLevel level, Drones.Drone d, long now) {
        MinecraftServer server = level.getServer();
        double speed = d.kind == 1 ? Balance.DRONE_T1_SPEED : Balance.DRONE_T2_SPEED;
        if (--d.battery <= 0) {
            Drones.tell(server, d.owner, Component.translatable("message.wmd.hive_drone.battery", d.id).withStyle(ChatFormatting.RED));
            ds.shootDown(level, d, null, "battery");
            return false;
        }
        switch (d.phase) {
            case Drones.CLIMB -> {
                d.vx = 0; d.vz = 0; d.vy = CLIMB_RATE;
                d.y += CLIMB_RATE;
                if (d.y >= d.hy + CLIMB_UP) d.phase = d.pilot != null ? PILOT : d.kind == 1 ? HUNT : STRIKE;
                if (d.pilot != null) pilotTick(level, d);
            }
            case HUNT -> hunt(level, d, now, speed);
            case STRIKE -> { if (strike(ds, level, d, now, speed)) return true; }
            case PILOT -> { if (pilot(ds, level, d, speed)) return true; }
            case HOME -> {
                refreshHome(level, d);
                if (moveTo(level, d, d.hx, d.hy + CLIMB_UP, d.hz, speed) < 0.6) {
                    d.phase = DOCK;
                    if (level.getBlockEntity(new BlockPos(d.hiveX, d.hiveY, d.hiveZ)) instanceof DroneHiveBlockEntity h) h.openLid(d.bay);
                }
            }
            case DOCK -> {
                refreshHome(level, d);
                d.vx = 0; d.vz = 0; d.vy = -DOCK_RATE;
                d.x = d.hx; d.z = d.hz;
                d.y = Math.max(d.hy, d.y - DOCK_RATE);
                if (d.y <= d.hy + 0.02) { dock(level, d); return true; }
            }
            default -> d.phase = HOME;
        }
        if ((d.phase == HUNT || d.phase == STRIKE) && d.battery < homeTicks(d, speed) + 80) {
            d.target = null;
            d.phase = HOME;
            Drones.tell(server, d.owner, Component.translatable("message.wmd.hive_drone.home", d.id).withStyle(ChatFormatting.GRAY));
        }
        return false;
    }

    private static void refreshHome(ServerLevel level, Drones.Drone d) {
        BlockPos hp = new BlockPos(d.hiveX, d.hiveY, d.hiveZ);
        if (!level.isLoaded(hp) || !(level.getBlockEntity(hp) instanceof DroneHiveBlockEntity h)) return;
        Vec3 p = h.bayWorld(d.bay);
        d.hx = p.x; d.hy = p.y; d.hz = p.z;
    }

    private static double homeTicks(Drones.Drone d, double speed) {
        return Math.hypot(d.x - d.hx, d.z - d.hz) / speed + Math.abs(d.y - d.hy) / DOCK_RATE + CLIMB_UP / CLIMB_RATE;
    }

    private static void hunt(ServerLevel level, Drones.Drone d, long now, double speed) {
        LivingEntity tgt = d.target != null && level.getEntity(d.target) instanceof LivingEntity le && valid(level, d, le, Balance.DRONE_T1_RANGE) ? le : null;
        if (tgt == null && (now + d.id) % 10 == 0) tgt = find(level, d, Balance.DRONE_T1_RANGE, d.huntPlayers, d.huntMobs);
        if (tgt == null) {
            d.target = null;
            d.angle += speed / PATROL_R;
            moveTo(level, d, d.hx + Math.cos(d.angle) * PATROL_R, d.hy + 10.0, d.hz + Math.sin(d.angle) * PATROL_R, speed);
            return;
        }
        d.target = tgt.getUUID();
        d.angle += 0.06;
        Vec3 aim = tgt.position().add(Math.cos(d.angle) * ORBIT_R, tgt.getBbHeight() + 2.0, Math.sin(d.angle) * ORBIT_R);
        moveTo(level, d, aim.x, aim.y, aim.z, speed);
        d.yaw = (float) Math.toDegrees(Math.atan2(-(tgt.getX() - d.x), tgt.getZ() - d.z));
        if (--d.cool > 0) return;
        Vec3 from = d.pos(), at = tgt.getEyePosition();
        if (from.distanceTo(at) > Balance.DRONE_T1_SHOT_RANGE) return;
        if (level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tgt)).getType() == HitResult.Type.BLOCK) return;
        d.cool = Balance.DRONE_T1_SHOT_TICKS;
        Entity standIn = d.entity != null ? level.getEntity(d.entity) : null;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(d.owner);
        DamageSource src = standIn != null ? level.damageSources().mobProjectile(standIn, owner) : level.damageSources().generic();
        tgt.hurt(src, Balance.DRONE_T1_DAMAGE);
        for (int k = 1; k <= 5; k++) {
            Vec3 p = from.lerp(at, k / 6.0);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SMOKE, from.x, from.y - 0.1, from.z, 2, 0.05, 0.05, 0.05, 0.01);
        level.playSound(null, from.x, from.y, from.z, WmdSounds.DRONE_GUN.get(), SoundSource.HOSTILE, 2.0F, 0.95F + level.random.nextFloat() * 0.1F);
    }

    private static boolean strike(Drones ds, ServerLevel level, Drones.Drone d, long now, double speed) {
        LivingEntity tgt = d.target != null && level.getEntity(d.target) instanceof LivingEntity le && valid(level, d, le, Balance.DRONE_T2_RANGE) ? le : null;
        if (tgt == null) tgt = find(level, d, Balance.DRONE_T2_RANGE, true, false);
        if (tgt == null) {
            Drones.tell(level.getServer(), d.owner, Component.translatable("message.wmd.hive_drone.no_target", d.id).withStyle(ChatFormatting.GRAY));
            d.target = null;
            d.phase = HOME;
            return false;
        }
        d.target = tgt.getUUID();
        double hd = Math.hypot(tgt.getX() - d.x, tgt.getZ() - d.z);
        if (hd > DIVE_AT) {
            int g = Drones.groundAt(level, d.x, d.z);
            double cruise = Math.max((g == Integer.MIN_VALUE ? d.y : g) + 14.0, tgt.getY() + 12.0);
            moveTo(level, d, tgt.getX(), cruise, tgt.getZ(), speed);
            return false;
        }
        Vec3 aim = tgt.position().add(0.0, tgt.getBbHeight() * 0.5, 0.0);
        moveTo(level, d, aim.x, aim.y, aim.z, speed * 1.4);
        if (d.pos().distanceTo(aim) < 1.6 || solid(level, d.x, d.y - 0.4, d.z)) { detonate(level, d); return true; }
        return false;
    }

    private static boolean pilot(Drones ds, ServerLevel level, Drones.Drone d, double speed) {
        MinecraftServer server = level.getServer();
        ServerPlayer p = d.pilot == null ? null : server.getPlayerList().getPlayer(d.pilot);
        if (p == null || p.level() != level || !p.isAlive()) { endPilot(server, d); d.phase = HOME; return false; }
        Vec3 body = DronePilots.home(p);
        if ((body == null ? new Vec3(d.hx, d.hy, d.hz) : body).distanceTo(d.pos()) > flyRange(server)) {
            p.displayClientMessage(Component.translatable("message.wmd.hive_drone.signal"), true);
            endPilot(server, d);
            d.phase = HOME;
            return false;
        }
        if (d.ctlLeave) { endPilot(server, d); d.phase = HOME; return false; }
        if (d.ctlFire) { detonate(level, d); return true; }
        if (d.ctlBoost) { speed *= BOOST; d.battery--; }
        double rad = Math.toRadians(d.ctlYaw), sin = Math.sin(rad), cos = Math.cos(rad);
        d.vx = (d.ctlS * cos - d.ctlF * sin) * speed;
        d.vz = (d.ctlF * cos + d.ctlS * sin) * speed;
        d.vy = d.ctlU * 0.4;
        d.yaw = d.ctlYaw;
        double nx = d.x + d.vx, ny = d.y + d.vy, nz = d.z + d.vz;
        if (solid(level, nx, ny, nz) || solid(level, nx, ny + 0.3, nz)) {
            if (Math.hypot(d.vx, d.vz) + Math.abs(d.vy) > 0.35) { detonate(level, d); return true; }
            d.vx = d.vy = d.vz = 0.0;
        } else { d.x = nx; d.y = ny; d.z = nz; }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(d.x - 0.9, d.y - 0.9, d.z - 0.9, d.x + 0.9, d.y + 0.9, d.z + 0.9),
                e -> e.isAlive() && e != p && !e.isSpectator());
        if (!near.isEmpty()) { detonate(level, d); return true; }
        pilotTick(level, d);
        return false;
    }

    private static void pilotTick(ServerLevel level, Drones.Drone d) {
        ServerPlayer p = d.pilot == null ? null : level.getServer().getPlayerList().getPlayer(d.pilot);
        if (p != null) { DronePilots.follow(p, d.x, d.y, d.z); sendFly(p, d, level); }
    }

    private static double moveTo(ServerLevel level, Drones.Drone d, double px, double py, double pz, double speed) {
        double dx = px - d.x, dy = py - d.y, dz = pz - d.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double s = Math.min(speed, dist);
        if (dist < 1.0E-6) { d.vx = d.vy = d.vz = 0.0; return 0.0; }
        d.vx = dx / dist * s; d.vy = dy / dist * s; d.vz = dz / dist * s;
        double nx = d.x + d.vx, ny = d.y + d.vy, nz = d.z + d.vz;
        if (solid(level, nx, ny, nz) || solid(level, nx, ny + 0.3, nz)) {
            d.vx = 0.0; d.vz = 0.0; d.vy = 0.4;
            ny = d.y + 0.4;
            if (!solid(level, d.x, ny + 0.3, d.z)) d.y = ny;
        } else { d.x = nx; d.y = ny; d.z = nz; }
        if (Math.hypot(d.vx, d.vz) > 1.0E-3) d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
        return Math.max(0.0, dist - s);
    }

    static boolean solid(ServerLevel level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        return ch != null && !ch.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    private static boolean valid(ServerLevel level, Drones.Drone d, LivingEntity e, double range) {
        if (!e.isAlive() || e.level() != level || e.isSpectator()) return false;
        if (Math.hypot(e.getX() - d.hx, e.getZ() - d.hz) > range) return false;
        if (e instanceof ServerPlayer sp) {
            if (sp.isCreative() || sp.getUUID().equals(d.owner)) return false;
            return d.kind == 2 || d.huntPlayers ? !CommandCenter.ownersTrusting(sp.getUUID()).contains(d.owner) : false;
        }
        return d.kind == 1 && d.huntMobs && e instanceof Enemy;
    }

    private static LivingEntity find(ServerLevel level, Drones.Drone d, double range, boolean players, boolean mobs) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        if (players) for (ServerPlayer p : level.players()) {
            if (!valid(level, d, p, range)) continue;
            double q = p.distanceToSqr(d.x, d.y, d.z);
            if (q < bd) { bd = q; best = p; }
        }
        if (mobs) {
            AABB box = new AABB(d.hx - range, d.hy - 48.0, d.hz - range, d.hx + range, d.hy + 64.0, d.hz + range);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy)) {
                if (!valid(level, d, e, range)) continue;
                double q = e.distanceToSqr(d.x, d.y, d.z);
                if (q < bd) { bd = q; best = e; }
            }
        }
        return best;
    }

    private static void detonate(ServerLevel level, Drones.Drone d) {
        MinecraftServer server = level.getServer();
        endPilot(server, d);
        if (level.getChunkSource().getChunkNow((int) Math.floor(d.x) >> 4, (int) Math.floor(d.z) >> 4) != null)
            level.explode(null, d.x, d.y, d.z, Balance.DRONE_T2_BLAST, Config.Server.blockDamage() && Config.Server.canBreak(level, BlockPos.containing(d.x, d.y, d.z)) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        Drones.tell(server, d.owner, Component.translatable("message.wmd.hive_drone.boom", d.id, Math.round(d.x), Math.round(d.z)).withStyle(ChatFormatting.GOLD));
        Wmd.LOGGER.info("Hive drone #{} of {} detonated at {} {} {}", d.id, d.ownerName, Math.round(d.x), Math.round(d.y), Math.round(d.z));
    }

    private static void dock(ServerLevel level, Drones.Drone d) {
        DroneType t = d.kind == 1 ? DroneType.T1 : DroneType.T2;
        BlockPos hp = new BlockPos(d.hiveX, d.hiveY, d.hiveZ);
        boolean ok = level.isLoaded(hp) && level.getBlockEntity(hp) instanceof DroneHiveBlockEntity h && h.dock(d.bay, d.id, t);
        if (!ok && level.getChunkSource().getChunkNow((int) Math.floor(d.hx) >> 4, (int) Math.floor(d.hz) >> 4) != null) {
            ItemEntity it = new ItemEntity(level, d.hx, d.hy + 0.2, d.hz, t.stack());
            it.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(it);
        }
        Drones.tell(level.getServer(), d.owner, Component.translatable("message.wmd.hive_drone.docked", d.id).withStyle(ChatFormatting.AQUA));
    }

    static boolean flying(MinecraftServer server, int id) { return Drones.alive(server, id); }

    static UUID pilotOf(MinecraftServer server, int id) {
        Drones.Drone d = Drones.get(server).byId(id);
        return d == null ? null : d.pilot;
    }
}
