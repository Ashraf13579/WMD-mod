package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientMissileCam {

    private ClientMissileCam() {}

    private static final int FREEZE_TICKS = 12;

    private static final double FREEZE_BLOCKS = 40.0;

    private static float savedRain = -1.0F, savedThunder;

    private static final int LINGER_TICKS = 40;

    private static long watching;

    private static final long PAD = -1L;
    private static final int PAD_TICKS = 1200;
    private static Vec3 padEye = Vec3.ZERO;
    private static int padTx, padTz;

    private static net.minecraft.core.BlockPos padPos;
    private static long padFrom;
    private static Marker cam;
    private static long lingerFrom = -1;
    private static Vec3 lastPos = Vec3.ZERO;
    private static float lastYaw, lastPitch;
    private static boolean hidGui;

    public static long watching() { return watching; }

    static Vec3 camPos() { return lastPos; }
    static float camYaw() { return lastYaw; }
    static float camPitch() { return lastPitch; }

    public static void start(long id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Flight f = ClientFlights.FLIGHTS.get(id);
        if (f == null) { mc.player.displayClientMessage(Component.translatable("message.wmd.cc.cam.no_flight", id), true); return; }
        if (watching != 0) restore(mc);
        watching = id;
        lingerFrom = -1;
        cam = new Marker(EntityType.MARKER, mc.level);
        lastPos = mc.player.getEyePosition();
        lastYaw = mc.player.getYRot();
        lastPitch = mc.player.getXRot();
        place(mc, mc.level.getGameTime());
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new View());
    }

    public static void startPad(CcCamPadPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (watching != 0) restore(mc);
        watching = PAD;
        lingerFrom = -1;
        padEye = new Vec3(p.x(), p.y(), p.z());
        padTx = p.targetX();
        padTz = p.targetZ();
        padPos = net.minecraft.core.BlockPos.of(p.pad());
        padFrom = mc.level.getGameTime();
        lastPos = padEye;
        lastYaw = p.yaw();
        lastPitch = 10.0F;
        cam = new Marker(EntityType.MARKER, mc.level);
        place(mc, mc.level.getGameTime());
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new View());
    }

    public static void stop() {
        if (watching == 0) return;
        Minecraft mc = Minecraft.getInstance();
        restore(mc);
        if (mc.screen instanceof View) mc.setScreen(null);
    }

    private static void restore(Minecraft mc) {
        if (savedRain >= 0.0F && mc.level != null) { mc.level.setRainLevel(savedRain); mc.level.setThunderLevel(savedThunder); }
        savedRain = -1.0F;
        watching = 0;
        cam = null;
        lingerFrom = -1;
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    private static void place(Minecraft mc, double t) {
        Flight f = watching == PAD ? null : ClientFlights.FLIGHTS.get(watching);
        if (watching == PAD) {
            lastPos = padEye;

            if (mc.level != null && padPos != null && mc.level.getBlockEntity(padPos) instanceof LaunchTerminalBlockEntity term
                    && term.getStage() == LaunchTerminalBlockEntity.Stage.LAUNCHING) {
                float up = term.missileOffset((float) (t - Math.floor(t)));
                lastPos = padEye.add(0.0, up, 0.0);
                lastPitch = 10.0F - 75.0F * Math.min(1.0F, up / 60.0F);
            }
        } else if (f != null && t < f.endTime() && t >= f.start()) {

            double vEnd = f.position(f.endTime()).distanceTo(f.position(f.endTime() - 1.0));
            double freeze = Math.max(2.0, Math.min(FREEZE_TICKS, FREEZE_BLOCKS / Math.max(0.1, vEnd)));
            double ct = Math.min(t, f.endTime() - freeze);
            Vec3 p = f.position(ct);
            Vec3 dir = f.position(ct + 1.0).subtract(p);
            if (t >= f.endTime() - freeze) dir = f.target().subtract(p);
            if (dir.lengthSqr() > 1.0E-8) {
                dir = dir.normalize();
                lastPos = p.add(dir.scale(MissileParts.lengthOf(f.build()) * 0.5 + 0.6));
                lastYaw = (float) (Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                lastPitch = (float) (-Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y)))));
            }
        } else if (lingerFrom < 0 && mc.level != null) {
            lingerFrom = mc.level.getGameTime();
        }
        if (cam == null) return;
        cam.setPos(lastPos.x, lastPos.y, lastPos.z);
        cam.xo = cam.xOld = lastPos.x;
        cam.yo = cam.yOld = lastPos.y;
        cam.zo = cam.zOld = lastPos.z;
        cam.setYRot(lastYaw);
        cam.setXRot(lastPitch);
        cam.yRotO = lastYaw;
        cam.xRotO = lastPitch;
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (watching == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || cam == null) { watching = 0; cam = null; return; }

        if (savedRain < 0.0F) { savedRain = mc.level.getRainLevel(1.0F); savedThunder = mc.level.getThunderLevel(1.0F); }
        mc.level.setRainLevel(0.0F);
        mc.level.setThunderLevel(0.0F);
        place(mc, mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (watching == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { watching = 0; cam = null; lingerFrom = -1; if (hidGui) mc.options.hideGui = false; hidGui = false; return; }
        if (cam != null && cam.level() != mc.level) { stop(); return; }
        if (!mc.player.isAlive() || !(mc.screen instanceof View)) { stop(); return; }
        if (lingerFrom >= 0 && mc.level.getGameTime() - lingerFrom > LINGER_TICKS) stop();
        if (watching == PAD && mc.level.getGameTime() - padFrom > PAD_TICKS) stop();
    }

    static final class View extends Screen {

        View() { super(Component.translatable("gui.wmd.cc.cam.title")); }

        @Override public boolean isPauseScreen() { return false; }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (minecraft != null && minecraft.options.keyShift.matches(keyCode, scanCode)) { stop(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return ClientTv.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            ClientTv.mouseReleased(button);
            return super.mouseReleased(mx, my, button);
        }

        @Override
        public void onClose() { stop(); if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            if (minecraft == null || minecraft.level == null) return;
            ClientTv.render(g, font, width, height, mx, my, watching == PAD ? null : ClientFlights.FLIGHTS.get(watching));
            if (!ClientConfig.missileCamHud()) { backHint(g); return; }
            Flight f = watching == PAD ? null : ClientFlights.FLIGHTS.get(watching);
            double t = minecraft.level.getGameTime() + pt;
            int cx = width / 2, cy = height / 2;

            int ret = WmdUi.TEXT;
            g.fill(cx - 12, cy, cx - 4, cy + 1, ret);
            g.fill(cx + 4, cy, cx + 12, cy + 1, ret);
            g.fill(cx, cy - 12, cx + 1, cy - 4, ret);
            g.fill(cx, cy + 4, cx + 1, cy + 12, ret);
            boolean seekerView = f != null && f.build() != null && MissileBuild.SEEK_LASER.equals(f.build().seeker()) && !f.build().camPod();
            Component head = Component.translatable(seekerView ? "gui.wmd.cc.cam.hud_title_seeker" : "gui.wmd.cc.cam.hud_title", watching);
            Component l1, l2 = null;
            int c1 = WmdUi.TEXT;
            boolean live = f != null && t < f.endTime();
            if (watching == PAD) {
                head = Component.translatable("gui.wmd.cc.cam.hud_pad_title");
                boolean blink = ((long) (t / 10.0)) % 2 == 0;
                l1 = Component.translatable("gui.wmd.cc.cam.hud_pad");
                c1 = blink ? WmdUi.TEXT : WmdUi.TEXT_MUTED;
                l2 = Component.translatable("gui.wmd.cc.cam.hud_target", padTx, padTz);
                live = true;
            } else if (live) {
                Vec3 p = f.position(t), q = f.position(t + 1.0);
                double speed = q.subtract(p).length() * 20.0;
                l1 = Component.translatable("gui.wmd.cc.cam.hud_data", (int) Math.round(p.y), (int) Math.round(speed),
                        OrbitMath.mmss(Math.max(0L, (long) ((f.endTime() - t) * 50.0))));
                l2 = Component.translatable("gui.wmd.cc.cam.hud_target", f.tx(), f.tz());
            } else {
                boolean blink = ((long) (t / 6.0)) % 2 == 0;
                l1 = Component.translatable(f == null ? "gui.wmd.cc.cam.hud_lost" : "gui.wmd.cc.cam.hud_impact");
                c1 = blink ? WmdUi.BAD : WmdUi.TEXT;
            }
            int bw = Math.max(font.width(head) + 15, Math.max(font.width(l1), l2 != null ? font.width(l2) : 0)) + 12;
            bw = Math.min(bw, width - 8);
            int bh = l2 != null ? 44 : 32;
            card(g, 4, 4, bw, bh);
            WmdUi.chip(g, font, 8, 7, head, WmdUi.K_BAD, live);
            WmdUi.drawFit(g, font, l1, 10, 21, bw - 12, c1);
            if (l2 != null) WmdUi.drawFit(g, font, l2, 10, 32, bw - 12, WmdUi.TEXT_MUTED);
            backHint(g);
        }

        private void backHint(GuiGraphics g) {
            if (watching != PAD)
                g.drawString(font, Component.translatable("gui.wmd.cc.cam.terrain", ClientCamTerrain.cached(), ClientCamTerrain.drawn()), 6, height - 12, WmdUi.TEXT_DIM, false);
            Component back = Component.translatable("gui.wmd.cc.cam.hud_back");
            int hw = font.width(back), cx = width / 2;
            card(g, cx - hw / 2 - 6, height - 24, hw + 12, 15);
            g.drawString(font, back, cx - hw / 2, height - 20, WmdUi.TEXT_MUTED, false);
        }

        private static void card(GuiGraphics g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, 0xB80F1318);
            g.fill(x, y, x + w, y + 1, WmdUi.BLUE);
            g.fill(x, y + h - 1, x + w, y + h, 0x803A4552);
            g.fill(x, y + 1, x + 1, y + h - 1, 0x803A4552);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x803A4552);
        }
    }
}
