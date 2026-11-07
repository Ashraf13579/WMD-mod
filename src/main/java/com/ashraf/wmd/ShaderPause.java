package com.ashraf.wmd;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
final class ShaderPause {

    private ShaderPause() {}

    static final String MARKER = "wmd-shader-paused.txt";

    static boolean pausedByUs;
    static boolean overridden;
    static boolean bootChecked;
    private static boolean toldCutscene, toldOrbit;

    private static boolean looked, broken;
    private static Object config;
    private static Method enabledM, setM;

    private static boolean iris() {
        if (broken) return false;
        if (!looked) {
            looked = true;
            try {
                if (!ModList.get().isLoaded("iris")) { broken = true; return false; }
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi"), cfg = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
                Object inst = api.getMethod("getInstance").invoke(null);
                config = api.getMethod("getConfig").invoke(inst);
                enabledM = cfg.getMethod("areShadersEnabled");
                setM = cfg.getMethod("setShadersEnabledAndApply", boolean.class);
            } catch (Throwable e) {
                broken = true;
                Wmd.LOGGER.info("[wmd] Iris config API not found: shaders are not paused for cinematics / orbit ({})", e.toString());
                return false;
            }
        }
        return config != null;
    }

    static Boolean enabled() {
        if (!iris()) return null;
        try { return Boolean.TRUE.equals(enabledM.invoke(config)); } catch (Throwable e) { broken = true; return null; }
    }

    private static boolean set(boolean on) {
        if (!iris()) return false;
        try { setM.invoke(config, on); return true; } catch (Throwable e) { broken = true; Wmd.LOGGER.warn("[wmd] could not switch the shaders {}: {}", on ? "on" : "off", e.toString()); return false; }
    }

    static Path marker() { return FMLPaths.CONFIGDIR.get().resolve(MARKER); }

    private static boolean writeMarker() {
        try {
            Files.writeString(marker(), "WMD switched your Iris shaders off for a cinematic / orbit. If you see this file the game stopped before they were\n"
                    + "switched back on: WMD turns them on at the next start and deletes this file (or press K in game).\n", StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            Wmd.LOGGER.warn("[wmd] could not write {}: not pausing the shaders ({})", MARKER, e.toString());
            return false;
        }
    }

    private static void deleteMarker() {
        try { Files.deleteIfExists(marker()); } catch (Exception ignored) {}
    }

    static String reason(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        boolean cut = !ClientConfig.SPEC.isLoaded() || ClientConfig.SHADER_PAUSE_CUTSCENE.get();
        boolean orb = !ClientConfig.SPEC.isLoaded() || ClientConfig.SHADER_PAUSE_ORBIT.get();
        if (cut && ClientCutscene.active()) return "cutscene";
        if (orb && Orbit.isOrbit(mc.level)) return "orbit";
        return null;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!bootChecked) {
            bootChecked = true;
            if (Files.exists(marker())) {
                Boolean on = enabled();
                if (on != null && !on) set(true);
                deleteMarker();
                Wmd.LOGGER.info("[wmd] found {}: the last session ended during a shader pause; shaders switched back on", MARKER);
            }
        }
        String why = reason(mc);
        if (why == null) { overridden = false; if (pausedByUs) resume(); return; }
        if (pausedByUs) {
            if (Boolean.TRUE.equals(enabled())) { pausedByUs = false; overridden = true; deleteMarker(); }
            return;
        }
        if (overridden || !Boolean.TRUE.equals(enabled())) return;
        if (!writeMarker()) return;
        if (!set(false)) { deleteMarker(); return; }
        pausedByUs = true;
        Wmd.LOGGER.info("[wmd] shaders paused for the {}", why);
        boolean first = why.equals("cutscene") ? !toldCutscene : !toldOrbit;
        if (first) {
            if (why.equals("cutscene")) toldCutscene = true; else toldOrbit = true;
            mc.player.displayClientMessage(Component.translatable("wmd.shader_pause." + why), false);
        }
    }

    static void resume() {
        if (!pausedByUs) return;
        pausedByUs = false;
        set(true);
        deleteMarker();
        Wmd.LOGGER.info("[wmd] shaders back on");
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resume();
        overridden = false;
    }
}
