package com.ashraf.wmd;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class DeepSeasBridge {

    private DeepSeasBridge() {}

    private static final String P = "com.maxenonyme.createsubmarine.submarine.";

    public record SubState(UUID id, int depth, boolean sealed, boolean breached, int cracks, boolean imploding) {}

    private static Boolean loaded;
    private static volatile boolean failed;
    private static Method mGetAll, mFindUUID, mGetBounds, mPressurized, mDepth, mBreached, mCracks, mBuilding, mBuildUp, mRupture, mCrashing, mOnCrashed;

    public static boolean loaded() {
        if (loaded == null) {
            try { loaded = ModList.get() != null && ModList.get().isLoaded("create_submarine"); }
            catch (Throwable t) { loaded = false; }
        }
        return loaded;
    }

    public static boolean active() {
        return loaded() && !failed && SableBridge.active() && Config.Server.deepSeasBridge() && bind();
    }

    static void fail(Throwable t) {
        if (failed) return;
        failed = true;
        Wmd.LOGGER.error("Deep Seas bridge switched off for this session after an error (WMD treats submarines as plain Sable ships)", t);
    }

    private static boolean bound;

    private static synchronized boolean bind() {
        if (bound) return true;
        if (failed) return false;
        try {
            Class<?> reg = Class.forName(P + "util.SubLevelRegistry"), press = Class.forName(P + "system.SubmarinePressureSystem");
            Class<?> imp = Class.forName(P + "system.ImplosionSequence"), sink = Class.forName(P + "system.SubmarineSinkingSystem");
            Class<?> bounds = Class.forName(P + "util.SubLevelRegistry$PlotBounds");
            mGetAll = reg.getMethod("getAll");
            mFindUUID = reg.getMethod("findUUID", Level.class, BlockPos.class);
            mGetBounds = reg.getMethod("getBounds", UUID.class);
            mPressurized = press.getMethod("isPressurized", UUID.class);
            mDepth = press.getMethod("getCachedDepth", UUID.class);
            mBreached = press.getMethod("isBreached", UUID.class);
            mCracks = press.getMethod("getCrackCount", UUID.class);
            mBuilding = imp.getMethod("isBuilding", UUID.class);
            mBuildUp = imp.getMethod("buildUp", ServerLevel.class, UUID.class, Vec3.class, Runnable.class);
            mRupture = imp.getMethod("rupture", ServerLevel.class, Vec3.class);
            mCrashing = sink.getMethod("isCrashing", UUID.class);
            Method crashed = null;
            for (Method m : sink.getMethods()) if (m.getName().equals("onCrashed") && m.getParameterCount() == 4 && m.getParameterTypes()[3] == bounds) crashed = m;
            if (crashed == null) throw new NoSuchMethodException("SubmarineSinkingSystem.onCrashed(UUID, SubLevelAccess, Level, PlotBounds)");
            mOnCrashed = crashed;
            bound = true;
            Wmd.LOGGER.info("Deep Seas bridge ready (create_submarine found)");
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public static boolean isSub(UUID shipId) {
        if (shipId == null || !active()) return false;
        try { return ((Map<?, ?>) mGetAll.invoke(null)).containsKey(shipId); } catch (Throwable t) { fail(t); return false; }
    }

    public static UUID subAt(Level level, BlockPos pos) {
        if (level == null || pos == null || !active()) return null;
        try { return (UUID) mFindUUID.invoke(null, level, pos); } catch (Throwable t) { fail(t); return null; }
    }

    public static SubState state(UUID id) {
        if (!isSub(id)) return null;
        try {
            return new SubState(id, (Integer) mDepth.invoke(null, id), (Boolean) mPressurized.invoke(null, id), (Boolean) mBreached.invoke(null, id),
                    (Integer) mCracks.invoke(null, id), (Boolean) mBuilding.invoke(null, id) || (Boolean) mCrashing.invoke(null, id));
        } catch (Throwable t) { fail(t); return null; }
    }

    public static int setBallast(ServerLevel level, UUID shipId, double fraction) {
        if (level == null || shipId == null || !active()) return -1;
        int n = 0;
        try {
            Class<?> fa = Class.forName("net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction");
            Object exec = fa.getField("EXECUTE").get(null);
            for (BlockPos p : SableBridge.blockEntitiesOn(level, shipId)) {
                Object be = level.getBlockEntity(p);
                if (be == null || !be.getClass().getName().endsWith("BallastTankBlockEntity")) continue;
                Class<?> c = be.getClass();
                int cap = (Integer) c.getMethod("getClusterTotalCapacity").invoke(be), amt = (Integer) c.getMethod("getClusterTotalAmount").invoke(be);
                int want = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * cap);
                if (want > amt) c.getMethod("fillCluster", int.class, fa).invoke(be, want - amt, exec);
                else if (want < amt) c.getMethod("drainCluster", int.class, fa).invoke(be, amt - want, exec);
                n++;
            }
            return n;
        } catch (Throwable t) { fail(t); return -1; }
    }

    private static boolean hullBound, hullFailed;
    private static Method mHullValues, mHullUpdate, mWeakest, mPropDepth, mWpState;

    private static synchronized boolean bindHull() {
        if (hullBound) return true;
        if (hullFailed || !active()) return false;
        try {
            Class<?> cfg = Class.forName(P + "config.HullStrengthConfig"), prop = Class.forName(P + "config.HullStrengthConfig$HullProperty");
            Class<?> press = Class.forName(P + "system.SubmarinePressureSystem"), wp = Class.forName(P + "system.SubmarinePressureSystem$WeakPoint");
            mHullValues = cfg.getMethod("getValues");
            mHullUpdate = cfg.getMethod("update", String.class, int.class, float.class);
            mPropDepth = prop.getMethod("maxWaterDepth");
            mWeakest = press.getMethod("weakestHull", java.util.UUID.class, Level.class);
            mWpState = wp.getMethod("state");
            hullBound = true;
            return true;
        } catch (Throwable t) {
            hullFailed = true;
            Wmd.LOGGER.error("Deep Seas hull ratings unavailable (deep-rated hulls fall back to Deep Seas' own numbers)", t);
            return false;
        }
    }

    public static int hullRating(String blockId) {
        if (!bindHull()) return -1;
        try {
            Object p = ((Map<?, ?>) mHullValues.invoke(null)).get(blockId);
            return p == null ? -1 : (Integer) mPropDepth.invoke(p);
        } catch (Throwable t) { hullFailed = true; Wmd.LOGGER.error("Deep Seas hull ratings switched off", t); return -1; }
    }

    public static boolean setHullRating(String blockId, int depth, float crackChance) {
        if (!bindHull()) return false;
        try { mHullUpdate.invoke(null, blockId, depth, crackChance); return true; }
        catch (Throwable t) { hullFailed = true; Wmd.LOGGER.error("Deep Seas hull ratings switched off", t); return false; }
    }

    public static String weakestHullBlock(Level level, UUID id) {
        if (level == null || !isSub(id) || !bindHull()) return null;
        try {
            Object wp = mWeakest.invoke(null, id, level);
            if (wp == null) return null;
            Object st = mWpState.invoke(wp);
            return st instanceof net.minecraft.world.level.block.state.BlockState s
                    ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString() : null;
        } catch (Throwable t) { hullFailed = true; Wmd.LOGGER.error("Deep Seas hull ratings switched off", t); return null; }
    }

    private static boolean floodBound, floodFailed;
    private static Method mWaterCells, mCompartments, mInternal, mAllCracks, mWeakDepth;
    private static java.lang.reflect.Field fBreaches;

    private static synchronized boolean bindFlood() {
        if (floodBound) return true;
        if (floodFailed || !active()) return false;
        try {
            Class<?> flood = Class.forName(P + "compartment.FloodSystem"), tracker = Class.forName(P + "compartment.CompartmentTracker");
            Class<?> comp = Class.forName(P + "compartment.CompartmentDetector$Component"), press = Class.forName(P + "system.SubmarinePressureSystem");
            mWaterCells = flood.getMethod("waterCells", UUID.class);
            mCompartments = tracker.getMethod("getCompartments", UUID.class);
            mInternal = comp.getMethod("internal");
            mAllCracks = press.getMethod("getAllCracks");
            mWeakDepth = press.getMethod("getWeakestHullDepth", UUID.class, Level.class);
            try {
                java.lang.reflect.Field f = Class.forName(P + "compartment.BreachLedger").getDeclaredField("BREACHES");
                f.setAccessible(true);
                fBreaches = f;
            } catch (Throwable t) { fBreaches = null; Wmd.LOGGER.info("Deep Seas breach ledger not readable (the hull page lists cracks only): {}", t.toString()); }
            floodBound = true;
            return true;
        } catch (Throwable t) {
            floodFailed = true;
            Wmd.LOGGER.error("Deep Seas flooding data unavailable (the sonar console's HULL page shows no flooding)", t);
            return false;
        }
    }

    private static void floodFail(Throwable t) {
        if (floodFailed) return;
        floodFailed = true;
        Wmd.LOGGER.error("Deep Seas flooding data switched off for this session", t);
    }

    public static java.util.Set<BlockPos> waterCells(UUID id) {
        if (!isSub(id) || !bindFlood()) return java.util.Set.of();
        try {
            Object s = mWaterCells.invoke(null, id);
            return s instanceof java.util.Collection<?> c ? copyPositions(c) : java.util.Set.of();
        } catch (Throwable t) { floodFail(t); return java.util.Set.of(); }
    }

    public static java.util.Set<BlockPos> interiorCells(UUID id) {
        if (!isSub(id) || !bindFlood()) return java.util.Set.of();
        try {
            java.util.Set<BlockPos> out = new java.util.HashSet<>();
            Object l = mCompartments.invoke(null, id);
            if (l instanceof java.util.Collection<?> comps)
                for (Object c : comps) if (c != null && mInternal.invoke(c) instanceof java.util.Collection<?> in) out.addAll(copyPositions(in));
            return out;
        } catch (Throwable t) { floodFail(t); return java.util.Set.of(); }
    }

    public static Map<BlockPos, Boolean> leaks(UUID id) {
        if (!isSub(id) || !bindFlood()) return Map.of();
        Map<BlockPos, Boolean> out = new java.util.HashMap<>();
        try {
            if (mAllCracks.invoke(null) instanceof Map<?, ?> all && all.get(id) instanceof Map<?, ?> cr)
                for (Object p : cr.keySet()) if (p instanceof BlockPos b) out.put(b.immutable(), false);
            if (fBreaches != null && fBreaches.get(null) instanceof Map<?, ?> br && br.get(id) instanceof java.util.Collection<?> c)
                for (BlockPos b : copyPositions(c)) out.put(b, true);
        } catch (Throwable t) { floodFail(t); }
        return out;
    }

    public static int crushDepth(Level level, UUID id) {
        if (level == null || !isSub(id)) return -1;
        if (bindFlood()) {
            try {
                int d = (Integer) mWeakDepth.invoke(null, id, level);
                if (d > 0 && d < 100000) return d;
            } catch (Throwable t) { floodFail(t); }
        }
        String w = weakestHullBlock(level, id);
        return w == null ? -1 : hullRating(w);
    }

    private static java.util.Set<BlockPos> copyPositions(java.util.Collection<?> c) {
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (Object o : c.toArray()) if (o instanceof BlockPos b) out.add(b.immutable());
        return out;
    }

    public static boolean implode(ServerLevel level, UUID id, Vec3 hit) {
        if (level == null || hit == null || !isSub(id)) return false;
        try {
            if ((Boolean) mBuilding.invoke(null, id) || (Boolean) mCrashing.invoke(null, id)) return false;
            Runnable finish = () -> {
                try {
                    mRupture.invoke(null, level, hit);
                    SeaSound.nf(level, WmdSounds.SUB_IMPLODE.get(), WmdSounds.SUB_IMPLODE_FAR.get(), 96.0, hit, 10.0F, 1.0F);
                    Object access = ((Map<?, ?>) mGetAll.invoke(null)).get(id);
                    Object bounds = mGetBounds.invoke(null, id);
                    if (access != null && bounds != null) mOnCrashed.invoke(null, id, access, level, bounds);
                } catch (Throwable t) { fail(t); }
            };
            mBuildUp.invoke(null, level, id, hit, finish);
            Wmd.LOGGER.info("WMD hit imploded submarine {} at {}", id, hit);
            return true;
        } catch (Throwable t) { fail(t); return false; }
    }
}
