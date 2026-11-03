package com.ashraf.wmd;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class DhBridge {

    private DhBridge() {}

    record CloudRequest(double x, double y, double z, int tier, WarheadType type, long start) {}
    static final java.util.List<CloudRequest> PENDING = new java.util.concurrent.CopyOnWriteArrayList<>();

    static volatile boolean ready;
    private static boolean failed;
    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("distanthorizons");
        return loaded;
    }

    public static boolean active() { return ready && !failed && loaded(); }

    public static void impact(double x, double y, double z, int tier, WarheadType type) {
        if (failed || !loaded()) return;
        if (type == WarheadType.DECOY || type == WarheadType.CLUSTER || type == WarheadType.INTERCEPTOR) return;
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) PENDING.add(new CloudRequest(x, y, z, tier, type, level.getGameTime()));
    }

    static void tick(ClientLevel level, Vec3 cam) {
        if (failed || !loaded()) return;
        try {
            DhFar.tick(level, cam);
        } catch (Throwable t) {
            fail(t);
        }
    }

    static void hideFar(boolean hide) {
        if (!loaded() || failed && hide) return;
        try {
            DhFar.hideFar(hide);
        } catch (Throwable t) {
            fail(t);
        }
    }

    static void clearStale() {
        if (failed || !loaded()) return;
        try {
            DhFar.clearStale();
        } catch (Throwable t) {
            fail(t);
        }
    }

    static boolean hiddenByUs() {
        if (!loaded()) return false;
        try { return DhFar.hidden; } catch (Throwable t) { return false; }
    }

    static void clear() {
        PENDING.clear();
        if (!loaded()) return;
        try {
            DhFar.reset();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void fail(Throwable t) {
        failed = true;
        ready = false;
        Wmd.LOGGER.warn("Distant Horizons far view switched off after an error", t);
    }
}
