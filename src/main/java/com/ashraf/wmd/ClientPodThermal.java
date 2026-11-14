package com.ashraf.wmd;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPodThermal {

    private ClientPodThermal() {}

    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "shaders/post/pod_thermal.json");

    private static boolean on;
    private static PostChain chain;
    private static boolean failed;
    private static int cw = -1, ch = -1;
    private static final Set<Integer> HOT = new HashSet<>();

    static boolean active() { return on && ClientPodCam.looking(); }

    static boolean blocked() { return active() && (VeilBridge.shaderPackOn() || failed); }

    public static boolean glows(Entity e) { return active() && !blocked() && HOT.contains(e.getId()); }

    static void end() {
        on = false;
        HOT.clear();
        close();
    }

    private static void close() {
        if (chain != null) { chain.close(); chain = null; }
        cw = ch = -1;
    }

    private static boolean wasDown;

    private static boolean keyDown(Minecraft mc) {
        com.mojang.blaze3d.platform.InputConstants.Key k = ClientCockpit.THERMAL.getKey();
        long win = mc.getWindow().getWindow();
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) return org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, k.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && k.getValue() >= 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, k.getValue());
    }

    @SubscribeEvent
    static void middleClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock() || !ClientPodCam.looking()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void toggle(Minecraft mc) {
        on = !on;
        if (!on) { HOT.clear(); close(); }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), on ? 1.4F : 1.1F, 0.4F));
        if (on && VeilBridge.shaderPackOn() && mc.player != null) mc.player.displayClientMessage(Component.translatable("hud.wmd.podcam.thermal_na"), true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc0 = Minecraft.getInstance();
        boolean down = keyDown(mc0);
        if (down && !wasDown && ClientPodCam.looking() && mc0.screen == null) toggle(mc0);
        wasDown = down;
        if (!active()) { if (!HOT.isEmpty()) HOT.clear(); if (!on && chain != null) close(); return; }
        Minecraft mc = Minecraft.getInstance();
        Vec3 lens = ClientPodCam.eye();
        HOT.clear();
        if (mc.level == null || lens == null) return;
        double r2 = Balance.TPOD_THERMAL_RANGE * Balance.TPOD_THERMAL_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (HOT.size() >= Balance.TPOD_THERMAL_MAX) break;
            if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player || e.isInvisible()) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            if (c.distanceToSqr(lens) > r2) continue;
            HitResult hit = mc.level.clip(new ClipContext(lens, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(c) < 1.0) HOT.add(e.getId());
        }
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !active() || blocked()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (chain == null) {
            try { chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN); }
            catch (Exception ex) { failed = true; Wmd.LOGGER.warn("[wmd] pod thermal shader failed to load: {}", ex.toString()); return; }
        }
        if (cw != main.width || ch != main.height) { chain.resize(main.width, main.height); cw = main.width; ch = main.height; }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }
}
