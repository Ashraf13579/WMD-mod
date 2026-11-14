package com.ashraf.wmd;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class ApHud {

    private ApHud() {}

    private static final String[] MODE = { "off", "hold", "route", "orbit", "home", "parked" };
    private static final String[] PHASE = { "return", "circuit", "final", "flare", "rollout", "goaround", "circle" };

    static void render(GuiGraphics g, Font font, BlockPos cockpit) {
        ApHudPayload a = ClientAutopilotBridge.hud(cockpit);
        if (a == null || !AirGate.on()) return;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
        int y = 30;
        boolean on = a.mode() != Autopilot.OFF;
        if (on) {
            int bx = cx - 150;
            g.fill(bx, y, bx + 18, y + 1, JetHud.GREEN); g.fill(bx, y + 11, bx + 18, y + 12, JetHud.GREEN);
            g.fill(bx, y, bx + 1, y + 12, JetHud.GREEN); g.fill(bx + 17, y, bx + 18, y + 12, JetHud.GREEN);
            g.drawString(font, "AP", bx + 3, y + 2, JetHud.GREEN, false);
            g.drawString(font, line(a), bx + 23, y + 2, JetHud.GREEN, false);
            if (a.has(ApHudPayload.F_FLOOR)) g.drawString(font, Component.translatable("hud.wmd.ap.floor"), bx + 23, y + 13, JetHud.AMBER, false);
            if (a.target() && a.mode() != Autopilot.HOLD && a.mode() != Autopilot.PARKED) mark(g, font, w, h, a);
        }
        if (a.reason() != Autopilot.R_NONE && (System.currentTimeMillis() / 300) % 2 == 0) {
            Component r = Component.translatable("hud.wmd.ap.reason" + a.reason());
            g.drawString(font, r, cx - font.width(r) / 2, 70, a.reason() == Autopilot.R_LANDED ? JetHud.GREEN : JetHud.AMBER, false);
        }
    }

    static void renderGunner(GuiGraphics g, Font font, BlockPos cockpit) {
        ApHudPayload a = ClientAutopilotBridge.hud(cockpit);
        if (a == null || !AirGate.on()) return;
        int col = 0xFF7FD8FF;
        if (a.mode() != Autopilot.OFF) g.drawString(font, "AP " + line(a), 52, 26, col, false);
        if (a.reason() != Autopilot.R_NONE && (System.currentTimeMillis() / 300) % 2 == 0)
            g.drawString(font, Component.translatable("hud.wmd.ap.reason" + a.reason()), 52, 38, a.reason() == Autopilot.R_LANDED ? col : JetHud.AMBER, false);
    }

    private static String km(float d) { return d >= 1000.0F ? String.format("%.1fk", d / 1000.0F) : Integer.toString(Math.round(d)); }

    static String line(ApHudPayload a) {
        String m = Component.translatable("hud.wmd.ap.mode_" + MODE[Mth.clamp(a.mode(), 0, 5)]).getString();
        return switch (a.mode()) {
            case Autopilot.HOLD -> m + "  HDG " + Math.round(a.hdg()) + "  ALT " + Math.round(a.alt()) + "  " + Math.round(a.spd());
            case Autopilot.ROUTE -> m + "  WP " + (a.leg() + 1) + "/" + a.legs() + "  " + km(a.dist()) + "  ALT " + Math.round(a.alt()) + "  " + Math.round(a.spd());
            case Autopilot.ORBIT -> m + "  R " + Math.round(a.radius()) + (a.has(ApHudPayload.F_RIGHT) ? " R" : " L") + "  ALT " + Math.round(a.alt()) + "  " + Math.round(a.spd());
            case Autopilot.HOME -> m + "  " + Component.translatable("hud.wmd.ap.phase_" + PHASE[Mth.clamp(a.phase(), 0, 6)]).getString() + "  " + km(a.dist());
            default -> m;
        };
    }

    private static void mark(GuiGraphics g, Font font, int w, int h, ApHudPayload a) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = new Vec3(a.tx(), a.ty(), a.tz()).subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        Vec3 f = new Vec3(lf.x(), lf.y(), lf.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), left = new Vec3(lv.x(), lv.y(), lv.z());
        double z = v.dot(f);
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        int col = JetHud.GREEN;
        int sx, sy;
        boolean off;
        if (z <= 0.5) { sx = v.dot(left) > 0 ? 12 : w - 12; sy = h / 2; off = true; }
        else {
            sx = (int) Math.round(w * 0.5 - v.dot(left) / z / th * hh); sy = (int) Math.round(hh - v.dot(up) / z / th * hh);
            off = sx < 10 || sx > w - 10 || sy < 10 || sy > h - 10;
        }
        if (off) {
            int ax = Mth.clamp(sx, 12, w - 12), ay = Mth.clamp(sy, 12, h - 50);
            g.drawString(font, ax < w / 2 ? "◀" : "▶", ax - 3, ay - 4, col, false);
            return;
        }
        int r = 6;
        if (a.mode() == Autopilot.ORBIT) {
            for (int k = 0; k < 32; k++) { double t = k * Math.PI * 2.0 / 32.0; g.fill(sx + (int) Math.round(Math.cos(t) * r), sy + (int) Math.round(Math.sin(t) * r), sx + (int) Math.round(Math.cos(t) * r) + 1, sy + (int) Math.round(Math.sin(t) * r) + 1, col); }
            g.fill(sx - 3, sy, sx + 4, sy + 1, col); g.fill(sx, sy - 3, sx + 1, sy + 4, col);
        } else {
            for (int k = 0; k <= r; k++) {
                g.fill(sx - r + k, sy - k, sx - r + k + 1, sy - k + 1, col); g.fill(sx + r - k, sy - k, sx + r - k + 1, sy - k + 1, col);
                g.fill(sx - r + k, sy + k, sx - r + k + 1, sy + k + 1, col); g.fill(sx + r - k, sy + k, sx + r - k + 1, sy + k + 1, col);
            }
            if (a.mode() == Autopilot.ROUTE) g.drawString(font, Integer.toString(a.leg() + 1), sx - 2, sy - r - 10, JetHud.GREEN_DIM, false);
        }
        g.drawString(font, km(a.dist()), sx + r + 4, sy - 4, JetHud.GREEN_DIM, false);
    }
}
