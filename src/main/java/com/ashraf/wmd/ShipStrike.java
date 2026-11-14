package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipStrike {

    private ShipStrike() {}

    private static UUID current;

    static UUID begin(UUID shooter) { UUID prev = current; current = shooter; return prev; }

    static void end(UUID previous) { current = previous; }

    static UUID shooter() { return current; }

    static float powerFor(double crater) {
        return (float) Math.max(Balance.SHIP_BLAST_MIN, Math.min(Balance.SHIP_BLAST_MAX, crater * Balance.SHIP_BLAST_PER_RADIUS));
    }

    static float powerForGun(int gun) {
        if (gun == Artillery.MORTAR) return (float) Balance.SHIP_BLAST_MORTAR;
        if (gun == Artillery.ROCKET) return (float) Balance.SHIP_BLAST_ROCKET;
        return (float) Balance.SHIP_BLAST_HOWITZER;
    }

    private record Req(Vec3 pos, double radius, float power, UUID shooter, long at) {}

    private static final Map<ResourceKey<Level>, ArrayDeque<Req>> QUEUE = new HashMap<>();

    private static final Map<ResourceKey<Level>, long[]> USED = new HashMap<>();
    private static boolean warnedFull;

    public static int blast(ServerLevel level, Vec3 pos, double radius, float power, UUID shooter) {
        if (level == null || pos == null || power <= 0.0F || !SableBridge.active()) return 0;
        AirEngineBlockEntity.blastAt(level, pos, radius, power);
        long now = level.getGameTime();
        long[] u = USED.computeIfAbsent(level.dimension(), k -> new long[2]);
        if (u[0] != now) { u[0] = now; u[1] = 0; }
        if (u[1] >= Math.max(1, Balance.SHIP_BLAST_MAX_PER_TICK)) {
            if (anyShips(level)) {
                ArrayDeque<Req> q = QUEUE.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
                if (q.size() >= Math.max(1, Balance.SHIP_BLAST_QUEUE)) {
                    q.pollFirst();
                    if (!warnedFull) { warnedFull = true; Wmd.LOGGER.warn("WMD ship collateral: queue full, the oldest ship blasts are dropped (Balance.SHIP_BLAST_QUEUE)"); }
                }
                q.addLast(new Req(pos, radius, power, shooter, now));
            }
            return 0;
        }
        int n = run(level, pos, radius, power, shooter);
        u[1] += n;
        return n;
    }

    public static int chew(ServerLevel level, UUID shipId, Vec3 from, Vec3 to, int maxBlocks, float maxHardness, UUID shooter) {
        if (level == null || shipId == null || !SableBridge.active()) return 0;
        AirEngineBlockEntity.rayAt(level, from, to, maxBlocks);
        TankDamage.rayAt(level, from, to, maxBlocks);
        try { return SableImpact.breakAlong(level, shipId, from, to, maxBlocks, maxHardness, shooter); }
        catch (Throwable t) { SableBridge.fail(t); return 0; }
    }

    private static int run(ServerLevel level, Vec3 pos, double radius, float power, UUID shooter) {
        try { return SableImpact.collateral(level, pos, radius, power, shooter); }
        catch (Throwable t) { SableBridge.fail(t); return 0; }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (QUEUE.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        ArrayDeque<Req> q = QUEUE.get(level.dimension());
        if (q == null || q.isEmpty()) return;
        long now = level.getGameTime();
        long[] u = USED.computeIfAbsent(level.dimension(), k -> new long[2]);
        if (u[0] != now) { u[0] = now; u[1] = 0; }
        while (!q.isEmpty() && u[1] < Math.max(1, Balance.SHIP_BLAST_MAX_PER_TICK)) {
            Req r = q.pollFirst();
            if (now - r.at() > Balance.SHIP_BLAST_QUEUE_TICKS || !SableBridge.active()) continue;
            u[1] += Math.max(1, run(level, r.pos(), r.radius(), r.power(), r.shooter()));
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        QUEUE.clear();
        USED.clear();
        warnedFull = false;
        current = null;
        if (SableBridge.loaded()) {
            try { SableImpact.clearCaches(); } catch (Throwable ignored) {}
        }
    }

    static boolean anyShips(ServerLevel level) {
        if (level == null || !SableBridge.active()) return false;
        try { return SableImpact.anyShips(level); }
        catch (Throwable t) { SableBridge.fail(t); return false; }
    }

    static boolean spare(ServerLevel level, UUID shipId, UUID shooter) {
        if (Config.Server.shipFriendlyFire() || shooter == null || shipId == null || !SableBridge.active()) return false;
        try { return SableImpact.friendly(level, shipId, shooter); }
        catch (Throwable t) { SableBridge.fail(t); return false; }
    }

    static boolean sameSide(UUID shooter, UUID other) {
        if (shooter == null || other == null) return false;
        if (shooter.equals(other)) return true;
        return CommandCenter.ownersTrusting(shooter).contains(other) || CommandCenter.ownersTrusting(other).contains(shooter);
    }

    static void shipMachines(ServerLevel level, double x, double z, double r, Consumer<BlockEntity> fn) {
        if (level == null || fn == null || !SableBridge.active()) return;
        try { SableImpact.machinesInField(level, x, z, r, fn); }
        catch (Throwable t) { SableBridge.fail(t); }
    }
}
