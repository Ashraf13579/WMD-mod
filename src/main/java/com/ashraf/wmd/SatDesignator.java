package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SatDesignator {

    private SatDesignator() {}

    public static final int LIT = 0, NO_SAT = 1, NO_CONTROL = 2, WAITING = 3, SMOKE = 4, NO_HIT = 5;

    static final class Desig {
        final UUID owner; final int satId; final ResourceKey<Level> dim; final int x, z;
        UUID ship; Vec3 plot; int spotId = -1; int state = WAITING; Vec3 spot, sky;
        Desig(UUID owner, int satId, ResourceKey<Level> dim, int x, int z) { this.owner = owner; this.satId = satId; this.dim = dim; this.x = x; this.z = z; }
    }

    private static final Map<UUID, Desig> DESIG = new HashMap<>();

    static OrbitMath.Sat sat(ServerLevel level, UUID owner, int id) {
        for (OrbitMath.Sat s : OrbitData.get(level.getServer()).sats())
            if (s.id() == id && "designator".equals(s.type()) && owner.equals(s.owner())) return s;
        return null;
    }

    static void set(ServerPlayer p, int satId, int x, int z) {
        ServerLevel level = p.serverLevel();
        if (sat(level, p.getUUID(), satId) == null) { p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.desig.no_sat"), true); return; }
        clear(level, p.getUUID());
        Desig d = new Desig(p.getUUID(), satId, level.dimension(), x, z);
        DESIG.put(p.getUUID(), d);
        step(level, d, System.currentTimeMillis());
        status(level, d);
        broadcast(level);
    }

    static void clear(ServerLevel level, UUID owner) {
        Desig d = DESIG.remove(owner);
        if (d == null) return;
        if (d.spotId >= 0) LaserSpots.remove(d.spotId);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p != null) PacketDistributor.sendToPlayer(p, new DesigStatusPayload(-1, 0, 0, NO_SAT, 0L, 0L, false));
        broadcast(level);
    }

    @SubscribeEvent
    static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % Balance.DESIG_REFRESH != 0) return;
        boolean drones = !droneBeams(level).isEmpty();
        if (DESIG.isEmpty()) {
            if (drones || dronesShown) broadcast(level);
            return;
        }
        long nowMs = System.currentTimeMillis();
        boolean any = drones || dronesShown;
        for (Desig d : new ArrayList<>(DESIG.values())) {
            if (!d.dim.equals(level.dimension())) continue;
            any = true;
            step(level, d, nowMs);
            if (level.getGameTime() % 20 == 0) status(level, d);
        }
        if (any) broadcast(level);
    }

    private static void step(ServerLevel level, Desig d, long nowMs) {
        OrbitMath.Sat s = sat(level, d.owner, d.satId);
        int st;
        Vec3 at = null;
        if (s == null || nowMs >= s.endMs()) st = NO_SAT;
        else if (!OrbitData.controlled(s)) st = NO_CONTROL;
        else if (!OrbitMath.isOverhead(s, d.x + 0.5, d.z + 0.5, nowMs)) st = WAITING;
        else {
            at = aim(level, d);
            st = at == null ? NO_HIT : SmokeScreens.inside(level, at) ? SMOKE : LIT;
        }
        d.state = st;
        if (st != LIT) {
            if (d.spotId >= 0) { LaserSpots.remove(d.spotId); d.spotId = -1; }
            d.spot = null;
            return;
        }
        double[] sky = OrbitMath.sky(s, d.x + 0.5, d.z + 0.5, nowMs);
        d.sky = new Vec3(sky[2], sky[3], sky[4]);
        d.spot = at;
        Vec3 origin = at.add(d.sky.scale(200.0));
        if (d.spotId < 0 || !LaserSpots.refresh(level, d.spotId, at, origin)) d.spotId = LaserSpots.light(level, d.owner, at, LaserSpots.SRC_SATELLITE, origin);
    }

    private static Vec3 aim(ServerLevel level, Desig d) {
        if (d.ship != null && d.plot != null && SableBridge.shipInfo(level, d.ship) != null) {
            Vec3 w = SableBridge.toWorld(level, d.plot);
            if (w != null && w != d.plot) return w;
        }
        d.ship = null;
        d.plot = null;
        if (!level.hasChunk(d.x >> 4, d.z >> 4)) return d.spot;
        double top = level.getMaxBuildHeight() + 32.0;
        BlockHitResult bh = BeamTrace.hit(level, new Vec3(d.x + 0.5, top, d.z + 0.5), new Vec3(0.0, -1.0, 0.0), top - level.getMinBuildHeight());
        if (bh == null) return null;
        UUID ship = SableBridge.shipIdAt(level, bh.getBlockPos());
        if (ship != null) {
            d.ship = ship;
            d.plot = bh.getLocation();
            return SableBridge.toWorld(level, bh.getLocation());
        }
        return bh.getLocation();
    }

    private static void status(ServerLevel level, Desig d) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.owner);
        if (p == null) return;
        OrbitMath.Sat s = sat(level, d.owner, d.satId);
        long now = System.currentTimeMillis();
        long left = s == null ? 0L : OrbitMath.overheadLeft(s, d.x + 0.5, d.z + 0.5, now), next = s == null ? 0L : OrbitMath.nextPass(s, d.x + 0.5, d.z + 0.5, now);
        PacketDistributor.sendToPlayer(p, new DesigStatusPayload(d.satId, d.x, d.z, d.state, left, next, d.ship != null));
    }

    private static boolean dronesShown;

    private static List<SatBeamPayload.Beam> droneBeams(ServerLevel level) {
        List<SatBeamPayload.Beam> l = new ArrayList<>();
        if (level != level.getServer().overworld()) return l;
        for (Drones.Drone d : Drones.get(level.getServer()).drones()) {
            if (d.kind() != 4 || d.spotId < 0) continue;
            LaserSpots.Spot s = LaserSpots.get(level, d.spotId);
            if (s == null) continue;
            net.minecraft.world.phys.Vec3 up = d.pos().subtract(s.pos());
            double len = up.length();
            if (len < 1.0) continue;
            up = up.scale(1.0 / len);
            l.add(new SatBeamPayload.Beam((float) s.pos().x, (float) s.pos().y, (float) s.pos().z, (float) up.x, (float) up.y, (float) up.z, (float) len, d.id()));
        }
        return l;
    }

    private static void broadcast(ServerLevel level) {
        List<SatBeamPayload.Beam> l = new ArrayList<>();
        for (Desig d : DESIG.values())
            if (d.dim.equals(level.dimension()) && d.state == LIT && d.spot != null && d.sky != null && l.size() < SatBeamPayload.MAX)
                l.add(new SatBeamPayload.Beam((float) d.spot.x, (float) d.spot.y, (float) d.spot.z, (float) d.sky.x, (float) d.sky.y, (float) d.sky.z));
        List<SatBeamPayload.Beam> dl = droneBeams(level);
        for (int i = 0; i < dl.size() && l.size() < SatBeamPayload.MAX; i++) l.add(dl.get(i));
        dronesShown = !dl.isEmpty();
        SatBeamPayload pl = new SatBeamPayload(List.copyOf(l));
        for (ServerPlayer sp : level.players()) PacketDistributor.sendToPlayer(sp, pl);
    }

    static int[] target(UUID owner) {
        Desig d = DESIG.get(owner);
        return d == null ? null : new int[] { d.x, d.z };
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { DESIG.clear(); }
}
