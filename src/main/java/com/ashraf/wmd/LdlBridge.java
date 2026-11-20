package com.ashraf.wmd;

import net.neoforged.fml.ModList;

final class LdlBridge {

    private LdlBridge() {}

    private static Boolean loaded;
    private static boolean failed;

    private static java.lang.reflect.Method sync;

    private static void call(boolean want) throws ReflectiveOperationException {
        if (sync == null) sync = Class.forName("com.ashraf.wmd.LdlLights").getDeclaredMethod("sync", boolean.class);
        sync.invoke(null, want);
    }

    static void sync() {
        if (loaded == null) loaded = ModList.get().isLoaded("lambdynlights");
        if (!loaded || failed) return;
        boolean want;
        try { want = ClientConfig.LAMBDYNLIGHTS.get() && !WmdLights.drawnNow(); } catch (RuntimeException e) { want = false; }
        try {
            call(want);
        } catch (Throwable t) {
            failed = true;
            if (t instanceof ClassNotFoundException) return;
            Wmd.LOGGER.error("[wmd] LambDynamicLights bridge disabled for this session", t);
            try { call(false); } catch (Throwable ignored) { }
        }
    }
}
