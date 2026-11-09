package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
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

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPeriscope {

    private ClientPeriscope() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "periscope_view");

    private static BlockPos active;
    private static Marker cam;
    private static int zoom;
    private static float yaw0;
    private static long enteredAt;

    public static boolean active() { return active != null; }

    public static boolean viewing(BlockPos pos) { return active != null && active.equals(pos); }

    public static float trainDeg() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0.0F : Mth.wrapDegrees(mc.player.getYRot() - yaw0);
    }

    private static PeriscopeBlockEntity be(Minecraft mc) {
        return active != null && mc.level != null && mc.level.getBlockEntity(active) instanceof PeriscopeBlockEntity p ? p : null;
    }

    public static void enter(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof PeriscopeBlockEntity p) || !p.canLook()) return;
        active = pos.immutable();
        zoom = 1;
        yaw0 = mc.player.getYRot();
        enteredAt = mc.level.getGameTime();
        mc.player.setXRot(0.0F);
        cam = new Marker(EntityType.MARKER, mc.level);
        place(mc, p, 1.0F);
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.player.closeContainer();
        mc.setScreen(null);
        mc.setCameraEntity(cam);
        mc.player.displayClientMessage(Component.translatable("hud.wmd.periscope.enter"), true);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.0F, 0.6F));
    }

    static void leave(String why) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        active = null;
        cam = null;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (why != null) mc.player.displayClientMessage(Component.translatable(why), true);
        }
    }

    private static void place(Minecraft mc, PeriscopeBlockEntity p, float pt) {
        if (cam == null || mc.player == null) return;
        Vec3 h = p.head();

        float yr = mc.player.getYRot() * Mth.DEG_TO_RAD;
        h = h.add(-Mth.sin(yr) * Balance.PERI_EYE_AHEAD, 0.0, Mth.cos(yr) * Balance.PERI_EYE_AHEAD);
        cam.setPos(h.x, h.y, h.z);
        cam.xo = cam.xOld = h.x;
        cam.yo = cam.yOld = h.y;
        cam.zo = cam.zOld = h.z;
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.yRotO = mc.player.getYRot();
        cam.xRotO = mc.player.getXRot();
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        PeriscopeBlockEntity p = be(mc);
        if (active == null || mc.player == null || p == null) return;
        if (mc.player.getXRot() > 30.0F || mc.player.getXRot() < -30.0F) { float x = Mth.clamp(mc.player.getXRot(), -30.0F, 30.0F); mc.player.setXRot(x); mc.player.xRotO = x; }
        place(mc, p, event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        LocalPlayer pl = mc.player;
        PeriscopeBlockEntity p = be(mc);
        if (pl == null || !pl.isAlive() || mc.level == null || p == null) { leave(null); return; }
        if (mc.screen != null) { leave(null); return; }
        if (pl.isShiftKeyDown()) { leave(null); return; }
        if (!p.fullyUp()) { leave("hud.wmd.periscope.lowered"); return; }
        if (p.headWet()) { leave("hud.wmd.periscope.under"); return; }
        Vec3 eyepiece = SableBridge.worldPos(mc.level, active.getX() + 0.5, active.getY() + 1.6, active.getZ() + 0.5);
        if (pl.getEyePosition().distanceTo(eyepiece) > Balance.PERI_REACH + 1.5) leave(null);
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
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (active == null) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom + (event.getScrollDeltaY() > 0 ? 1 : -1), 0, 2);
    }

    private static double zoomFactor() { return zoom == 2 ? Balance.PERI_ZOOM_HIGH : zoom == 1 ? Balance.PERI_ZOOM_LOW : 1.0; }

    @SubscribeEvent
    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (active != null && event.usedConfiguredFov()) event.setFOV(event.getFOV() / zoomFactor());
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (active != null) event.setCanceled(true); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientPeriscope::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.options.hideGui || mc.level == null || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        int r = Math.min(w, h) / 2 - 6;

        for (int y = 0; y < h; y += 2) {
            int dy = y - cy, half = dy * dy >= r * r ? -1 : (int) Math.sqrt(r * r - dy * dy);
            if (half < 0) g.fill(0, y, w, y + 2, 0xF0000000);
            else { g.fill(0, y, cx - half, y + 2, 0xF0000000); g.fill(cx + half, y, w, y + 2, 0xF0000000); }
        }
        int c = 0xC0E0E8E0;
        g.fill(cx - r + 8, cy, cx - 12, cy + 1, c);
        g.fill(cx + 12, cy, cx + r - 8, cy + 1, c);
        g.fill(cx, cy - r + 8, cx + 1, cy - 12, c);
        g.fill(cx, cy + 12, cx + 1, cy + r - 8, c);
        for (int k = 1; k <= 4; k++) {
            int d = k * r / 5;
            g.fill(cx - d, cy - 3, cx - d + 1, cy + 4, c);
            g.fill(cx + d, cy - 3, cx + d + 1, cy + 4, c);
        }
        Font font = mc.font;
        int brg = Math.floorMod(Math.round(mc.player.getYRot() + 180.0F), 360);
        String top = String.format("%03d", brg) + "°   x" + (zoom == 0 ? "1" : zoom == 1 ? String.valueOf(Balance.PERI_ZOOM_LOW) : String.valueOf((int) Balance.PERI_ZOOM_HIGH));
        g.drawCenteredString(font, top, cx, cy - r + 14, 0xFFE8F0E8);
        g.drawCenteredString(font, Component.translatable("hud.wmd.periscope.hint"), cx, cy + r - 22, 0xFFB0C0B0);
        if (mc.level.getGameTime() - enteredAt < 6) g.fill(0, 0, w, h, 0xFF000000);
    }

    public static void clearStatics() { active = null; cam = null; }
}
