package com.ashraf.wmd;

import java.util.List;

import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class JetHud {

    private JetHud() {}

    static final int GREEN = 0xFF58F08A, GREEN_DIM = 0x9958F08A, AMBER = 0xFFF2C08A, RED = 0xFFFF6A5A;

    private static final float PX_DEG = 3.2F;

    static void render(GuiGraphics g, Font font, CockpitHudPayload d, List<RwrPayload.Line> rwr, boolean minimal, float thr, boolean ab, float eject,
                       float slide, float hints, float pt) {
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        boolean power = d.has(CockpitHudPayload.F_POWER);
        throttle(g, font, 6, h - 60 - Math.round((1.0F - WmdUi.easeOut(slide)) * 70), d, thr, ab, power);
        if (eject > 0.0F) ejectBar(g, font, cx, h - 96, eject);
        if (hints > 0.0F) hints(g, font, cx, h - 38, hints);
        if (!power) return;
        ladder(g, font, cx, cy, d.pitch(), d.roll(), minimal);
        if (!minimal) {
            ShipHud.tape(g, font, cx, 4 - Math.round((1.0F - WmdUi.easeOut(slide)) * 30), (d.heading() % 360 + 360) % 360);
            fpm(g, cx, cy, d);
            if (d.gunRounds() >= 0) gunCross(g, cx, cy);
        }
        box(g, font, cx - 128, cy - 9, Component.translatable("hud.wmd.jet.speed"), String.format("%.0f", d.speed()), true);
        box(g, font, cx + 92, cy - 9, Component.translatable("hud.wmd.jet.alt"), String.format("%.0f", d.alt()), false);
        if (!minimal) {
            g.drawString(font, Component.translatable("hud.wmd.jet.agl", Math.round(d.agl())), cx + 92, cy + 12, d.agl() < 20.0F ? AMBER : GREEN_DIM, false);
            String vs = String.format("%+.0f", d.vy());
            g.drawString(font, vs, cx - 92 - font.width(vs), cy + 12, GREEN_DIM, false);
            stations(g, font, cx, h - 22, d);
            rwrScope(g, font, w - 78, h - 82, rwr, d.heading());
        }
        if (d.locked()) lockBox(g, font, w, h, new Vec3(d.lx(), d.ly(), d.lz()), d.lockDist(), d.has(CockpitHudPayload.F_LOCKJAM));
        if (d.lasing() && !ClientPodCam.active()) podBox(g, font, w, h, new Vec3(d.px(), d.py(), d.pz()));
        if (d.bombAim() && !ClientPodCam.active()) {
            if (d.stnKind() == AirStores.GUN) gunAim(g, font, w, h, new Vec3(d.ix(), d.iy(), d.iz()), d);
            else if (d.stnKind() == AirStores.ROCKET) {
                rocketAim(g, font, w, h, new Vec3(d.ix(), d.iy(), d.iz()));
                if (d.lead()) leadMark(g, font, w, h, new Vec3(d.qx(), d.qy(), d.qz()));
            }
            else bombAim(g, font, w, h, new Vec3(d.ix(), d.iy(), d.iz()));
        }
        gear(g, font, cx - 128, cy + 26, d.gear());
        systems(g, font, cx - 128, cy + 48, d);
        if (d.beacon() && !minimal) meatball(g, font, cx + 128 + 6, cy - 40, d.bDev(), d.bDist(), d.bSide());
        if (d.has(CockpitHudPayload.F_JAM)) { Component jm = Component.translatable("hud.wmd.jet.jam"); g.drawString(font, jm, cx + 92, cy - 22, AMBER, false); }
        if (ClientCockpit.pullUp(d) && WmdUi.pulse(0.5F) > 0.4F) {
            Component pu = Component.translatable("hud.wmd.jet.pull_up");
            g.drawString(font, pu, cx - font.width(pu) / 2, cy + 34, RED, false);
        }
    }

    private static void ladder(GuiGraphics g, Font font, int cx, int cy, float pitch, float roll, boolean minimal) {
        g.enableScissor(cx - 90, cy - 70, cx + 90, cy + 70);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-roll));
        for (int a = -90; a <= 90; a += 10) {
            float y = (pitch - a) * PX_DEG;
            if (Math.abs(y) > 90.0F) continue;
            int yi = Math.round(y);
            if (a == 0) { g.fill(-80, yi, -14, yi + 1, GREEN); g.fill(14, yi, 80, yi + 1, GREEN); continue; }
            if (minimal) continue;
            boolean above = a > 0;
            for (int sx = -1; sx <= 1; sx += 2) {
                int x0 = sx * 18, x1 = sx * 44;
                if (above) g.fill(Math.min(x0, x1), yi, Math.max(x0, x1), yi + 1, GREEN);
                else for (int x = Math.min(x0, x1); x < Math.max(x0, x1); x += 6) g.fill(x, yi, x + 3, yi + 1, GREEN);
                g.fill(x1, yi, x1 + 1, yi + (above ? 4 : -4), GREEN);
            }
            String lab = String.valueOf(Math.abs(a));
            g.drawString(font, lab, 48, yi - 4, GREEN_DIM, false);
            g.drawString(font, lab, -48 - font.width(lab), yi - 4, GREEN_DIM, false);
        }
        g.pose().popPose();
        g.disableScissor();

        g.fill(cx - 10, cy, cx - 4, cy + 1, GREEN);
        g.fill(cx + 4, cy, cx + 10, cy + 1, GREEN);
        g.fill(cx - 4, cy, cx - 3, cy + 3, GREEN);
        g.fill(cx + 3, cy, cx + 4, cy + 3, GREEN);
    }

    private static void fpm(GuiGraphics g, int cx, int cy, CockpitHudPayload d) {
        if (d.speed() < 1.0F && Math.abs(d.vy()) < 1.0F) return;
        float fpa = (float) Math.toDegrees(Math.atan2(d.vy(), Math.max(0.1F, d.speed())));
        int y = cy + Math.round(Mth.clamp((d.pitch() - fpa) * PX_DEG, -60.0F, 60.0F));
        g.fill(cx - 2, y - 3, cx + 3, y - 2, GREEN); g.fill(cx - 2, y + 2, cx + 3, y + 3, GREEN);
        g.fill(cx - 3, y - 2, cx - 2, y + 2, GREEN); g.fill(cx + 2, y - 2, cx + 3, y + 2, GREEN);
        g.fill(cx - 9, y, cx - 3, y + 1, GREEN); g.fill(cx + 3, y, cx + 9, y + 1, GREEN); g.fill(cx, y - 7, cx + 1, y - 3, GREEN);
    }

    private static void gunCross(GuiGraphics g, int cx, int cy) {
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8.0;
            int x = cx + (int) Math.round(Math.cos(a) * 7), y = cy + 4 + (int) Math.round(Math.sin(a) * 7);
            g.fill(x, y, x + 1, y + 1, GREEN_DIM);
        }
        g.fill(cx, cy + 4, cx + 1, cy + 5, GREEN);
    }

    private static void box(GuiGraphics g, Font font, int x, int y, Component label, String value, boolean left) {
        int bw = 36, bh = 16;
        g.fill(x, y, x + bw, y + 1, GREEN); g.fill(x, y + bh, x + bw, y + bh + 1, GREEN);
        g.fill(x, y, x + 1, y + bh + 1, GREEN); g.fill(x + bw, y, x + bw + 1, y + bh + 1, GREEN);
        g.drawString(font, value, x + bw / 2 - font.width(value) / 2, y + 4, GREEN, false);
        g.drawString(font, label, left ? x : x + bw - font.width(label), y - 10, GREEN_DIM, false);
    }

    private static void lockBox(GuiGraphics g, Font font, int w, int h, Vec3 target, float dist, boolean jammed) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = target.subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        Vec3 f = new Vec3(lf.x(), lf.y(), lf.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), left = new Vec3(lv.x(), lv.y(), lv.z());
        double z = v.dot(f);
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        boolean blink = WmdUi.pulse(0.8F) > 0.25F;
        if (z > 0.5) {
            int sx = (int) Math.round(w * 0.5 - v.dot(left) / z / th * hh), sy = (int) Math.round(hh - v.dot(up) / z / th * hh);
            if (sx > 8 && sx < w - 8 && sy > 8 && sy < h - 8) {
                int r = 7;
                int col = blink ? GREEN : GREEN_DIM;
                g.fill(sx - r, sy - r, sx + r + 1, sy - r + 1, col); g.fill(sx - r, sy + r, sx + r + 1, sy + r + 1, col);
                g.fill(sx - r, sy - r, sx - r + 1, sy + r + 1, col); g.fill(sx + r, sy - r, sx + r + 1, sy + r + 1, col);
                String rg = Math.round(dist) + "";
                g.drawString(font, rg, sx + r + 3, sy - 4, GREEN, false);
                if (jammed) {
                    long t = System.currentTimeMillis() / 60L;
                    for (int k = 0; k < 14; k++) { long hsh = (t * 31 + k * 7919) * 2654435761L; int nx = sx - r + (int) Math.floorMod(hsh >> 8, 2 * r + 1), ny = sy - r + (int) Math.floorMod(hsh >> 20, 2 * r + 1); g.fill(nx, ny, nx + 1, ny + 1, AMBER); }
                }
                Component lk = Component.translatable(jammed ? "hud.wmd.jet.lock_jam" : "hud.wmd.jet.lock");
                g.drawString(font, lk, sx - font.width(lk) / 2, sy - r - 10, GREEN, false);
                return;
            }
        }

        double ax = -v.dot(left), ay = v.dot(up);
        if (z < 0) { ax = -ax; ay = -ay; }
        double a = Math.atan2(-ay, ax);
        int ex = (int) Math.round(w * 0.5 + Math.cos(a) * 80), ey = (int) Math.round(hh + Math.sin(a) * 80);
        for (int k = 0; k < 4; k++) g.fill(ex - k, ey - k, ex + k + 1, ey + k + 1, blink ? GREEN : GREEN_DIM);
    }

    private static void gear(GuiGraphics g, Font font, int x, int y, int st) {
        if (st < 0) return;
        int s = st % 10;
        boolean wow = st >= 10;
        Component t = Component.translatable(s == 2 ? "hud.wmd.jet.gear_down" : s == 1 ? "hud.wmd.jet.gear_transit" : "hud.wmd.jet.gear_up");
        int col = s == 2 ? GREEN : s == 1 ? (WmdUi.pulse(0.6F) > 0.3F ? AMBER : GREEN_DIM) : GREEN_DIM;
        g.drawString(font, t, x, y, col, false);
        if (wow) g.drawString(font, Component.translatable("hud.wmd.jet.wow"), x, y + 10, GREEN_DIM, false);
    }

    private static void systems(GuiGraphics g, Font font, int x, int y, CockpitHudPayload d) {
        if (d.hover()) { g.drawString(font, Component.translatable("hud.wmd.jet.hover"), x, y, GREEN, false); y += 10; }
        if (d.chute() == 2) { g.drawString(font, Component.translatable("hud.wmd.jet.chute_out"), x, y, AMBER, false); y += 10; }
        else if (d.chute() == 3) { g.drawString(font, Component.translatable("hud.wmd.jet.chute_spent"), x, y, GREEN_DIM, false); y += 10; }
        if (d.rotorBlades() > 0) { g.drawString(font, Component.translatable("hud.wmd.jet.rotor", d.rotorBlades(), d.rotorLength(), d.rotorSpool()), x, y, GREEN_DIM, false); y += 10; }
        int dmg = d.engineDamage();
        if (dmg > 0) {
            int col = dmg == 1 ? AMBER : WmdUi.pulse(0.5F) > 0.3F ? RED : AMBER;
            g.drawString(font, Component.translatable(dmg == 1 ? "hud.wmd.jet.eng_smoke" : dmg == 2 ? "hud.wmd.jet.eng_fire" : "hud.wmd.jet.eng_out", d.damagedEngines()), x, y, col, false);
            y += 10;
        }
        if (d.hook() > 0) { g.drawString(font, Component.translatable(d.hook() == 2 ? "hud.wmd.jet.hook_loaded" : "hud.wmd.jet.hook_down"), x, y, d.hook() == 2 ? AMBER : GREEN_DIM, false); y += 10; }
        if (d.ramp() > 0) { g.drawString(font, Component.translatable("hud.wmd.jet.ramp_" + d.ramp()), x, y, d.ramp() == 4 ? AMBER : GREEN_DIM, false); y += 10; }
        if (d.refuel() > 0) {
            int r = d.refuel();
            boolean flow = r == 2 || r == 4;
            g.drawString(font, Component.translatable("hud.wmd.jet.refuel" + r), x, y, flow ? GREEN : GREEN_DIM, false);
            y += 10;
        }
        if (!Float.isNaN(d.fuel())) g.drawString(font, Component.translatable("hud.wmd.jet.fuel", Math.round(d.fuel() * 100.0F)), x, y, d.fuel() < 0.2F ? AMBER : GREEN_DIM, false);
    }

    private static void bombAim(GuiGraphics g, Font font, int w, int h, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = target.subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        Vec3 f = new Vec3(lf.x(), lf.y(), lf.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), left = new Vec3(lv.x(), lv.y(), lv.z());
        double z = v.dot(f);
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        int col = GREEN;
        if (z <= 0.5) { g.drawString(font, "▼ " + Component.translatable("hud.wmd.jet.bomb_low").getString(), w / 2 - 30, h - 46, col, false); return; }
        int sx = (int) Math.round(w * 0.5 - v.dot(left) / z / th * hh), sy = (int) Math.round(hh - v.dot(up) / z / th * hh);
        if (sx < 10 || sx > w - 10 || sy < 10 || sy > h - 10) {
            int ax = Mth.clamp(sx, 12, w - 12), ay = Mth.clamp(sy, 12, h - 50);
            g.drawString(font, "▼", ax - 3, ay - 4, col, false);
            return;
        }
        int r = 7;
        for (int a = 0; a < 32; a++) {
            double t = a * Math.PI * 2.0 / 32.0;
            int px = sx + (int) Math.round(Math.cos(t) * r), py = sy + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
        g.fill(sx - 1, sy - 1, sx + 1, sy + 1, col);
        for (int y = h / 2 + 12; y < sy - r - 2; y += 4) g.fill(w / 2, y, w / 2 + 1, y + 2, GREEN_DIM);
        String rg = Component.translatable("hud.wmd.jet.bomb").getString() + " " + Math.round(v.length());
        g.drawString(font, rg, sx + r + 3, sy - 4, col, false);
    }

    private static int[] screen(int w, int h, Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = p.subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        double z = v.dot(new Vec3(lf.x(), lf.y(), lf.z()));
        if (z <= 0.5) return null;
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        return new int[] { (int) Math.round(w * 0.5 - v.dot(new Vec3(lv.x(), lv.y(), lv.z())) / z / th * hh), (int) Math.round(hh - v.dot(new Vec3(uv.x(), uv.y(), uv.z())) / z / th * hh) };
    }

    private static void gunAim(GuiGraphics g, Font font, int w, int h, Vec3 at, CockpitHudPayload d) {
        int[] s = screen(w, h, at);
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        double range = at.distanceTo(mc.gameRenderer.getMainCamera().getPosition());
        boolean hit = d.has(CockpitHudPayload.F_PIP_HIT), far = range > Balance.GPOD_RANGE - 2.0 && !hit;
        int col = hit ? GREEN : far ? GREEN_DIM : GREEN;
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
        int r = Math.max(7, (int) Math.round(Math.tan(Math.toRadians(Balance.GPOD_CONE)) / th * h * 0.5));
        int sx = Mth.clamp(s[0], 12, w - 12), sy = Mth.clamp(s[1], 12, h - 50);
        for (int a = 0; a < 36; a++) {
            if (far && (a / 3) % 2 == 1) continue;
            double t = a * Math.PI * 2.0 / 36.0;
            int px = sx + (int) Math.round(Math.cos(t) * r), py = sy + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
        g.fill(sx - 1, sy - 1, sx + 1, sy + 1, col);
        g.drawString(font, Component.translatable("hud.wmd.jet.gun_pip").getString() + " " + Math.round(range), sx + r + 4, sy - 4, col, false);
        if (hit) { Component t = Component.translatable("hud.wmd.jet.pip_hit"); g.drawString(font, t, sx - font.width(t) / 2, sy + r + 4, GREEN, false); }
    }

    private static void leadMark(GuiGraphics g, Font font, int w, int h, Vec3 at) {
        int[] s = screen(w, h, at);
        if (s == null || s[0] < 10 || s[0] > w - 10 || s[1] < 10 || s[1] > h - 10) return;
        int sx = s[0], sy = s[1];
        for (int i = 0; i <= 5; i++) {
            g.fill(sx - 5 + i, sy - i, sx - 4 + i, sy - i + 1, AMBER); g.fill(sx + 5 - i, sy - i, sx + 6 - i, sy - i + 1, AMBER);
            g.fill(sx - 5 + i, sy + i, sx - 4 + i, sy + i + 1, AMBER); g.fill(sx + 5 - i, sy + i, sx + 6 - i, sy + i + 1, AMBER);
        }
        g.drawString(font, Component.translatable("hud.wmd.jet.lead"), sx + 8, sy + 4, AMBER, false);
    }

    private static void rocketAim(GuiGraphics g, Font font, int w, int h, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = target.subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        Vec3 f = new Vec3(lf.x(), lf.y(), lf.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), left = new Vec3(lv.x(), lv.y(), lv.z());
        double z = v.dot(f), range = v.length();
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        boolean in = range >= Balance.RPOD_RANGE_MIN && range <= Balance.RPOD_RANGE_MAX;
        int col = in ? GREEN : GREEN_DIM;
        if (z <= 0.5) { g.drawString(font, "▼ " + Component.translatable("hud.wmd.jet.rkt_low").getString(), w / 2 - 30, h - 46, col, false); return; }
        int sx = (int) Math.round(w * 0.5 - v.dot(left) / z / th * hh), sy = (int) Math.round(hh - v.dot(up) / z / th * hh);
        if (sx < 10 || sx > w - 10 || sy < 10 || sy > h - 10) {
            g.drawString(font, "▼", Mth.clamp(sx, 12, w - 12) - 3, Mth.clamp(sy, 12, h - 50) - 4, col, false);
            return;
        }
        int r = 9;
        for (int a = 0; a < 40; a++) {
            if (!in && (a / 3) % 2 == 1) continue;
            double t = a * Math.PI * 2.0 / 40.0;
            int px = sx + (int) Math.round(Math.cos(t) * r), py = sy + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
        g.fill(sx - 1, sy - 1, sx + 1, sy + 1, col);
        double k = Mth.clamp(range / 1000.0, 0.0, 1.0);
        for (int a = 0; a < (int) Math.round(48 * k); a++) {
            double t = -Math.PI / 2.0 + a * Math.PI * 2.0 / 48.0;
            int px = sx + (int) Math.round(Math.cos(t) * (r + 3)), py = sy + (int) Math.round(Math.sin(t) * (r + 3));
            g.fill(px, py, px + 2, py + 2, col);
        }
        for (int y = h / 2 + 12; y < sy - r - 4; y += 6) g.fill(w / 2, y, w / 2 + 1, y + 2, GREEN_DIM);
        g.drawString(font, Component.translatable("hud.wmd.jet.rkt").getString() + " " + Math.round(range), sx + r + 6, sy - 4, col, false);
        if (in) { Component t = Component.translatable("hud.wmd.jet.in_range"); g.drawString(font, t, sx - font.width(t) / 2, sy + r + 6, GREEN, false); }
    }

    private static void podBox(GuiGraphics g, Font font, int w, int h, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = target.subtract(cam.getPosition());
        org.joml.Vector3f lf = cam.getLookVector(), uv = cam.getUpVector(), lv = cam.getLeftVector();
        Vec3 f = new Vec3(lf.x(), lf.y(), lf.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), left = new Vec3(lv.x(), lv.y(), lv.z());
        double z = v.dot(f);
        if (z <= 0.5) return;
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5), hh = h * 0.5;
        int sx = (int) Math.round(w * 0.5 - v.dot(left) / z / th * hh), sy = (int) Math.round(hh - v.dot(up) / z / th * hh);
        if (sx < 8 || sx > w - 8 || sy < 8 || sy > h - 8) return;
        int col = 0xFFFF8A3A;
        for (int s = -6; s <= 6; s++) { int o = 6 - Math.abs(s); g.fill(sx + s, sy - o, sx + s + 1, sy - o + 1, col); g.fill(sx + s, sy + o, sx + s + 1, sy + o + 1, col); }
        String rg = Component.translatable("hud.wmd.jet.lsr").getString() + " " + Math.round(v.length());
        g.drawString(font, rg, sx + 9, sy - 4, col, false);
    }

    private static void meatball(GuiGraphics g, Font font, int x, int y, float dev, float dist, float side) {
        int hgt = 60, mid = y + hgt / 2;
        g.fill(x, y, x + 1, y + hgt, GREEN_DIM);
        g.fill(x - 8, mid, x - 2, mid + 1, GREEN); g.fill(x + 3, mid, x + 9, mid + 1, GREEN);
        int by = mid - Math.round(Mth.clamp(dev, -2.0F, 2.0F) / 2.0F * (hgt / 2.0F));
        int col = Math.abs(dev) < 0.5F ? GREEN : Math.abs(dev) < 1.2F ? AMBER : RED;
        g.fill(x - 3, by - 3, x + 4, by + 4, col);
        g.drawString(font, Component.translatable("hud.wmd.jet.ball"), x - 10, y - 11, GREEN_DIM, false);
        String s = Math.round(dist) + (Math.abs(side) > 2.0F ? (side > 0 ? " R" : " L") : "");
        g.drawString(font, s, x - 10, y + hgt + 3, Math.abs(side) > 6.0F ? AMBER : GREEN_DIM, false);
    }

    private static void rwrScope(GuiGraphics g, Font font, int x, int y, List<RwrPayload.Line> lines, float heading) {
        int s = 72, c = s / 2, r = 28;
        WmdUi.panel(g, x, y, s, s);
        for (int k = 0; k < 48; k++) {
            double a = Math.PI * 2 * k / 48.0;
            g.fill(x + c + (int) Math.round(Math.cos(a) * r), y + c + (int) Math.round(Math.sin(a) * r), x + c + (int) Math.round(Math.cos(a) * r) + 1, y + c + (int) Math.round(Math.sin(a) * r) + 1, GREEN_DIM);
        }
        g.fill(x + c, y + c - 3, x + c + 1, y + c + 4, GREEN); g.fill(x + c - 3, y + c + 1, x + c + 4, y + c + 2, GREEN);
        boolean launch = false;
        for (RwrPayload.Line l : lines) {
            double rel = Math.toRadians(((l.bearing() - heading) % 360 + 360) % 360);
            int px = x + c + (int) Math.round(Math.sin(rel) * (r - 4)), py = y + c - (int) Math.round(Math.cos(rel) * (r - 4));
            int col = l.kind() == Rwr.LAUNCH ? RED : l.kind() == Rwr.LOCK ? AMBER : GREEN;
            boolean on = l.kind() == Rwr.SEARCH || WmdUi.pulse(l.kind() == Rwr.LAUNCH ? 0.25F : 0.6F) > 0.35F;
            if (on) g.fill(px - 2, py - 2, px + 3, py + 3, col);
            launch |= l.kind() == Rwr.LAUNCH;
        }
        Component t = Component.translatable(launch ? "hud.wmd.jet.launch" : "hud.wmd.jet.rwr");
        g.drawString(font, t, x + c - font.width(t) / 2, y + s - 11, launch ? RED : WmdUi.TEXT_MUTED, false);
    }

    private static void throttle(GuiGraphics g, Font font, int x, int y, CockpitHudPayload d, float thr, boolean ab, boolean power) {
        int pw = 112, ph = 54, ix = x + 7, iw = pw - 14;
        WmdUi.panel(g, x, y, pw, ph);
        g.drawString(font, Component.translatable("hud.wmd.jet.throttle"), ix, y + 7, WmdUi.TEXT, false);
        Component chip; int kind; boolean pulse = false;
        if (!d.has(CockpitHudPayload.F_SHIP)) { chip = Component.translatable("hud.wmd.helm.not_aboard"); kind = WmdUi.K_BAD; }
        else if (!power) { chip = Component.translatable("hud.wmd.jet.avionics_off"); kind = WmdUi.K_WARN; pulse = true; }
        else if (d.seat() == 1) { chip = Component.translatable("hud.wmd.jet.wso"); kind = WmdUi.K_INFO; }
        else if (d.has(CockpitHudPayload.F_CONTROL)) { chip = Component.translatable("hud.wmd.helm.in_control"); kind = WmdUi.K_INFO; }
        else { chip = Component.translatable("hud.wmd.helm.standby"); kind = WmdUi.K_IDLE; }
        WmdUi.chipRight(g, font, x + pw - 6, y + 5, chip, kind, pulse, 66);
        WmdUi.divider(g, ix, y + 18, iw);
        float f = d.seat() == 0 ? thr : d.throttle();
        boolean a = d.seat() == 0 ? ab : d.has(CockpitHudPayload.F_AB);
        ShipHud.bar(g, ix, y + 24, iw, 5, f, a ? WmdUi.ORANGE : WmdUi.BLUE_LIGHT);
        String v = a ? "AB" : Math.round(f * 100.0F) + "%";
        g.drawString(font, v, ix, y + 33, a ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT, false);
        String en = d.engines() == 0 ? Component.translatable("hud.wmd.jet.no_engines").getString() : d.running() + "/" + d.engines();
        g.drawString(font, en, ix + iw - font.width(en), y + 33, d.engines() == 0 ? WmdUi.TEXT_DIM : d.running() < d.engines() ? WmdUi.WARN : WmdUi.TEXT_MUTED, false);
        g.drawString(font, Component.translatable(d.has(CockpitHudPayload.F_NORADAR) ? "hud.wmd.jet.no_radar" : d.has(CockpitHudPayload.F_SEA) ? "hud.wmd.jet.radar_sea" : d.has(CockpitHudPayload.F_RADAR) ? "hud.wmd.jet.radar_on" : "hud.wmd.jet.radar_off"), ix, y + 43,
                d.has(CockpitHudPayload.F_RADAR) && power ? WmdUi.CYAN : WmdUi.TEXT_DIM, false);
    }

    private static void stations(GuiGraphics g, Font font, int cx, int y, CockpitHudPayload d) {
        Component load = Component.translatable(AirStores.isBomb(d.stnKind()) ? "item.wmd." + AirStores.bomb(d.stnKind()).id
                : d.stnKind() >= 0 ? "hud.wmd.jet.kind" + d.stnKind() : d.stnKind() == -1 ? "hud.wmd.jet.kind_empty" : "hud.wmd.jet.kind_none");
        if (d.stnKind() == AirMissiles.AGM) load = Component.translatable(d.has(CockpitHudPayload.F_AGMGPS) ? "hud.wmd.jet.agm_gps" : "hud.wmd.jet.agm_laser");
        Component s = Component.translatable("hud.wmd.jet.stations", d.station() + 1, d.stnCount(), load, d.flares(), d.chaff());
        if (d.gunRounds() >= 0) s = s.copy().append(Component.translatable("hud.wmd.jet.gun", d.gunRounds()));
        g.drawString(font, s, cx - font.width(s) / 2, y, GREEN_DIM, false);
    }

    private static void ejectBar(GuiGraphics g, Font font, int cx, int y, float k) {
        int bw = 120;
        g.fill(cx - bw / 2, y, cx + bw / 2, y + 12, 0xC00F1318);
        g.fill(cx - bw / 2, y, cx - bw / 2 + Math.round(bw * Mth.clamp(k, 0.0F, 1.0F)), y + 12, 0xFFB03A2E);
        Component t = Component.translatable("hud.wmd.jet.eject_hold");
        g.drawString(font, t, cx - font.width(t) / 2, y + 2, WmdUi.TEXT, false);
    }

    private static void hints(GuiGraphics g, Font font, int cx, int y, float a) {
        int alpha = Math.round(255 * Mth.clamp(a, 0.0F, 1.0F));
        if (alpha < 8) return;
        Component t = Component.translatable("hud.wmd.jet.hints", ClientCockpit.PITCH_DOWN.getTranslatedKeyMessage(), ClientCockpit.PITCH_UP.getTranslatedKeyMessage(),
                ClientCockpit.ROLL_LEFT.getTranslatedKeyMessage(), ClientCockpit.ROLL_RIGHT.getTranslatedKeyMessage(), ClientCockpit.YAW_LEFT.getTranslatedKeyMessage(),
                ClientCockpit.YAW_RIGHT.getTranslatedKeyMessage(), ClientCockpit.THR_UP.getTranslatedKeyMessage(), ClientCockpit.THR_DOWN.getTranslatedKeyMessage(),
                ClientCockpit.EJECT.getTranslatedKeyMessage());
        int tw = font.width(t) + 12;
        g.fill(cx - tw / 2, y - 3, cx + tw / 2, y + 11, (Math.round(alpha * 0.7F) << 24) | 0x0F1318);
        g.drawString(font, t, cx - font.width(t) / 2, y, (alpha << 24) | 0xDCE3EA, false);
    }
}
