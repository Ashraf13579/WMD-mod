package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCctv {

    private ClientCctv() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cctv_live");
    private static final int[] ZOOMS = { 2, 4 };

    static CctvPayloads.Open open;
    static final Map<Long, CctvPayloads.FeedData> FEEDS = new HashMap<>();
    static final Map<Long, Long> FEED_AT = new HashMap<>();

    public static void onOpen(CctvPayloads.Open p) {
        open = p;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SecurityMonitorScreen s) s.refresh(p);
        else if (active == null) mc.setScreen(new SecurityMonitorScreen(p));
    }

    public static void onFeed(CctvPayloads.Feed p) {
        long now = System.currentTimeMillis();
        for (CctvPayloads.FeedData f : p.feeds()) { FEEDS.put(f.pos(), f); FEED_AT.put(f.pos(), now); }
    }

    static CctvPayloads.FeedData feed(long pos) { return FEEDS.get(pos); }

    private static CctvPayloads.CamInfo active;
    private static int activeIndex;
    private static Marker cam;
    private static int zoom;
    private static float sentPan = Float.NaN;
    private static long enteredAt;

    public static boolean active() { return active != null; }

    public static boolean viewing(BlockPos pos) { return active != null && active.pos() == pos.asLong(); }

    static Vec3 camPos() { return active == null ? Vec3.ZERO : CctvPayloads.lens(new CctvNet.Cam(BlockPos.of(active.pos()), active.yaw(), active.covert())); }

    static float camYaw() { Minecraft mc = Minecraft.getInstance(); return mc.player == null ? 0.0F : mc.player.getYRot(); }

    static void enter(CctvPayloads.CamInfo c, int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || open == null) return;
        active = c;
        activeIndex = index;
        zoom = 0;
        enteredAt = mc.level.getGameTime();
        mc.player.setYRot(c.yaw());
        mc.player.setXRot(12.0F);
        cam = new Marker(EntityType.MARKER, mc.level);
        place(mc);
        mc.setScreen(null);
        mc.setCameraEntity(cam);
        send(mc.player);
        mc.player.displayClientMessage(Component.translatable("hud.wmd.cctv.enter"), true);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.0F, 0.8F));
    }

    static void leave(boolean backToGrid) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        active = null;
        cam = null;
        sentPan = Float.NaN;
        if (open != null) PacketDistributor.sendToServer(new CctvPayloads.View(open.monitor(), 0L, 0.0F));
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (backToGrid && open != null && mc.screen == null) mc.setScreen(new SecurityMonitorScreen(open));
    }

    private static float pan(LocalPlayer p) { return Mth.wrapDegrees(active.yaw() - p.getYRot()); }

    private static void send(LocalPlayer p) {
        sentPan = pan(p);
        PacketDistributor.sendToServer(new CctvPayloads.View(open.monitor(), active.pos(), sentPan));
    }

    private static void place(Minecraft mc) {
        if (cam == null || mc.player == null || active == null) return;
        Vec3 s = camPos();
        cam.setPos(s.x, s.y, s.z);
        cam.xo = cam.xOld = s.x;
        cam.yo = cam.yOld = s.y;
        cam.zo = cam.zOld = s.z;
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.yRotO = mc.player.getYRot();
        cam.xRotO = mc.player.getXRot();
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.player == null) return;

        float d = Mth.wrapDegrees(mc.player.getYRot() - active.yaw());
        if (Math.abs(d) > Balance.CCTV_PAN) { float y = active.yaw() + Math.copySign(Balance.CCTV_PAN, d); mc.player.setYRot(y); mc.player.yRotO = y; }
        if (mc.player.getXRot() > 60.0F || mc.player.getXRot() < -45.0F) { float x = Mth.clamp(mc.player.getXRot(), -45.0F, 60.0F); mc.player.setXRot(x); mc.player.xRotO = x; }
        place(mc);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        LocalPlayer p = mc.player;
        if (p == null || !p.isAlive() || mc.level == null || open == null) { leave(false); return; }
        if (mc.screen != null) { leave(false); return; }
        if (p.isShiftKeyDown() || p.position().distanceTo(Vec3.atCenterOf(open.monitor())) > CcSecurityMonitorBlockEntity.REACH + 1.0) { leave(true); return; }
        long t = mc.level.getGameTime();
        if (t % 4 == 0 && Math.abs(Mth.wrapDegrees(pan(p) - sentPan)) > 0.5F) send(p);
        if (t % 10 == 0) PacketDistributor.sendToServer(new CctvPayloads.FeedRequest(open.monitor(), List.of(active.pos())));
        if (t % 40 == 0) PacketDistributor.sendToServer(new CctvPayloads.View(open.monitor(), active.pos(), pan(p)));
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (active == null) return;
        var in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (active == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || open == null) return;
        CctvPayloads.FeedData f = feed(active.pos());
        if (f == null) return;
        Vec3 eye = camPos(), look = Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot());
        CctvPayloads.Mark best = null;
        boolean animal = false;
        double bestCos = Math.cos(Math.toRadians(6.0)), animalCos = bestCos;
        for (CctvPayloads.Mark m : f.marks()) {
            Vec3 d = new Vec3(m.x(), m.y() + 0.6, m.z()).subtract(eye);
            double c = d.normalize().dot(look);
            if (m.kind() == AreaMarkersPayload.CREATURE) { if (c > animalCos) { animalCos = c; animal = true; } continue; }
            if (c > bestCos) { bestCos = c; best = m; }
        }
        if (best == null) { mc.player.displayClientMessage(Component.translatable(animal ? "hud.wmd.cctv.mark_animal" : "hud.wmd.cctv.mark_none"), true); return; }
        WmdUi.confirm();
        PacketDistributor.sendToServer(new CctvPayloads.MarkTarget(open.monitor(), active.pos(), best.id()));
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (active == null) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom + (event.getScrollDeltaY() > 0 ? 1 : -1), 0, ZOOMS.length);
    }

    @SubscribeEvent
    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (active != null && zoom > 0 && event.usedConfiguredFov()) event.setFOV(event.getFOV() / ZOOMS[zoom - 1]);
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (active != null) event.setCanceled(true); }

    @SubscribeEvent
    static void boxes(RenderLevelStageEvent event) {
        if (active == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        CctvPayloads.FeedData f = feed(active.pos());
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.level == null) return;
        Vec3 c = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        for (CctvPayloads.Mark m : f.marks()) {
            Entity e = mc.level.getEntity(m.id());
            AABB b = e != null ? e.getBoundingBox() : new AABB(m.x() - 0.3, m.y(), m.z() - 0.3, m.x() + 0.3, m.y() + 1.8, m.z() + 0.3);
            float r = m.kind() == AreaMarkersPayload.MOB ? 1.0F : m.kind() == AreaMarkersPayload.PLAYER ? 1.0F : 0.4F;
            float g = m.kind() == AreaMarkersPayload.MOB ? 0.25F : 1.0F, bl = m.kind() == AreaMarkersPayload.MOB ? 0.2F : m.kind() == AreaMarkersPayload.PLAYER ? 1.0F : 0.45F;
            LevelRenderer.renderLineBox(ps, vc, b.move(-c.x, -c.y, -c.z), r, g, bl, 0.9F);
        }
        buf.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientCctv::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.options.hideGui || mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        long t = mc.level.getGameTime();
        CctvPayloads.FeedData f = feed(active.pos());
        int state = f == null ? CcSecurityMonitorBlockEntity.LIVE : f.state();
        if (f != null && f.dark()) g.fill(0, 0, w, h, 0x3A1AFF40);
        for (int y = (int) (t % 4); y < h; y += 4) g.fill(0, y, w, y + 1, 0x14000000);
        if (state != CcSecurityMonitorBlockEntity.LIVE) {
            g.fill(0, 0, w, h, 0xE0101418);
            long seed = t / 2;
            for (int k = 0; k < 220; k++) {
                long hsh = (seed * 31 + k) * 0x9E3779B97F4A7C15L;
                int x = (int) (((hsh >>> 8) & 1023) * w / 1024), y = (int) (((hsh >>> 20) & 1023) * h / 1024);
                g.fill(x, y, x + 6, y + 2, 0x70FFFFFF);
            }
            Component s = Component.translatable(state == CcSecurityMonitorBlockEntity.STATIC ? "hud.wmd.cctv.static" : "hud.wmd.cctv.lost");
            g.drawCenteredString(mc.font, s, w / 2, h / 2 - 4, 0xFFFF8080);
        }
        Font font = mc.font;
        BlockPos p = BlockPos.of(active.pos());
        String name = Component.translatable(active.covert() ? "hud.wmd.cctv.cam_covert" : "hud.wmd.cctv.cam", activeIndex + 1).getString() + "  " + p.getX() + " " + p.getY() + " " + p.getZ();
        g.drawString(font, name, 10, 10, 0xFFE8F0E8, true);
        if ((t / 10) % 2 == 0) { g.fill(w - 62, 11, w - 56, 17, 0xFFFF3030); }
        g.drawString(font, "REC", w - 52, 10, 0xFFFF6060, true);
        long dt = mc.level.getDayTime() % 24000L;
        int hh = (int) ((dt / 1000L + 6) % 24), mm = (int) ((dt % 1000L) * 60L / 1000L);
        g.drawString(font, String.format("%02d:%02d", hh, mm) + (zoom > 0 ? "   x" + ZOOMS[zoom - 1] : ""), w - 52, 22, 0xFFE8F0E8, true);
        int n = f == null ? 0 : f.marks().size();
        g.drawString(font, Component.translatable("hud.wmd.cctv.seen", n), 10, 22, 0xFFB0C0B0, true);

        int cx = w / 2, cy = h / 2;
        g.fill(cx - 10, cy - 10, cx - 5, cy - 9, 0xC0FFFFFF); g.fill(cx - 10, cy - 10, cx - 9, cy - 5, 0xC0FFFFFF);
        g.fill(cx + 5, cy - 10, cx + 10, cy - 9, 0xC0FFFFFF); g.fill(cx + 9, cy - 10, cx + 10, cy - 5, 0xC0FFFFFF);
        g.fill(cx - 10, cy + 9, cx - 5, cy + 10, 0xC0FFFFFF); g.fill(cx - 10, cy + 5, cx - 9, cy + 10, 0xC0FFFFFF);
        g.fill(cx + 5, cy + 9, cx + 10, cy + 10, 0xC0FFFFFF); g.fill(cx + 9, cy + 5, cx + 10, cy + 10, 0xC0FFFFFF);
        Component hint = Component.translatable("hud.wmd.cctv.hint");
        g.drawString(font, hint, cx - font.width(hint) / 2, h - 40, 0xFFE0E0E0, true);
        if (t - enteredAt < 6) g.fill(0, 0, w, h, 0xFF000000);
    }

    public static void clearStatics() { open = null; FEEDS.clear(); FEED_AT.clear(); active = null; cam = null; }
}
