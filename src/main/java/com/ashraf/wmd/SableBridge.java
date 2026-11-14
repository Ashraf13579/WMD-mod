package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class SableBridge {

    private SableBridge() {}

    public record ShipInfo(UUID id, String name, Vec3 worldCenter, Vec3 linearVelocity, AABB worldBounds, double mass, int blockCount) {
        public double speed() { return linearVelocity.length(); }
        public Vec3 size() { return new Vec3(worldBounds.getXsize(), worldBounds.getYsize(), worldBounds.getZsize()); }

        public boolean isLarge() { return blockCount >= Config.Server.largeShipMinBlocks(); }

        public double maxSize() { return Math.max(worldBounds.getXsize(), Math.max(worldBounds.getYsize(), worldBounds.getZsize())); }
    }

    private static Boolean loaded;
    private static volatile boolean failed;

    public static boolean loaded() {
        if (loaded == null) {
            try { loaded = ModList.get() != null && ModList.get().isLoaded("sable"); }
            catch (Throwable t) { loaded = false; }
        }
        return loaded;
    }

    public static boolean active() {
        return loaded() && !failed && Config.Server.sableBridge();
    }

    static void fail(Throwable t) {
        if (failed) return;
        failed = true;
        Wmd.LOGGER.error("Sable bridge switched off for this session after an error (WMD carries on as if Sable were not installed)", t);
    }

    public static ScrewPropellerBlockEntity newScrew(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.screw(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new ScrewPropellerBlockEntity(pos, state);
    }

    public static WingSectionBlock newWingSection(net.minecraft.world.level.block.state.BlockBehaviour.Properties p) {
        if (loaded() && !failed) {
            try { return SableWings.section(p); } catch (Throwable t) { fail(t); }
        }
        return new WingSectionBlock(p);
    }

    public static ControlSurfaceBlock newControlSurface(net.minecraft.world.level.block.state.BlockBehaviour.Properties p) {
        if (loaded() && !failed) {
            try { return SableWings.control(p); } catch (Throwable t) { fail(t); }
        }
        return new ControlSurfaceBlock(p);
    }

    public static PumpJetBlockEntity newPumpJet(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.pumpJet(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new PumpJetBlockEntity(pos, state);
    }

    public static DuctedFanBlockEntity newDuctedFan(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.ductedFan(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new DuctedFanBlockEntity(pos, state);
    }

    public static LiftFanBlockEntity newLiftFan(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.liftFan(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new LiftFanBlockEntity(pos, state);
    }

    public static HeliRotorBlockEntity newHeliRotor(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.heliRotor(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new HeliRotorBlockEntity(pos, state);
    }

    public static HeliTailBlockEntity newHeliTail(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.heliTail(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new HeliTailBlockEntity(pos, state);
    }

    public static HeliTurboshaftBlockEntity newHeliTurboshaft(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { return new HeliTurboshaftBlockEntity(pos, state); }

    public static JetEngineBlockEntity newJetEngine(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.jet(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new JetEngineBlockEntity(pos, state);
    }

    public static TurbopropBlockEntity newTurboprop(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.turboprop(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new TurbopropBlockEntity(pos, state);
    }

    public static TiltrotorBlockEntity newTiltrotor(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.tiltrotor(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new TiltrotorBlockEntity(pos, state);
    }

    public static WaterjetBlockEntity newWaterjet(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.waterjet(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new WaterjetBlockEntity(pos, state);
    }

    public static AzimuthPodBlockEntity newAzimuthPod(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.azimuthPod(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new AzimuthPodBlockEntity(pos, state);
    }

    public static CargoHookBlockEntity newCargoHook(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.cargoHook(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new CargoHookBlockEntity(pos, state);
    }

    public static BowThrusterBlockEntity newBowThruster(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.bowThruster(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new BowThrusterBlockEntity(pos, state);
    }

    public static TurbofanBlockEntity newTurbofan(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.turbofan(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new TurbofanBlockEntity(pos, state);
    }

    public static LandingGearBlockEntity newLandingGear(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableGear.gear(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new LandingGearBlockEntity(pos, state);
    }

    public static Object liftPlatform(ServerLevel level, DeckLiftBlockEntity lift, int sx, int sz) {
        if (level == null || lift == null || liftFailed || !active()) return null;
        try { return SableLift.add(level, lift, sx, sz); } catch (Throwable t) { liftFail(t); return null; }
    }

    public static void liftPlatformRemove(ServerLevel level, Object handle) {
        if (level == null || handle == null) return;
        try { SableLift.remove(level, handle); } catch (Throwable t) { liftFail(t); }
    }

    private static volatile boolean liftFailed;

    private static void liftFail(Throwable t) {
        if (liftFailed) return;
        liftFailed = true;
        Wmd.LOGGER.error("WMD deck lift: the Sable platform failed, lifts carry craft on their landing gear only for this session", t);
    }

    public static Object turntableDisc(ServerLevel level, TurntableBlockEntity tt, int n) {
        if (level == null || tt == null || turnFailed || !active()) return null;
        try { return SableTurntable.add(level, tt, n); } catch (Throwable t) { turnFail(t); return null; }
    }

    public static void turntableDiscRemove(ServerLevel level, Object handle) {
        if (level == null || handle == null) return;
        try { SableTurntable.remove(level, handle); } catch (Throwable t) { turnFail(t); }
    }

    private static volatile boolean turnFailed;

    private static void turnFail(Throwable t) {
        if (turnFailed) return;
        turnFailed = true;
        Wmd.LOGGER.error("WMD turntable: the Sable disc failed, turntables turn craft on their landing gear only for this session", t);
    }

    public static boolean wake(ServerLevel level, UUID id) {
        if (level == null || id == null || !active()) return false;
        try { return SableDebris.wake(level, id); } catch (Throwable t) { fail(t); return false; }
    }

    public static JatoRackBlockEntity newJato(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (loaded() && !failed) {
            try { return SableEngines.jato(pos, state); } catch (Throwable t) { fail(t); }
        }
        return new JatoRackBlockEntity(pos, state);
    }

    public static List<ShipInfo> allShips(ServerLevel level) {
        if (level == null || !active()) return List.of();
        try { return SableShips.all(level); } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static List<ShipInfo> shipsIntersecting(ServerLevel level, AABB box) {
        if (level == null || box == null || !active()) return List.of();
        try { return SableShips.intersecting(level, box); } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static List<ShipInfo> shipsAlongSegment(ServerLevel level, Vec3 from, Vec3 to) {
        if (level == null || from == null || to == null || !active()) return List.of();
        try {
            List<ShipInfo> near = SableShips.intersecting(level, new AABB(from, to));
            if (near.isEmpty()) return near;
            java.util.ArrayList<ShipInfo> out = new java.util.ArrayList<>(near.size());
            for (ShipInfo s : near)
                if (s.worldBounds().contains(from) || s.worldBounds().contains(to) || s.worldBounds().clip(from, to).isPresent()) out.add(s);
            return out;
        } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static ProjectilePath.PathHit exactHit(ServerLevel level, Vec3 from, Vec3 to, List<ShipInfo> ships) {
        if (level == null || from == null || to == null || ships == null || ships.isEmpty() || !active()) return null;
        try { return SableImpact.exact(level, from, to, ships); } catch (Throwable t) { fail(t); return null; }
    }

    public static ShipInfo shipInfo(ServerLevel level, UUID id) {
        if (level == null || id == null || !active()) return null;
        try { return SableShips.info(level, id); } catch (Throwable t) { fail(t); return null; }
    }

    public static ShipInfo shipInfoFresh(ServerLevel level, UUID id) {
        if (level == null || id == null || !active()) return null;
        try { SableShips.forgetCount(id); return SableShips.info(level, id); } catch (Throwable t) { fail(t); return null; }
    }

    public static int shipFracture(ServerLevel level, UUID id, Vec3 at) {
        if (level == null || id == null || !active()) return 0;
        try { return SableShips.fracture(level, id, at); } catch (Throwable t) { fail(t); return 0; }
    }

    public static ShipInfo shipAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !active()) return null;
        try { return SableShips.infoAt(level, pos); } catch (Throwable t) { fail(t); return null; }
    }

    public static UUID shipUnder(net.minecraft.world.entity.Entity e) {
        if (e == null || !active()) return null;
        try { return SableShips.shipUnder(e); } catch (Throwable t) { fail(t); return null; }
    }

    public static List<ShipInfo> largeShipsIntersecting(ServerLevel level, AABB box) {
        List<ShipInfo> all = shipsIntersecting(level, box);
        if (all.isEmpty()) return all;
        java.util.ArrayList<ShipInfo> out = new java.util.ArrayList<>(all.size());
        for (ShipInfo s : all) if (s.isLarge() && NavalLayer.visibleAboveWater(level, s)) out.add(s);
        return out;
    }

    public static List<ShipInfo> largeShipsNear(ServerLevel level, double x, double z, double range) {
        if (level == null || !active()) return List.of();
        AABB box = new AABB(x - range, level.getMinBuildHeight() - 512.0, z - range, x + range, level.getMaxBuildHeight() + 2048.0, z + range);
        List<ShipInfo> all = largeShipsIntersecting(level, box);
        if (all.isEmpty()) return all;
        java.util.ArrayList<ShipInfo> out = new java.util.ArrayList<>(all.size());
        double r2 = range * range;
        for (ShipInfo s : all) {
            double dx = s.worldCenter().x - x, dz = s.worldCenter().z - z;
            if (dx * dx + dz * dz <= r2) out.add(s);
        }
        return out;
    }

    public static List<BlockPos> blockEntitiesOn(ServerLevel level, UUID shipId) {
        if (level == null || shipId == null || !active()) return List.of();
        try { return SableShips.blockEntitiesOn(level, shipId); } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static int[] plotBox(ServerLevel level, UUID shipId) {
        if (level == null || shipId == null || !active()) return null;
        try { return SableShips.plotBox(level, shipId); } catch (Throwable t) { fail(t); return null; }
    }

    public static java.util.Map<Long, net.minecraft.world.level.chunk.LevelChunk> plotChunks(ServerLevel level, UUID shipId) {
        if (level == null || shipId == null || !active()) return java.util.Map.of();
        try { return SableShips.plotChunks(level, shipId); } catch (Throwable t) { fail(t); return java.util.Map.of(); }
    }

    public static int countBlocks(ServerLevel level, UUID shipId, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> match) {
        if (level == null || shipId == null || !active()) return 0;
        try { return SableShips.countBlocks(level, shipId, match); } catch (Throwable t) { fail(t); return 0; }
    }

    public static double[] physicsAt(Level level, Vec3 p) {
        if (!active() || level == null || p == null) return null;
        try { return SableShips.physicsAt(level, p); } catch (Throwable t) { return null; }
    }

    public static boolean applyAir(ServerLevel level, java.util.List<double[]> points, int priority) {
        if (!active() || level == null || points == null || points.isEmpty()) return false;
        try { return SableShips.applyAir(level, points, priority); } catch (Throwable t) { Wmd.LOGGER.warn("WMD air: Sable refused the air curve", t); return false; }
    }

    public static int countLiftBlocks(ServerLevel level, UUID shipId) {
        if (level == null || shipId == null || !active()) return 0;
        try { return SableShips.countBlocks(level, shipId, SableShips::liftProvider); } catch (Throwable t) { fail(t); return 0; }
    }

    public static boolean isOnShip(Level level, BlockPos pos) {
        if (level == null || pos == null || !active()) return false;
        try { return SableShips.onShip(level, pos); } catch (Throwable t) { fail(t); return false; }
    }

    public static UUID shipIdAt(Level level, BlockPos pos) {
        if (level == null || pos == null || !active()) return null;
        try { return SableShips.idAt(level, pos); } catch (Throwable t) { fail(t); return null; }
    }

    public static Vec3 worldPos(Level level, BlockPos pos) {
        Vec3 c = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (level == null || !active()) return c;
        try { return SableShips.toWorld(level, c); } catch (Throwable t) { fail(t); return c; }
    }

    public static Vec3 worldPos(Level level, double x, double y, double z) {
        Vec3 c = new Vec3(x, y, z);
        if (level == null || !active()) return c;
        try { return SableShips.toWorld(level, c); } catch (Throwable t) { fail(t); return c; }
    }

    public static Vec3 toWorld(Level level, Vec3 plotPos) {
        if (level == null || plotPos == null || !active()) return plotPos;
        try { return SableShips.toWorld(level, plotPos); } catch (Throwable t) { fail(t); return plotPos; }
    }

    public static Vec3 toShip(Level level, UUID shipId, Vec3 worldPos) {
        if (level == null || shipId == null || worldPos == null || !active()) return null;
        try { return SableShips.toShip(level, shipId, worldPos); } catch (Throwable t) { fail(t); return null; }
    }

    public static Vec3 toShip(Level level, ShipInfo ship, Vec3 worldPos) { return ship == null ? null : toShip(level, ship.id(), worldPos); }

    public static double distSqWithShips(Level level, Vec3 a, Vec3 b) {
        if (level == null || !active()) return a.distanceToSqr(b);
        try { return SableShips.toWorld(level, a).distanceToSqr(SableShips.toWorld(level, b)); }
        catch (Throwable t) { fail(t); return a.distanceToSqr(b); }
    }

    public static double distSqWithShips(Level level, BlockPos a, BlockPos b) {
        return worldPos(level, a).distanceToSqr(worldPos(level, b));
    }

    public record DebrisScan(UUID id, long until, UUID splitFrom, AABB bounds) {}

    private static volatile boolean debrisFailed;

    public static boolean debrisActive() { return active() && !debrisFailed; }

    private static void debrisFail(Throwable t) {
        if (debrisFailed) return;
        debrisFailed = true;
        Wmd.LOGGER.error("Sable explosion debris switched off for this session after an error (blasts fall back to plain falling blocks)", t);
    }

    public static UUID debrisSpawn(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, long until) {
        if (level == null || blocks == null || blocks.isEmpty() || !debrisActive()) return null;
        try { return SableDebris.spawn(level, anchor, blocks, until); } catch (Throwable t) { debrisFail(t); return null; }
    }

    public static UUID assembleShip(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, String name) {
        if (level == null || blocks == null || blocks.isEmpty() || !active()) return null;
        try { return SableDebris.assembleShip(level, anchor, blocks, name); } catch (Throwable t) { fail(t); return null; }
    }

    public static boolean debrisPush(ServerLevel level, UUID id, Vec3 vel, Vec3 offset) {
        if (level == null || id == null || !debrisActive()) return false;
        try { return SableDebris.push(level, id, vel, offset); } catch (Throwable t) { debrisFail(t); return false; }
    }

    public static boolean shipPush(ServerLevel level, UUID id, Vec3 dv, Vec3 offset) {
        if (level == null || id == null || dv == null || !active()) return false;
        try { boolean ok = SableDebris.push(level, id, dv, offset == null ? Vec3.ZERO : offset); if (ok) SableDebris.wake(level, id); return ok; } catch (Throwable t) { fail(t); return false; }
    }

    public static boolean debrisWake(ServerLevel level, UUID id) {
        if (level == null || id == null || !debrisActive()) return false;
        try { return SableDebris.wake(level, id); } catch (Throwable t) { debrisFail(t); return false; }
    }

    public static AABB debrisBounds(ServerLevel level, UUID id) {
        if (level == null || id == null || !debrisActive()) return null;
        try { return SableDebris.bounds(level, id); } catch (Throwable t) { debrisFail(t); return null; }
    }

    public static boolean debrisAdopt(ServerLevel level, UUID id, long until) {
        if (level == null || id == null || !debrisActive()) return false;
        try { return SableDebris.adopt(level, id, until); } catch (Throwable t) { debrisFail(t); return false; }
    }

    public static int debrisCrack(ServerLevel level, UUID id, int spacing) {
        if (level == null || id == null || !debrisActive()) return 0;
        try { return SableDebris.crack(level, id, spacing); } catch (Throwable t) { debrisFail(t); return 0; }
    }

    public static boolean debrisRemove(ServerLevel level, UUID id) {
        if (level == null || id == null || !debrisActive()) return false;
        try { return SableDebris.remove(level, id); } catch (Throwable t) { debrisFail(t); return false; }
    }

    public static boolean debrisRemnant(ServerLevel level, UUID id) {
        if (level == null || id == null || !debrisActive()) return false;
        try { return SableDebris.naturalRemnant(level, id, Balance.DEBRIS_TREE_MAX); } catch (Throwable t) { debrisFail(t); return false; }
    }

    public static List<DebrisScan> debrisScan(ServerLevel level) {
        if (level == null || !debrisActive()) return List.of();
        try { return SableDebris.scan(level); } catch (Throwable t) { debrisFail(t); return List.of(); }
    }
}
