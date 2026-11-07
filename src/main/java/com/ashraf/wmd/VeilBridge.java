package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

public final class VeilBridge {

    private VeilBridge() {}

    private static Boolean loaded;
    private static boolean failed;

    public static boolean active() {
        if (loaded == null) loaded = ModList.get().isLoaded("veil");
        return loaded && !failed;
    }

    private static boolean shaderPack;
    private static long shaderCheckedAt;
    private static boolean shaderChecked, shaderLogged, irisBroken;
    private static boolean irisLooked;
    private static Object irisApi;
    private static java.lang.reflect.Method irisInUse;

    static boolean shaderPackOn() {
        long t = now();

        if (shaderChecked && t >= shaderCheckedAt && t - shaderCheckedAt < 20) return shaderPack;
        shaderChecked = true;
        shaderCheckedAt = t;
        try {
            if (!irisLooked) {
                irisLooked = true;
                if (ModList.get().isLoaded("iris")) {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = c.getMethod("getInstance").invoke(null);
                    irisInUse = c.getMethod("isShaderPackInUse");
                }
            }
            shaderPack = irisInUse != null ? Boolean.TRUE.equals(irisInUse.invoke(irisApi)) : irisBroken && ModList.get().isLoaded("irisveil");
        } catch (Throwable e) {
            irisInUse = null;
            irisBroken = true;
            shaderPack = ModList.get().isLoaded("irisveil");
        }
        if (shaderPack && !shaderLogged) { shaderLogged = true; Wmd.LOGGER.info("[wmd] Iris shaderpack in use: WMD's Veil point lights are off (irisveil cannot build veil:light/point)"); }
        return shaderPack;
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    public static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, long gameTime) {
        glow(key, x, y, z, r, g, b, radius, brightness, 0.0F, gameTime);
    }

    public static void machineGlow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, long gameTime) {
        if (ClientHemp.darkAt(x, z)) return;
        glow(key, x, y, z, r, g, b, radius, brightness, inscatter, gameTime);
    }

    public static void machineGlow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, long gameTime) {
        machineGlow(key, x, y, z, r, g, b, radius, brightness, 0.0F, gameTime);
    }

    public static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, long gameTime) {
        if (!active() || shaderPackOn()) return;
        try {
            VeilLights.glow(key, x, y, z, r, g, b, radius, brightness, inscatter, gameTime);
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, int duration) {
        flash(x, y, z, r, g, b, radius, brightness, 0.0F, duration);
    }

    public static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, int duration) {
        if (!active() || shaderPackOn()) return;
        try {
            VeilLights.flash(x, y, z, r, g, b, radius, brightness, inscatter, duration, now());
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static double camDist(double x, double y, double z) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
    }

    public static boolean quasar(String emitter, double x, double y, double z) {
        if (!active() || quasarFailed) return false;
        if (shaderPackOn()) {
            if (QUASAR_LIGHT_ONLY.contains(emitter)) return true;
            for (String p : QUASAR_LIT) if (emitter.startsWith(p) && !emitter.endsWith("_nl")) { emitter = emitter + "_nl"; break; }
        }
        try {
            VeilQuasar.spawn(emitter, x, y, z);
            return true;
        } catch (Throwable t) {
            quasarFailed = true;
            Wmd.LOGGER.error("Quasar particles disabled for this session (emitter " + emitter + ")", t);
            return false;
        }
    }

    private static boolean quasarFailed;

    private static final java.util.List<String> QUASAR_LIT = java.util.List.of("he_firelight_", "inc_droplight_", "tbx_light_", "nuk_light_", "emp_arcs_", "frag_flash_");
    private static final java.util.Set<String> QUASAR_LIGHT_ONLY = java.util.Set.of("decoy_flares_lit");

    public static void bloomNear(double x, double y, double z, double range, float strength) {
        if (!active()) return;
        double d = camDist(x, y, z);
        if (d >= range) return;
        try {
            VeilPost.bloom((float) (strength * (1.0 - d / range)));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void haze(double x, double y, double z, double size, double range, float strength) {
        if (!active()) return;
        double d = camDist(x, y, z);
        if (d >= range) return;
        try {
            VeilPost.haze(x, y, z, size, (float) (strength * Math.min(1.0, 2.0 * (1.0 - d / range))));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void lens(double x, double y, double z, double r, float strength) {
        if (!active() || r <= 0.01) return;
        if (camDist(x, y, z) >= 256.0) return;
        try {
            VeilPost.lens(x, y, z, r, strength);
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void shock(double x, double y, double z) {
        if (!active()) return;
        try {
            VeilPost.shock(x, y, z);
        } catch (Throwable t) {
            fail(t);
        }
    }

    static void tick(long gameTime) {
        if (!active()) return;
        try {
            VeilLights.sweep(gameTime);
            VeilPost.tick();
        } catch (Throwable t) {
            fail(t);
        }
    }

    static void clear() {
        if (!active()) return;
        try {
            VeilLights.clear();
            VeilPost.clear();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void fail(Throwable t) {
        failed = true;
        Wmd.LOGGER.error("Veil lights disabled for this session", t);
    }
}
