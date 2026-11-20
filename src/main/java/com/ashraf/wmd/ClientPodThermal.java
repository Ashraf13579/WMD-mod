package com.ashraf.wmd;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPodThermal {

    private ClientPodThermal() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pod_thermal");

    private static boolean on;
    private static ShaderInstance maskShader, viewShader;
    private static TextureTarget mask, scene;
    private static long frame, maskFrame = -1;
    private static boolean drawingMask;
    private static final List<Integer> NEAR = new ArrayList<>();
    private static final List<Integer> SEEN = new ArrayList<>();

    private static boolean visor;

    public static void setVisor(boolean wanted) { visor = wanted; }

    public static boolean visorOn() { return visor; }

    private static boolean visorActive() {
        Minecraft mc = Minecraft.getInstance();
        return (visor || ClientExoMods.thermalVisor()) && !ClientPodCam.looking() && mc.player != null && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    static boolean active() { return (on && ClientPodCam.looking()) || visorActive(); }

    private static Vec3 lens(Minecraft mc) {
        if (ClientPodCam.looking()) return ClientPodCam.eye();
        return mc.player == null ? null : mc.player.getEyePosition();
    }

    static boolean blocked() { return active() && (maskShader == null || viewShader == null); }

    static void end() {
        on = false;
        NEAR.clear();
        SEEN.clear();
        close();
    }

    private static void close() {
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        maskFrame = -1;
    }

    @SubscribeEvent
    static void shaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pod_heat_mask"), DefaultVertexFormat.POSITION), s -> maskShader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pod_thermal_view"), DefaultVertexFormat.POSITION), s -> viewShader = s);
        } catch (IOException ex) {
            Wmd.LOGGER.warn("[wmd] pod thermal shaders failed to load: {}", ex.toString());
        }
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerBelowAll(LAYER, ClientPodThermal::picture); }

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
        if (!on) { NEAR.clear(); SEEN.clear(); close(); }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), on ? 1.4F : 1.1F, 0.4F));
        if (on && (maskShader == null || viewShader == null) && mc.player != null) mc.player.displayClientMessage(Component.translatable("hud.wmd.podcam.thermal_na"), true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = keyDown(mc);
        if (down && !wasDown && ClientPodCam.looking() && mc.screen == null) toggle(mc);
        wasDown = down;
        if (!active()) { NEAR.clear(); SEEN.clear(); if (mask != null || scene != null) close(); return; }
        NEAR.clear();
        SEEN.clear();
        Vec3 lens = lens(mc);
        if (mc.level == null || lens == null) return;
        double r2 = Balance.TPOD_THERMAL_RANGE * Balance.TPOD_THERMAL_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (NEAR.size() >= Balance.TPOD_THERMAL_MAX) break;
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || !le.isAlive() || e == mc.player || e.isInvisible() || e.isSpectator()) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            if (c.distanceToSqr(lens) > r2) continue;
            NEAR.add(e.getId());
            HitResult hit = mc.level.clip(new ClipContext(lens, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(c) < 1.0) SEEN.add(e.getId());
        }
    }

    @SubscribeEvent
    static void frameStart(RenderFrameEvent.Pre event) { frame++; }

    @SubscribeEvent
    static void nameTag(RenderNameTagEvent event) { if (drawingMask) event.setCanRender(TriState.FALSE); }

    private static final VertexConsumer NONE = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    @SubscribeEvent
    static void heatMask(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !active() || blocked() || WmdLights.shadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        int prevDraw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), prevRead = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int vx, vy, vw, vh;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer vp = st.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            vx = vp.get(0); vy = vp.get(1); vw = vp.get(2); vh = vp.get(3);
        }

        int depthType = 0, depthName = 0, depthLevel = 0;
        if (prevDraw != 0) {
            depthType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (depthType == GL11.GL_TEXTURE || depthType == GL30.GL_RENDERBUFFER)
                depthName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (depthType == GL11.GL_TEXTURE)
                depthLevel = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        }
        if (mask == null) { mask = new TextureTarget(w, h, false, Minecraft.ON_OSX); mask.setClearColor(0.0F, 0.0F, 0.0F, 0.0F); mask.setFilterMode(GL11.GL_LINEAR); }
        else if (mask.width != w || mask.height != h) { mask.resize(w, h, Minecraft.ON_OSX); mask.setFilterMode(GL11.GL_LINEAR); }
        mask.clear(Minecraft.ON_OSX);
        mask.bindWrite(true);
        boolean depth = false;
        if (depthName != 0) {
            if (depthType == GL11.GL_TEXTURE) GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthName, depthLevel);
            else GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthName);
            depth = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
            if (!depth) detachDepth(depthType);
        }
        List<Integer> ids = depth ? NEAR : SEEN;
        if (!ids.isEmpty()) drawBodies(event, mc, ids, depth);
        if (depth) detachDepth(depthType);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._viewport(vx, vy, vw, vh);
        maskFrame = frame;
    }

    private static void detachDepth(int type) {
        if (type == GL11.GL_TEXTURE) GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        else GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, 0);
    }

    private static void drawBodies(RenderLevelStageEvent event, Minecraft mc, List<Integer> ids, boolean depth) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        MultiBufferSource src = rt -> rt.mode() == VertexFormat.Mode.QUADS ? bb : NONE;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = new PoseStack();
        drawingMask = true;
        try {
            for (int id : ids) {
                Entity e = mc.level.getEntity(id);
                if (e == null) continue;
                try {
                    EntityRenderer<? super Entity> r = mc.getEntityRenderDispatcher().getRenderer(e);
                    Vec3 off = r.getRenderOffset(e, pt);
                    ps.pushPose();
                    ps.translate(Mth.lerp(pt, e.xOld, e.getX()) - cam.x + off.x, Mth.lerp(pt, e.yOld, e.getY()) - cam.y + off.y, Mth.lerp(pt, e.zOld, e.getZ()) - cam.z + off.z);
                    r.render(e, Mth.lerp(pt, e.yRotO, e.getYRot()), pt, ps, src, LightTexture.FULL_BRIGHT);
                } catch (RuntimeException ex) {

                } finally {
                    while (!ps.clear()) ps.popPose();
                }
            }
        } finally {
            drawingMask = false;
        }
        MeshData md = bb.build();
        if (md == null) return;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -4.0F);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> maskShader);
        BufferUploader.drawWithShader(md);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void picture(GuiGraphics g, DeltaTracker delta) {
        if (!active() || blocked()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        g.flush();
        if (scene == null) scene = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else if (scene.width != w || scene.height != h) scene.resize(w, h, Minecraft.ON_OSX);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        boolean heat = mask != null && maskFrame == frame;
        RenderSystem.setShaderTexture(0, scene.getColorTextureId());
        RenderSystem.setShaderTexture(1, heat ? mask.getColorTextureId() : scene.getColorTextureId());
        viewShader.safeGetUniform("HeatOn").set(heat ? 1 : 0);
        RenderSystem.setShader(() -> viewShader);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.addVertex(-1.0F, -1.0F, 0.0F);
        bb.addVertex(1.0F, -1.0F, 0.0F);
        bb.addVertex(1.0F, 1.0F, 0.0F);
        bb.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
