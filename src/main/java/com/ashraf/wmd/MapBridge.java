package com.ashraf.wmd;

import net.minecraft.server.MinecraftServer;

public final class MapBridge {

    private MapBridge() {}

    private static Boolean loaded;
    private static volatile boolean failed, registered;

    public static boolean loaded() {
        if (loaded == null) {
            try { Class.forName("de.bluecolored.bluemap.api.BlueMapAPI", false, MapBridge.class.getClassLoader()); loaded = true; }
            catch (Throwable t) { loaded = false; }
        }
        return loaded;
    }

    public static boolean tracking() { return loaded() && !failed && Config.Server.blueMap(); }

    static void fail(Throwable t) {
        if (failed) return;
        failed = true;
        Wmd.LOGGER.error("BlueMap bridge switched off for this session after an error (WMD carries on as if BlueMap were not installed)", t);
    }

    static void register() {
        if (!tracking() || registered) return;
        try { BlueMapMarkers.register(); registered = true; } catch (Throwable t) { fail(t); }
    }

    static boolean connected() {
        if (!tracking() || !registered) return false;
        try { return BlueMapMarkers.connected(); } catch (Throwable t) { fail(t); return false; }
    }

    static void sync(MinecraftServer server, MapFeed.Snapshot snap) {
        if (!tracking() || !registered) return;
        try { BlueMapMarkers.sync(server, snap); } catch (Throwable t) { fail(t); }
    }

    static String status() {
        if (!tracking() || !registered) return "off";
        try { return BlueMapMarkers.status(); } catch (Throwable t) { fail(t); return "error"; }
    }

    static void clearAll() {
        if (!tracking() || !registered) return;
        try { BlueMapMarkers.clearAll(); } catch (Throwable t) { fail(t); }
    }
}
