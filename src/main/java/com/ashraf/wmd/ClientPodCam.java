package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPodCam {

    private ClientPodCam() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pod_camera");

    private static BlockPos pod;
    private static Marker cam;

    private static int phase;
    private static double transStart;
    private static int zoom = 1;
    private static long enteredAt;
    private static double lastFov = 70.0;

    private static float az, el = (float) Balance.TPOD_CAM_EL_DEF;
    private static Vec3 look;
    private static float lastYaw;

    private static float pAz, pEl;
    private static boolean slewPend;
    private static long slewAt = -100L;
    private static Vec3[] lastAx;

    public static boolean active() { return pod != null; }

    static boolean looking() { return pod != null && cam != null && phase == 0; }

    static Vec3 eye() { return cam == null ? null : cam.position(); }

    static Vec3 lookDir() { return cam == null || phase != 0 ? null : look; }

    static Vec3 takeSlew() {
        if (!slewPend || lastAx == null || cam == null || phase != 0) return null;
        slewPend = false;
        return dirOf(lastAx, pAz, pEl);
    }

    private static boolean dragging(Minecraft mc) { return mc.level != null && mc.level.getGameTime() - slewAt < 10L; }

    static void toggle(CockpitSeatEntity seat, CockpitHudPayload h) {
        Minecraft mc = Minecraft.getInstance();
        if (pod != null) { if (phase == 0) startExit(mc); else leave(null); return; }
        if (mc.player == null || mc.level == null || seat == null || !AirGate.on()) return;
        if (h == null || h.pod() == CockpitHudPayload.NONE || !h.has(CockpitHudPayload.F_POWER)) { mc.player.displayClientMessage(Component.translatable("hud.wmd.podcam.none"), true); return; }
        BlockPos p = BlockPos.of(h.pod());
        if (!(mc.level.getBlockEntity(p) instanceof TargetingPodBlockEntity)) { mc.player.displayClientMessage(Component.translatable("hud.wmd.podcam.none"), true); return; }
        pod = p;
        az = 0.0F; el = (float) Balance.TPOD_CAM_EL_DEF; look = null;
        enteredAt = mc.level.getGameTime() + Balance.TPOD_TRANS_TICKS;
        cam = new Marker(EntityType.MARKER, mc.level);
        phase = 1;
        transStart = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        transit(mc, 0.0);
        mc.setCameraEntity(cam);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.2F, 0.6F));
    }

    private static void startExit(Minecraft mc) {
        if (phase == 2) return;
        phase = 2;
        transStart = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 0.9F, 0.5F));
    }

    private static void transit(Minecraft mc, double k) {
        if (cam == null || mc.player == null) return;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(pt);
        boolean fresh = ClientPodPage.screenWorld != null && mc.level != null && mc.level.getGameTime() - ClientPodPage.screenAt <= 3;
        Vec3 scr = fresh ? ClientPodPage.screenWorld : eye.add(mc.player.getViewVector(pt).scale(0.6));
        Vec3 stand = fresh && ClientPodPage.screenToPilot != null ? scr.add(ClientPodPage.screenToPilot.scale(0.07)) : scr;
        double e = k * k * (3.0 - 2.0 * k);
        Vec3 at = eye.lerp(stand, e);
        Vec3 d = scr.subtract(at);
        float yaw0 = mc.player.getViewYRot(pt), pitch0 = mc.player.getViewXRot(pt);
        float yaw1 = d.lengthSqr() > 1.0E-9 ? (float) Math.toDegrees(Math.atan2(-d.x, d.z)) : yaw0;
        float pitch1 = d.lengthSqr() > 1.0E-9 ? (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))) : pitch0;
        float yaw = yaw0 + Mth.wrapDegrees(yaw1 - yaw0) * (float) Math.min(1.0, e * 1.6), pitch = Mth.lerp((float) Math.min(1.0, e * 1.6), pitch0, pitch1);
        cam.setPos(at.x, at.y, at.z);
        cam.xo = cam.xOld = at.x; cam.yo = cam.yOld = at.y; cam.zo = cam.zOld = at.z;
        cam.setYRot(yaw); cam.yRotO = yaw;
        cam.setXRot(pitch); cam.xRotO = pitch;
    }

    private static double transK(Minecraft mc) {
        if (mc.level == null) return 1.0;
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        return Mth.clamp((now - transStart) / Balance.TPOD_TRANS_TICKS, 0.0, 1.0);
    }

    static void leave(String why) {
        Minecraft mc = Minecraft.getInstance();
        if (pod == null) return;
        pod = null;
        cam = null;
        phase = 0;
        ClientPodThermal.end();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (why != null) mc.player.displayClientMessage(Component.translatable(why), true);
        }
    }

    private static TargetingPodBlockEntity be(Minecraft mc) {
        return pod != null && mc.level != null && mc.level.getBlockEntity(pod) instanceof TargetingPodBlockEntity t ? t : null;
    }

    private static void place(Minecraft mc, CockpitHudPayload h) {
        TargetingPodBlockEntity t = be(mc);
        if (cam == null || mc.player == null || t == null) return;
        Vec3 l = t.lensLocal();
        Vec3 w = ShipFrame.drawnWorld(mc.level, t.getBlockPos(), l, Float.NaN);
        Vec3[] ax = axes(mc, t);
        if (w == null || ax == null) return;
        Vec3 d = null;
        lastAx = ax;
        if (h != null && h.lasing() && dragging(mc)) d = dirOf(ax, pAz, pEl);
        else if (h != null && h.lasing()) {
            Vec3 s = new Vec3(h.px(), h.py(), h.pz()).subtract(w);
            if (s.lengthSqr() > 1.0E-6) {
                d = s.normalize();
                el = (float) Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp(d.dot(ax[1]), -1.0, 1.0))), Balance.TPOD_CAM_EL_MIN, Balance.TPOD_CAM_EL_MAX);
                az = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.dot(ax[2]), d.dot(ax[0]))), -Balance.TPOD_CAM_AZ, Balance.TPOD_CAM_AZ);
            }
        }
        if (d == null) d = dirOf(ax);
        look = d;
        double hz = Math.hypot(d.x, d.z);
        float yaw = hz > 1.0E-4 ? (float) Math.toDegrees(Math.atan2(-d.x, d.z)) : lastYaw;
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, hz));
        lastYaw = yaw;
        cam.setPos(w.x, w.y, w.z);
        cam.xo = cam.xOld = w.x;
        cam.yo = cam.yOld = w.y;
        cam.zo = cam.zOld = w.z;
        cam.setYRot(yaw); cam.yRotO = yaw;
        cam.setXRot(pitch); cam.xRotO = pitch;
    }

    private static Vec3[] axes(Minecraft mc, TargetingPodBlockEntity t) {
        Direction m = t.mountSide(), f = t.facing();
        BlockPos bp = t.getBlockPos();
        Vec3 c = Vec3.atCenterOf(bp);
        Vec3 c0 = ShipFrame.drawnWorld(mc.level, bp, c, Float.NaN);
        Vec3 cf = ShipFrame.drawnWorld(mc.level, bp, c.add(f.getStepX(), f.getStepY(), f.getStepZ()), Float.NaN);
        Vec3 co = ShipFrame.drawnWorld(mc.level, bp, c.add(-m.getStepX(), -m.getStepY(), -m.getStepZ()), Float.NaN);
        if (c0 == null || cf == null || co == null) return null;
        Vec3 out = co.subtract(c0), nose = cf.subtract(c0);
        if (out.lengthSqr() < 1.0E-9 || nose.lengthSqr() < 1.0E-9) return null;
        out = out.normalize();
        nose = nose.subtract(out.scale(nose.dot(out)));
        if (nose.lengthSqr() < 1.0E-6) nose = Math.abs(out.y) < 0.9 ? new Vec3(0, 1, 0).cross(out) : new Vec3(1, 0, 0).cross(out);
        nose = nose.normalize();
        return new Vec3[] { nose, out, out.cross(nose).normalize() };
    }

    private static Vec3 dirOf(Vec3[] ax) { return dirOf(ax, az, el); }

    private static Vec3 dirOf(Vec3[] ax, float azDeg, float elDeg) {
        double a = Math.toRadians(azDeg), e = Math.toRadians(elDeg);
        return ax[0].scale(Math.cos(e) * Math.cos(a)).add(ax[2].scale(Math.cos(e) * Math.sin(a))).add(ax[1].scale(Math.sin(e))).normalize();
    }

    @SubscribeEvent
    static void turn(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent event) {
        if (pod == null) return;
        Minecraft mc = Minecraft.getInstance();
        double f = event.getMouseSensitivity() * 0.6 + 0.2, k = f * f * f * 8.0 * 0.15 / Balance.TPOD_ZOOMS[zoom];
        CockpitHudPayload h = ClientCockpitBridge.hud();
        double dx = mc.mouseHandler.getXVelocity(), dy = mc.mouseHandler.getYVelocity() * (mc.options.invertYMouse().get() ? -1.0 : 1.0);
        if (phase == 0 && (h == null || !h.lasing())) {
            az = (float) Mth.clamp(az + dx * k, -Balance.TPOD_CAM_AZ, Balance.TPOD_CAM_AZ);
            el = (float) Mth.clamp(el + dy * k, Balance.TPOD_CAM_EL_MIN, Balance.TPOD_CAM_EL_MAX);
        } else if (phase == 0 && mc.level != null && (dx != 0.0 || dy != 0.0)) {
            if (!dragging(mc)) { pAz = az; pEl = el; }
            pAz = (float) Mth.clamp(pAz + dx * k, -Balance.TPOD_CAM_AZ, Balance.TPOD_CAM_AZ);
            pEl = (float) Mth.clamp(pEl + dy * k, Balance.TPOD_CAM_EL_MIN, Balance.TPOD_CAM_EL_MAX);
            slewPend = true;
            slewAt = mc.level.getGameTime();
        }
        event.setMouseSensitivity(-0.2 / 0.6);
        event.setCinematicCameraEnabled(false);
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        if (pod == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (phase == 1) {
            double k = transK(mc);
            if (k >= 1.0) { phase = 0; enteredAt = mc.level.getGameTime(); }
            else { transit(mc, k); return; }
        } else if (phase == 2) {
            double k = transK(mc);
            if (k >= 1.0) { leave(null); return; }
            transit(mc, 1.0 - k);
            return;
        }
        place(mc, ClientCockpitBridge.hud());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (pod == null) return;
        Minecraft mc = Minecraft.getInstance();
        CockpitHudPayload h = ClientCockpitBridge.hud();
        if (mc.player == null || !mc.player.isAlive() || mc.level == null || be(mc) == null) { leave(null); return; }
        if (ClientCockpit.seated() == null) { leave(null); return; }
        if (mc.screen != null || mc.player.isShiftKeyDown()) { leave(null); return; }
        if (h == null || h.pod() == CockpitHudPayload.NONE || !h.has(CockpitHudPayload.F_POWER)) leave("hud.wmd.podcam.lost");
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (pod == null) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom + (event.getScrollDeltaY() > 0 ? 1 : -1), 0, Balance.TPOD_ZOOMS.length - 1);
    }

    @SubscribeEvent
    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (pod == null || !event.usedConfiguredFov()) return;
        if (phase != 0) {
            double k = transK(Minecraft.getInstance());
            double e = phase == 1 ? k : 1.0 - k;
            event.setFOV(event.getFOV() * (1.0 - 0.45 * e * e));
            return;
        }
        event.setFOV(event.getFOV() / Balance.TPOD_ZOOMS[zoom]); lastFov = event.getFOV();
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (pod != null) event.setCanceled(true); }

    @SubscribeEvent
    static void body(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        if (pod != null && event.getEntity() == Minecraft.getInstance().player) event.setCanceled(true);
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientPodCam::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (pod == null || mc.options.hideGui || mc.level == null || mc.player == null || cam == null) return;
        if (phase != 0) {
            double k = transK(mc), e = phase == 1 ? k : 1.0 - k;
            if (e > 0.6) {
                int a = (int) (Mth.clamp((e - 0.6) / 0.4, 0.0, 1.0) * 230);
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x061209);
                for (int y = 0; y < g.guiHeight(); y += 3) g.fill(0, y, g.guiWidth(), y + 1, ((a / 4) << 24) | 0x6FE69A);
            }
            return;
        }
        CockpitHudPayload h = ClientCockpitBridge.hud();
        Font font = mc.font;
        int w = g.guiWidth(), hh = g.guiHeight(), cx = w / 2, cy = hh / 2;
        int frame = 0xE0101418, line = 0xFF7FD8FF, dim = 0xB07FD8FF;

        g.fill(0, 0, w, 18, frame);
        g.fill(0, hh - 30, w, hh, frame);
        int m = 26, k = 18;
        g.fill(m, 24, m + k, 25, line); g.fill(m, 24, m + 1, 24 + k, line);
        g.fill(w - m - k, 24, w - m, 25, line); g.fill(w - m - 1, 24, w - m, 24 + k, line);
        g.fill(m, hh - 37, m + k, hh - 36, line); g.fill(m, hh - 37 - k, m + 1, hh - 36, line);
        g.fill(w - m - k, hh - 37, w - m, hh - 36, line); g.fill(w - m - 1, hh - 37 - k, w - m, hh - 36, line);

        boolean lasing = h != null && h.lasing();
        int c = lasing ? (WmdUi.pulse(0.8F) > 0.3F ? 0xFFFF8A3A : 0xC0FF8A3A) : line;
        int r = 6;
        g.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, c); g.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, c);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, c); g.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, c);
        g.fill(cx - 70, cy, cx - 14, cy + 1, dim); g.fill(cx + 14, cy, cx + 70, cy + 1, dim);
        g.fill(cx, cy - 50, cx + 1, cy - 14, dim); g.fill(cx, cy + 14, cx + 1, cy + 50, dim);

        String z = "x" + (int) Balance.TPOD_ZOOMS[zoom];
        g.drawString(font, Component.translatable("hud.wmd.podcam.title"), 8, 5, WmdUi.TEXT, false);
        g.drawString(font, z, 8 + font.width(Component.translatable("hud.wmd.podcam.title")) + 10, 5, WmdUi.CYAN, false);
        if (ClientPodThermal.active()) {
            Component th = Component.translatable(ClientPodThermal.blocked() ? "hud.wmd.podcam.thermal_na" : "hud.wmd.podcam.thermal");
            g.drawString(font, th, 8 + font.width(Component.translatable("hud.wmd.podcam.title")) + 10 + font.width(z) + 10, 5, ClientPodThermal.blocked() ? WmdUi.TEXT_MUTED : 0xFFFFFFFF, false);
        }
        boolean mem = lasing && h.has(CockpitHudPayload.F_PODMEM);
        Component ls = Component.translatable(mem ? "hud.wmd.podcam.mem" : lasing ? "hud.wmd.podcam.lasing" : "hud.wmd.podcam.standby");
        g.drawString(font, ls, cx - font.width(ls) / 2, 5, mem ? (WmdUi.pulse(1.2F) > 0.5F ? 0xFFFFC14D : 0x80FFC14D) : lasing ? 0xFFFF8A3A : WmdUi.TEXT_MUTED, false);
        Component md = Component.translatable(h != null && h.has(CockpitHudPayload.F_AGMGPS) ? "hud.wmd.podcam.agm_gps" : "hud.wmd.podcam.agm_laser");
        g.drawString(font, md, w - 8 - font.width(md), 5, WmdUi.TEXT_MUTED, false);

        String rng = "--", pos = "";
        if (lasing) {
            Vec3 sp = new Vec3(h.px(), h.py(), h.pz());
            rng = String.format("%,d", Math.round(sp.distanceTo(cam.position())));
            pos = String.format("%d / %d / %d", Mth.floor(sp.x), Mth.floor(sp.y), Mth.floor(sp.z));
        }
        g.drawString(font, Component.translatable("hud.wmd.podcam.range", rng), 8, hh - 25, WmdUi.TEXT, false);
        if (!pos.isEmpty()) g.drawString(font, Component.translatable("hud.wmd.podcam.spot", pos), 8, hh - 13, WmdUi.TEXT_MUTED, false);
        Component hint = Component.translatable("hud.wmd.podcam.hint", ClientCockpit.DESIG.getTranslatedKeyMessage(), ClientCockpit.PODCAM.getTranslatedKeyMessage());
        g.drawString(font, hint, w - 8 - font.width(hint), hh - 19, WmdUi.TEXT_DIM, false);
        if (mc.level.getGameTime() - enteredAt < 4) g.fill(0, 0, w, hh, 0xFF000000);
    }

    public static void clearStatics() { pod = null; cam = null; phase = 0; ClientPodPage.clearStatics(); }
}
