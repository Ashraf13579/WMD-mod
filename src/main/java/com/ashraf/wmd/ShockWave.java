package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShockWave {

    private ShockWave() {}

    private static final class Wave {
        final ServerLevel level;
        final Vec3 at;
        final double inner, reach, strength;
        final long t0;
        double front;
        final Set<UUID> shipsDone = new HashSet<>();
        Wave(ServerLevel level, Vec3 at, double inner, double reach, double strength, long t0) {
            this.level = level; this.at = at; this.inner = inner; this.reach = reach; this.strength = strength; this.t0 = t0;
        }
    }

    private static final List<Wave> WAVES = Statics.server(new ArrayList<>());

    static void start(ServerLevel level, Vec3 at, double damageReach) {
        if (!Config.Server.shockwave() || damageReach < 2.0 || WAVES.size() >= Balance.SHOCK_MAX_WAVES) return;
        double reach = Math.min(damageReach * Balance.SHOCK_REACH_MUL, Balance.SHOCK_MAX_R);
        WAVES.add(new Wave(level, at, damageReach, reach, Math.min(3.0, 0.6 + damageReach / 40.0), level.getGameTime()));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        long pt0 = ImpactProfiler.start();
        try { tick(event); } finally { ImpactProfiler.add(ImpactProfiler.SHOCKWAVE, pt0); }
    }

    private static void tick(LevelTickEvent.Post event) {
        if (WAVES.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.level != sl) continue;
            double was = w.front;
            w.front = Math.min(w.reach, (now - w.t0 + 1) * Balance.SHOCK_SPEED);
            if (w.front > was) pass(w, was, w.front);
            if (w.front >= w.reach) it.remove();
        }
    }

    private static void pass(Wave w, double r0, double r1) {
        ServerLevel level = w.level;
        for (Entity e : level.getEntities((Entity) null, new AABB(w.at, w.at).inflate(r1, Math.min(r1, 96.0), r1), e -> e.isAlive() && !e.isSpectator())) {
            Vec3 d = e.position().subtract(w.at);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            if (h < r0 || h >= r1) continue;
            if (e instanceof LivingEntity && h <= w.inner) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e.isPassenger()) continue;
            if (LoadGuard.overloaded() && !(e instanceof Player)) continue;
            double k = 1.0 - h / w.reach;
            k = k * k * w.strength;
            if (k < 0.02) continue;
            Vec3 dir = h < 1.0E-3 ? new Vec3(0, 1, 0) : new Vec3(d.x / h, 0.0, d.z / h);
            double mass = e instanceof LivingEntity ? 0.6 : 1.0;
            e.push(dir.x * k * mass, 0.15 + 0.35 * k * mass, dir.z * k * mass);
            e.hurtMarked = true;
        }
        if (!SableBridge.active()) return;
        try {
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, new AABB(w.at, w.at).inflate(r1))) {
                if (w.shipsDone.contains(s.id())) continue;
                AABB b = s.worldBounds();
                Vec3 near = new Vec3(Math.max(b.minX, Math.min(w.at.x, b.maxX)), Math.max(b.minY, Math.min(w.at.y, b.maxY)), Math.max(b.minZ, Math.min(w.at.z, b.maxZ)));
                double h = near.distanceTo(w.at);
                if (h >= r1 || h < w.inner * 0.5) continue;
                w.shipsDone.add(s.id());
                double k = 1.0 - h / w.reach;
                SableImpact.shove(level, s, near, (float) (Balance.SHOCK_SHIP_POWER * k * k * w.strength));
            }
        } catch (Throwable t) {
            Wmd.LOGGER.debug("[shockwave] ship push skipped: {}", t.toString());
        }
    }
}
