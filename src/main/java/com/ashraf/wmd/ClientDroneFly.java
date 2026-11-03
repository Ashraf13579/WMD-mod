package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientDroneFly {

    private ClientDroneFly() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_fly");

    private static int active = -1;
    private static Marker cam;
    private static double px, py, pz, cx, cy, cz;

    private static float pyaw, cyaw, lookYaw0;

    private static double nx, ny, nz;
    private static float nyaw;

    private static double zoomT = 1.0, zoomNow = 1.0;
    private static final double ZOOM_MIN = 1.0, ZOOM_MAX = 8.0, ZOOM_STEP = 1.25;
    private static final java.util.Set<ResourceLocation> HIDE = java.util.Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.EFFECTS);
    private static DroneFlyPayload last;
    private static float fwd, strafe, up;
    private static boolean fire, leave;

    public static boolean flying(int id) { return active == id; }

    public static void onFly(DroneFlyPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (!p.active()) { if (active == p.id()) leave(); return; }
        if (mc.player == null || mc.level == null) return;
        if (active != p.id()) {
            active = p.id();
            cam = new Marker(EntityType.MARKER, mc.level);
            px = cx = nx = p.x(); py = cy = ny = p.y(); pz = cz = nz = p.z();
            pyaw = cyaw = nyaw = p.yaw();
            lookYaw0 = mc.player.getYRot();
            zoomT = zoomNow = 1.0;
            mc.setCameraEntity(cam);
            mc.player.displayClientMessage(Component.translatable("hud.wmd.drone.enter"), true);
            last = p;
            place(mc);
            return;
        }
        nx = p.x(); ny = p.y(); nz = p.z(); nyaw = p.yaw();
        last = p;
    }

    private static void leave() {
        Minecraft mc = Minecraft.getInstance();
        active = -1;
        cam = null;
        last = null;
        fwd = strafe = up = 0;
        fire = leave = false;
        zoomT = zoomNow = 1.0;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    private static void place(Minecraft mc) {
        if (cam == null || mc.player == null) return;
        double eye = 0.25;
        cam.setPos(cx, cy + eye, cz);
        cam.xo = cam.xOld = px;
        cam.yo = cam.yOld = py + eye;
        cam.zo = cam.zOld = pz;
        look(mc);
    }

    private static boolean watching() { return last != null && last.range() < 0; }

    private static void look(Minecraft mc) {
        if (cam == null || mc.player == null) return;
        if (watching()) {
            cam.setYRot(cyaw + mc.player.getYRot() - lookYaw0);
            cam.yRotO = pyaw + mc.player.yRotO - lookYaw0;
        } else {
            cam.setYRot(mc.player.getYRot());
            cam.yRotO = mc.player.yRotO;
        }
        cam.setXRot(mc.player.getXRot());
        cam.xRotO = mc.player.xRotO;
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        if (active >= 0 && cam != null) {
            Minecraft mc = Minecraft.getInstance();
            zoomNow += (zoomT - zoomNow) * 0.25;
            if (Math.abs(zoomT - zoomNow) < 0.005) zoomNow = zoomT;
            look(mc);
            if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive()) mc.mouseHandler.grabMouse();
        }
    }

    @SubscribeEvent
    static void camTick(ClientTickEvent.Pre event) {
        if (active < 0 || cam == null) return;
        px = cx; py = cy; pz = cz; cx = nx; cy = ny; cz = nz;
        pyaw = cyaw; cyaw = pyaw + net.minecraft.util.Mth.wrapDegrees(nyaw - pyaw);
        Minecraft mc = Minecraft.getInstance();
        place(mc);

        while (mc.options.keyInventory.consumeClick()) { }
        while (mc.options.keyDrop.consumeClick()) { }
        while (mc.options.keySwapOffhand.consumeClick()) { }
        for (var k : mc.options.keyHotbarSlots) while (k.consumeClick()) { }
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (active < 0 || Minecraft.getInstance().screen != null || event.getScrollDeltaY() == 0) return;
        event.setCanceled(true);
        zoomT = net.minecraft.util.Mth.clamp(event.getScrollDeltaY() > 0 ? zoomT * ZOOM_STEP : zoomT / ZOOM_STEP, ZOOM_MIN, ZOOM_MAX);
    }

    @SubscribeEvent
    static void fov(ViewportEvent.ComputeFov event) {
        if (active >= 0 && event.usedConfiguredFov() && zoomNow > 1.0) event.setFOV(event.getFOV() / zoomNow);
    }

    @SubscribeEvent
    static void turn(CalculatePlayerTurnEvent event) {
        if (active < 0 || zoomNow <= 1.0) return;
        double f = event.getMouseSensitivity() * 0.6 + 0.2;
        event.setMouseSensitivity((f * Math.cbrt(1.0 / zoomNow) - 0.2) / 0.6);
    }

    @SubscribeEvent
    static void hud(RenderGuiLayerEvent.Pre event) {
        if (active >= 0 && HIDE.contains(event.getName())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (active < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !p.isAlive() || mc.level == null) { leave(); return; }
        PacketDistributor.sendToServer(new DroneControlPayload(active, fwd, strafe, up, p.getYRot(), fire, leave, mc.options.keySprint.isDown()));
        fire = false;
        leave = false;
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (active < 0) return;
        var in = event.getInput();
        fwd = in.forwardImpulse;
        strafe = in.leftImpulse;
        up = (in.jumping ? 1.0F : 0.0F) - (in.shiftKeyDown ? 1.0F : 0.0F);
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (active < 0) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isAttack()) {
            fire = true;
            if (last != null && last.range() < 0 && cam != null) lase(Minecraft.getInstance());
        } else if (event.isUseItem()) leave = true;
    }

    private static void lase(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        net.minecraft.world.phys.Vec3 from = cam.getEyePosition(), to = from.add(cam.getViewVector(1.0F).scale(400.0));
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, cam));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        PacketDistributor.sendToServer(new DroneLasePayload(active, hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()));
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (active >= 0) event.setCanceled(true); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientDroneFly::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        DroneFlyPayload f = last;
        if (active < 0 || f == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight(), cxp = w / 2, cyp = h / 2;
        Font font = mc.font;
        int green = 0xFF7CFF8A, warn = 0xFFFFB040, frame = 0x80FFFFFF;

        int m = 24, l = 18;
        g.fill(m, m, m + l, m + 1, frame); g.fill(m, m, m + 1, m + l, frame);
        g.fill(w - m - l, m, w - m, m + 1, frame); g.fill(w - m - 1, m, w - m, m + l, frame);
        g.fill(m, h - m - 1, m + l, h - m, frame); g.fill(m, h - m - l, m + 1, h - m, frame);
        g.fill(w - m - l, h - m - 1, w - m, h - m, frame); g.fill(w - m - 1, h - m - l, w - m, h - m, frame);
        g.fill(cxp - 6, cyp, cxp - 1, cyp + 1, green); g.fill(cxp + 2, cyp, cxp + 7, cyp + 1, green);
        g.fill(cxp, cyp - 6, cxp + 1, cyp - 1, green); g.fill(cxp, cyp + 2, cxp + 1, cyp + 7, green);
        double speed = Math.sqrt((cx - px) * (cx - px) + (cy - py) * (cy - py) + (cz - pz) * (cz - pz)) * 20.0;
        Component alt = Component.translatable("hud.wmd.drone.alt", f.alt() < 0 ? "?" : Integer.toString(f.alt()));
        Component spd = Component.translatable("hud.wmd.drone.speed", (int) Math.round(speed));
        Component bat = Component.translatable("hud.wmd.drone.battery", f.battery());
        boolean watch = f.range() < 0;
        Component home = Component.translatable("hud.wmd.drone.home", f.home(), Math.abs(f.range()));
        g.drawString(font, alt, m + 6, m + 6, green, true);
        g.drawString(font, spd, m + 6, m + 18, green, true);
        if (zoomT > 1.0) g.drawString(font, Component.translatable("hud.wmd.drone.zoom", String.format("%.1f", zoomT)), m + 6, m + 30, green, true);
        g.drawString(font, bat, w - m - 6 - font.width(bat), m + 6, f.battery() < 20 ? warn : green, true);
        g.drawString(font, home, w - m - 6 - font.width(home), m + 18, green, true);
        Component hint = Component.translatable(watch ? "hud.wmd.drone.watch_hint" : "hud.wmd.drone.hint");
        g.drawString(font, hint, cxp - font.width(hint) / 2, h - m - 14, 0xFFE0E0E0, true);
        g.drawString(font, Component.translatable("hud.wmd.drone.rec"), m + 6, h - m - 14, 0xFFFF4040, true);
    }
}
