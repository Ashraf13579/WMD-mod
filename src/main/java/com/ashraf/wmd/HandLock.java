package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HandLock {

    private HandLock() {}

    public static final int MANPADS = 0, JAVELIN = 1;

    public static final int T_SHIP = 1, T_DRONE = 2, T_CRUISE = 3, T_ENTITY = 4, T_MACHINE = 5;

    public record Target(int type, UUID ship, int entity, long flight, BlockPos block) {
        String key() { return type + ":" + ship + ":" + entity + ":" + flight + ":" + block; }

        public Vec3 pos(ServerLevel level) {
            switch (type) {
                case T_SHIP -> { SableBridge.ShipInfo s = SableBridge.active() ? SableBridge.shipInfo(level, ship) : null; return s == null ? null : s.worldCenter(); }
                case T_DRONE, T_ENTITY -> { Entity e = level.getEntity(entity); return e == null || e.isRemoved() ? null : e.position().add(0, e.getBbHeight() * 0.5, 0); }
                case T_CRUISE -> { Flight f = FlightManager.get(level).find(flight); long now = level.getGameTime(); return f == null || now >= f.endTime() ? null : f.position(now); }
                case T_MACHINE -> { return level.getBlockState(block).isAir() ? null : Vec3.atCenterOf(block); }
                default -> { return null; }
            }
        }
    }

    private static final class State { Target target; int ticks; boolean sentLocked; int findAt = -100; Target found; }

    private static final Map<UUID, State> STATES = Statics.server(new HashMap<>());

    public static double range(int kind) { return kind == MANPADS ? Balance.MANPADS_RANGE : Balance.JAV_RANGE; }

    static void tick(ServerPlayer sp, int kind) {
        State s = STATES.computeIfAbsent(sp.getUUID(), u -> new State());
        if (sp.tickCount - s.findAt >= 4 || sp.tickCount < s.findAt) { s.found = find(sp, kind); s.findAt = sp.tickCount; }
        Target t = s.found;
        if (t == null) { s.target = null; s.ticks = 0; }
        else if (s.target != null && s.target.key().equals(t.key())) s.ticks++;
        else { s.target = t; s.ticks = 1; }
        boolean locked = s.target != null && s.ticks >= Balance.HAND_LOCK_TICKS;
        if (sp.tickCount % 2 == 0 || locked != s.sentLocked) {
            Vec3 p = s.target == null ? null : s.target.pos(sp.serverLevel());
            int dist = p == null ? 0 : (int) Math.round(p.distanceTo(sp.getEyePosition()));
            PacketDistributor.sendToPlayer(sp, new HandLockPayload(kind, s.target == null ? 0 : s.target.type(), Math.min(s.ticks, Balance.HAND_LOCK_TICKS), locked, dist));
            s.sentLocked = locked;
        }
    }

    static void clear(ServerPlayer sp, int kind) {
        if (STATES.remove(sp.getUUID()) != null) PacketDistributor.sendToPlayer(sp, new HandLockPayload(kind, 0, 0, false, 0));
    }

    static Target locked(ServerPlayer sp, int kind) {
        State s = STATES.get(sp.getUUID());
        if (s == null || s.target == null || s.ticks < Balance.HAND_LOCK_TICKS) return null;
        Target t = find(sp, kind);
        return t != null && t.key().equals(s.target.key()) ? s.target : null;
    }

    private static Target find(ServerPlayer sp, int kind) {
        ServerLevel level = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        double range = range(kind), minCos = Math.cos(Math.toRadians(Balance.HAND_LOCK_CONE));
        Target best = null;
        double bestCos = minCos;
        if (kind == MANPADS) {
            if (SableBridge.active()) {
                for (SableBridge.ShipInfo s : SableBridge.allShips(level)) {
                    Vec3 c = s.worldCenter();
                    if (c.distanceTo(eye) > range + 32.0) continue;
                    double cos = cosTo(eye, look, c);
                    if (s.worldBounds().clip(eye, eye.add(look.scale(range))).isPresent()) cos = Math.max(cos, minCos + 1.0E-6);
                    if (cos >= bestCos && !SmokeScreens.inside(level, c)) { bestCos = cos; best = new Target(T_SHIP, s.id(), 0, 0L, null); }
                }
            }
            for (ReconDroneEntity d : level.getEntitiesOfClass(ReconDroneEntity.class, new AABB(eye, eye).inflate(range))) {
                Vec3 c = d.position().add(0, d.getBbHeight() * 0.5, 0);
                double cos = cosTo(eye, look, c);
                if (cos >= bestCos && c.distanceTo(eye) <= range && clear(sp, eye, c)) { bestCos = cos; best = new Target(T_DRONE, null, d.getId(), 0L, null); }
            }
            long now = level.getGameTime();
            for (Flight f : FlightManager.get(level).flights()) {
                if (!f.isCruise() || now < f.start() || now >= f.endTime()) continue;
                Vec3 c = f.position(now);
                if (c.distanceTo(eye) > range) continue;
                double cos = cosTo(eye, look, c);
                if (cos >= bestCos && clear(sp, eye, c) && !SmokeScreens.inside(level, c)) { bestCos = cos; best = new Target(T_CRUISE, null, 0, f.id(), null); }
            }
            return best;
        }

        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
                e -> e != sp && e.isAlive() && (e instanceof Player p ? !p.isSpectator() : e instanceof Mob && e.getBbWidth() * e.getBbWidth() * e.getBbHeight() >= 1.0F));
        for (LivingEntity e : near) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double cos = cosTo(eye, look, c);
            if (cos >= bestCos && c.distanceTo(eye) <= range && clear(sp, eye, c) && !SmokeScreens.inside(level, c)) { bestCos = cos; best = new Target(T_ENTITY, null, e.getId(), 0L, null); }
        }
        if (best != null) return best;
        BlockHitResult hit = BeamTrace.hit(level, eye, look, range);
        if (hit == null) return null;
        BlockPos bp = hit.getBlockPos();
        BlockEntity be = level.getBlockEntity(bp);
        boolean machine = level.getBlockState(bp).getBlock() instanceof MachinePartBlock
                || be != null && Wmd.MODID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace());
        if (!machine || SmokeScreens.inside(level, Vec3.atCenterOf(bp))) return null;
        return new Target(T_MACHINE, null, 0, 0L, bp.immutable());
    }

    private static double cosTo(Vec3 eye, Vec3 look, Vec3 p) {
        Vec3 d = p.subtract(eye);
        double l = d.length();
        return l < 1.0E-6 ? 1.0 : d.dot(look) / l;
    }

    private static boolean clear(ServerPlayer sp, Vec3 eye, Vec3 p) {
        ServerLevel level = sp.serverLevel();
        BlockHitResult hit = BeamTrace.hit(level, eye, p.subtract(eye), eye.distanceTo(p));
        if (hit != null && hit.getLocation().distanceTo(p) > 1.5) return false;
        double sm = SmokeScreens.entry(level, eye, p);
        return sm < 0.0 || sm > eye.distanceTo(p) - 1.0;
    }
}
