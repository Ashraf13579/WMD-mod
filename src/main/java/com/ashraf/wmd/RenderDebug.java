package com.ashraf.wmd;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class RenderDebug {
    private RenderDebug() {}

    private static boolean stats;

    private static int abState;
    private static boolean abKeep;
    private static long frames, since;

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {

        event.getDispatcher().register(Commands.literal("wmdcam")
                .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("r", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 200.0))
                                                .executes(c -> {
                                                    ClientCutscene.watchStation(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"),
                                                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "z"),
                                                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "r"));
                                                    return 1;
                                                }))))));

        if (!net.neoforged.fml.loading.FMLEnvironment.production) event.getDispatcher().register(Commands.literal("wmdr")
                .then(Commands.literal("fast").executes(c -> mode(true)))
                .then(Commands.literal("legacy").executes(c -> mode(false)))
                .then(Commands.literal("cull").then(Commands.literal("on").executes(c -> cull(true))).then(Commands.literal("off").executes(c -> cull(false))))
                .then(Commands.literal("lod").then(Commands.argument("px2", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 16.0F)).executes(c -> {
                    MeshModel.lodMinPx2 = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "px2");
                    reset();
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Distance detail: quads under " + MeshModel.lodMinPx2 + " screen pixels are skipped (0 = off)"));
                    return 1;
                })))
                .then(Commands.literal("gpu").then(Commands.literal("on").executes(c -> gpu(true))).then(Commands.literal("off").executes(c -> gpu(false))))
                .then(Commands.literal("ab").executes(c -> {
                    abKeep = MeshModel.gpu;
                    MeshModel.gpu = true;
                    abState = 1;
                    return 1;
                }))
                .then(Commands.literal("stats").executes(c -> {
                    stats = !stats;
                    reset();
                    c.getSource().sendSuccess(() -> Component.literal("Render stats " + (stats ? "on (action bar)" : "off")), false);
                    return 1;
                })));
    }

    private static int cull(boolean on) {
        MeshModel.cull = on;
        reset();
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Mesh frustum culling: " + (on ? "ON" : "OFF")));
        return 1;
    }

    private static int gpu(boolean on) {
        MeshModel.gpu = on;
        reset();
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("GPU-cached static bones: " + (on ? "ON" : "OFF")));
        return 1;
    }

    private static int mode(boolean fast) {
        MeshModel.fast = fast;
        reset();
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Mesh writer: " + (fast ? "FAST" : "LEGACY")));
        return 1;
    }

    private static void reset() {
        MeshModel.statQuads = 0;
        MeshModel.statNanos = 0;
        frames = 0;
        since = System.nanoTime();
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Post event) {
        if (abState > 0) {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, abState == 1 ? "ab_gpu_on.png" : "ab_gpu_off.png", mc.getMainRenderTarget(), msg -> {});
            if (abState == 1) { MeshModel.gpu = false; abState = 2; }
            else {
                MeshModel.gpu = abKeep;
                abState = 0;
                mc.gui.getChat().addMessage(Component.literal("Saved screenshots/ab_gpu_on.png and ab_gpu_off.png. Run: python tools/shot_diff.py runs/client/screenshots/ab_gpu_on.png runs/client/screenshots/ab_gpu_off.png"));
            }
            return;
        }
        if (!stats) return;
        frames++;
        long now = System.nanoTime();
        if (now - since < 1_000_000_000L) return;
        double secs = (now - since) / 1e9;
        double fps = frames / secs;
        long q = frames > 0 ? MeshModel.statQuads / frames : 0;
        long gpuQ = frames > 0 ? MeshModel.statGpu / frames : 0;
        long lod = frames > 0 ? MeshModel.statLod / frames : 0;
        long culled = frames > 0 ? MeshModel.statCulled / frames : 0;
        double ms = frames > 0 ? MeshModel.statNanos / 1e6 / frames : 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(String.format("%s%s | %d quads/frame (%d culled, %d tiny, %dk on gpu) | %.2f ms/frame mesh write | %.0f fps", MeshModel.fast ? "FAST" : "LEGACY", MeshModel.cull ? " CULL" : "", q, culled, lod, gpuQ / 1000, ms, fps)), false);
        String top = MeshModel.topStats(frames);
        if (!top.isEmpty() && mc.player != null) mc.gui.getChat().addMessage(Component.literal(top));
        MeshModel.statQuads = 0;
        MeshModel.statNanos = 0;
        MeshModel.statCulled = 0;
        MeshModel.statLod = 0;
        MeshModel.statGpu = 0;
        frames = 0;
        since = now;
    }
}
