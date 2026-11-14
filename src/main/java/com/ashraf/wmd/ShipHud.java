package com.ashraf.wmd;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class ShipHud {

    private ShipHud() {}

    static final int PW = 112, PH = 96;
    private static final String[] STEP_SHORT = { "FULL", "HALF", "SLOW", "STOP", "SLOW", "HALF", "FULL", "FLANK" };

    private static float eDepth, eSpeed, eNoise, eRudReal, eRudOrd, ePlnReal, ePlnOrd, eBallast, eHeading;
    private static boolean primed;

    static void reset() { primed = false; }

    private static float ease(float cur, float tgt, float k) { return cur + (tgt - cur) * k; }

    public static void render(GuiGraphics g, Font font, HelmHudPayload d, float slide) {
        if (d == null) return;
        if (!primed) {
            eDepth = d.depth(); eSpeed = d.speed(); eNoise = d.noise(); eRudReal = d.rudderReal(); eRudOrd = d.rudderOrder(); ePlnReal = d.planesReal();
            ePlnOrd = d.planesOrder(); eBallast = Math.max(0.0F, d.ballast()); eHeading = d.heading(); primed = true;
        }
        float k = 0.18F;
        eDepth = ease(eDepth, d.depth(), k); eSpeed = ease(eSpeed, d.speed(), k); eNoise = ease(eNoise, d.noise(), k);
        eRudReal = ease(eRudReal, d.rudderReal(), 0.3F); eRudOrd = ease(eRudOrd, d.rudderOrder(), 0.4F);
        ePlnReal = ease(ePlnReal, d.planesReal(), 0.3F); ePlnOrd = ease(ePlnOrd, d.planesOrder(), 0.4F);
        eBallast = ease(eBallast, Math.max(0.0F, d.ballast()), k);
        eHeading = eHeading + Mth.wrapDegrees(d.heading() - eHeading) * 0.25F;
        int w = g.guiWidth(), h = g.guiHeight();
        int drop = Math.round((1.0F - WmdUi.easeOut(slide)) * (PH + 12));
        tape(g, font, w / 2, 4 - Math.round((1.0F - WmdUi.easeOut(slide)) * 30), (eHeading % 360 + 360) % 360);
        ship(g, font, 4, h - PH - 4 + drop, d);
        orders(g, font, w - PW - 4, h - PH - 4 + drop, d);
    }

    static void tape(GuiGraphics g, Font font, int cx, int y, float heading) {
        int tw = 180, th = 16, x0 = cx - tw / 2;
        WmdUi.well(g, x0, y, tw, th);
        g.enableScissor(x0, y, x0 + tw, y + th);
        int first = (int) Math.floor((heading - 46) / 5.0) * 5;
        for (int a = first; a <= heading + 46; a += 5) {
            int px = cx + Math.round((a - heading) * 2.0F);
            int norm = ((a % 360) + 360) % 360;
            boolean ten = norm % 10 == 0, thirty = norm % 30 == 0;
            int len = thirty ? 5 : ten ? 3 : 2;
            g.fill(px, y + th - len, px + 1, y + th, thirty ? WmdUi.TEXT : WmdUi.TEXT_DIM);
            if (thirty) {
                String lab = norm == 0 ? "N" : norm == 90 ? "E" : norm == 180 ? "S" : norm == 270 ? "W" : String.format("%03d", norm);
                boolean card = norm % 90 == 0;
                g.drawString(font, lab, px - font.width(lab) / 2, y + 2, card ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED, false);
            }
        }
        g.disableScissor();

        for (int i = 0; i < 4; i++) g.fill(cx - 3 + i, y - 1 + i, cx + 4 - i, y + i, WmdUi.CYAN);
        String hd = String.format("%03d°", Math.round(heading) % 360);
        int cw = font.width(hd) + 8;
        g.fill(cx - cw / 2, y + th + 2, cx + cw / 2, y + th + 13, WmdUi.CARD);
        g.fill(cx - cw / 2, y + th + 2, cx + cw / 2, y + th + 3, WmdUi.CYAN);
        g.drawString(font, hd, cx - font.width(hd) / 2, y + th + 4, WmdUi.TEXT, false);
    }

    private static void ship(GuiGraphics g, Font font, int x, int y, HelmHudPayload d) {
        WmdUi.panel(g, x, y, PW, PH);
        int ix = x + 7, iw = PW - 14;
        Component chip; int kind; boolean pulse = false;
        if (!d.has(HelmHudPayload.F_SHIP)) { chip = Component.translatable("hud.wmd.helm.not_aboard"); kind = WmdUi.K_BAD; }
        else if (!d.has(HelmHudPayload.F_POWER)) { chip = Component.translatable("hud.wmd.helm.no_power"); kind = WmdUi.K_BAD; pulse = true; }
        else if (d.has(HelmHudPayload.F_DRAG)) { chip = Component.translatable("hud.wmd.helm.dragging"); kind = WmdUi.K_WARN; pulse = true; }
        else if (d.has(HelmHudPayload.F_ANCHOR)) { chip = Component.translatable("hud.wmd.helm.anchored"); kind = WmdUi.K_GOOD; }
        else if (d.has(HelmHudPayload.F_CONTROL)) { chip = Component.translatable("hud.wmd.helm.in_control"); kind = WmdUi.K_INFO; }
        else if (d.has(HelmHudPayload.F_TELEGRAPH)) { chip = Component.translatable("hud.wmd.helm.telegraph"); kind = WmdUi.K_WARN; }
        else { chip = Component.translatable("hud.wmd.helm.standby"); kind = WmdUi.K_IDLE; }
        g.drawString(font, Component.translatable("hud.wmd.helm.title"), ix, y + 7, WmdUi.TEXT, false);
        WmdUi.chipRight(g, font, x + PW - 6, y + 5, chip, kind, pulse, 70);
        WmdUi.divider(g, ix, y + 18, iw);

        boolean air = d.has(HelmHudPayload.F_AIR);
        g.drawString(font, Component.translatable(air ? "hud.wmd.helm.alt" : "hud.wmd.helm.depth"), ix, y + 23, WmdUi.TEXT_MUTED, false);
        String dv = String.valueOf(Math.round(eDepth));
        g.pose().pushPose();
        g.pose().translate(ix + 38, y + 21, 0);
        g.pose().scale(1.5F, 1.5F, 1.0F);
        g.drawString(font, dv, 0, 0, WmdUi.TEXT, false);
        g.pose().popPose();
        g.drawString(font, "m", ix + 40 + Math.round(font.width(dv) * 1.5F), y + 25, WmdUi.TEXT_MUTED, false);
        int crush = air ? -1 : d.crush();
        float f = crush > 0 ? Mth.clamp(eDepth / crush, 0.0F, 1.0F) : 0.0F;
        int col = f < 0.6F ? WmdUi.GOOD : f < 0.85F ? WmdUi.WARN : WmdUi.BAD;
        bar(g, ix, y + 36, iw, 4, f, crush > 0 ? col : WmdUi.TEXT_DIM);
        String cr = air ? Component.translatable("hud.wmd.helm.agl", d.crush()).getString()
                : crush > 0 ? Component.translatable("hud.wmd.helm.crush", crush).getString() : Component.translatable("hud.wmd.helm.no_crush").getString();
        g.drawString(font, cr, ix + iw - font.width(cr), y + 42, f >= 0.85F && crush > 0 ? (WmdUi.pulse(0.6F) > 0.5F ? WmdUi.BAD : WmdUi.TEXT_MUTED) : WmdUi.TEXT_DIM, false);

        g.drawString(font, Component.translatable("hud.wmd.helm.speed"), ix, y + 53, WmdUi.TEXT_MUTED, false);
        String sp = String.format("%.1f m/s  %d kn", eSpeed, Math.round(eSpeed * 1.944F));
        g.drawString(font, sp, ix + iw - font.width(sp), y + 53, eSpeed < -0.05F ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT, false);

        g.drawString(font, Component.translatable("hud.wmd.helm.noise"), ix, y + 65, WmdUi.TEXT_MUTED, false);
        String nv = eNoise < 1.0F ? "-" : Math.round(eNoise) + " dB";
        int ncol = eNoise < 80.0F ? WmdUi.GOOD : eNoise < 100.0F ? WmdUi.WARN : WmdUi.BAD;
        g.drawString(font, nv, ix + iw - font.width(nv), y + 65, eNoise < 1.0F ? WmdUi.TEXT_DIM : ncol, false);
        bar(g, ix, y + 75, iw, 3, Mth.clamp((eNoise - 40.0F) / 90.0F, 0.0F, 1.0F), ncol);

        if (d.has(HelmHudPayload.F_SUB) && d.ballast() >= 0.0F) {
            g.drawString(font, Component.translatable("hud.wmd.helm.ballast", Math.round(eBallast * 100.0F)), ix, y + 82, WmdUi.TEXT_MUTED, false);
            bar(g, ix + 62, y + 84, iw - 62, 3, eBallast, WmdUi.BLUE_LIGHT);
        }
    }

    private static void orders(GuiGraphics g, Font font, int x, int y, HelmHudPayload d) {
        WmdUi.panel(g, x, y, PW, PH);
        int ix = x + 7;
        g.drawString(font, Component.translatable("hud.wmd.helm.throttle"), ix, y + 7, WmdUi.TEXT, false);
        String en = Component.translatable("hud.wmd.helm.engines", d.running(), d.engines()).getString();
        g.drawString(font, en, x + PW - 7 - font.width(en), y + 7, d.engines() == 0 ? WmdUi.BAD : d.running() < d.engines() ? WmdUi.WARN : WmdUi.TEXT_MUTED, false);
        WmdUi.divider(g, ix, y + 18, PW - 14);

        int lx = ix, ly = y + 22, rowH = 8, lw = 44;
        WmdUi.well(g, lx, ly, lw, rowH * ShipControl.STEPS);
        for (int i = ShipControl.STEPS - 1, row = 0; i >= 0; i--, row++) {
            int ry = ly + row * rowH;
            boolean ordered = i == d.step(), replied = i == d.reply();
            int band = i > Balance.PROP_STEP_STOP ? WmdUi.BLUE : i < Balance.PROP_STEP_STOP ? WmdUi.ORANGE : 0xFF9AA6B2;
            if (ordered) g.fill(lx, ry, lx + lw - 6, ry + rowH - 1, (band & 0x00FFFFFF) | 0xC0000000);
            else g.fill(lx, ry + rowH - 1, lx + lw - 6, ry + rowH, 0x20FFFFFF);
            g.pose().pushPose();
            g.pose().translate(lx + 2, ry + 1, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, STEP_SHORT[i], 0, 0, ordered ? WmdUi.TEXT : i < Balance.PROP_STEP_STOP ? WmdUi.ORANGE_LIGHT : i > Balance.PROP_STEP_STOP ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED, false);
            g.pose().popPose();
            if (replied) for (int k = 0; k < 3; k++) g.fill(lx + lw - 5 + k, ry + 1 + k, lx + lw - 4 + k, ry + rowH - 2 - k, WmdUi.CYAN);
        }

        int gx = lx + lw + 8, gw = PW - 14 - lw - 8;
        g.drawString(font, Component.translatable("hud.wmd.helm.rudder"), gx, y + 22, WmdUi.TEXT_MUTED, false);
        int rud = Math.round(eRudReal);
        String rv = rud == 0 ? "0" : Math.abs(rud) + (rud > 0 ? " S" : " P");
        g.drawString(font, rv, gx + gw - font.width(rv), y + 22, WmdUi.TEXT, false);
        centreBarH(g, gx, y + 32, gw, 6, eRudReal / (float) Balance.PROP_RUDDER_MAX, eRudOrd);

        g.drawString(font, Component.translatable("hud.wmd.helm.planes"), gx, y + 44, WmdUi.TEXT_MUTED, false);
        int pln = Math.round(ePlnReal);
        String pv = pln == 0 ? "0" : Math.abs(pln) + (pln > 0 ? " ↓" : " ↑");
        g.drawString(font, pv, gx + gw - font.width(pv), y + 44, WmdUi.TEXT, false);
        centreBarV(g, gx + gw / 2 - 3, y + 54, 6, 30, ePlnReal / (float) Balance.PROP_PLANES_MAX, ePlnOrd);
        g.drawString(font, Component.translatable("hud.wmd.helm.rise"), gx, y + 56, WmdUi.TEXT_DIM, false);
        g.drawString(font, Component.translatable("hud.wmd.helm.dive"), gx, y + 76, WmdUi.TEXT_DIM, false);
    }

    static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int col) {
        WmdUi.well(g, x, y, w, h);
        int fill = Math.round(w * Mth.clamp(f, 0.0F, 1.0F));
        if (fill > 0) { g.fill(x, y, x + fill, y + h, col); g.fill(x, y, x + fill, y + 1, 0x40FFFFFF); }
    }

    private static void centreBarH(GuiGraphics g, int x, int y, int w, int h, float real, float order) {
        WmdUi.well(g, x, y, w, h);
        int mid = x + w / 2, fx = mid + Math.round(Mth.clamp(real, -1.0F, 1.0F) * (w / 2 - 1));
        g.fill(Math.min(mid, fx), y + 1, Math.max(mid, fx) + 1, y + h - 1, WmdUi.BLUE);
        g.fill(mid, y - 1, mid + 1, y + h + 1, WmdUi.TEXT_DIM);
        int ox = mid + Math.round(Mth.clamp(order, -1.0F, 1.0F) * (w / 2 - 1));
        g.fill(ox, y - 2, ox + 1, y + h + 2, WmdUi.TEXT);
    }

    private static void centreBarV(GuiGraphics g, int x, int y, int w, int h, float real, float order) {
        WmdUi.well(g, x, y, w, h);
        int mid = y + h / 2, fy = mid + Math.round(Mth.clamp(real, -1.0F, 1.0F) * (h / 2 - 1));
        g.fill(x + 1, Math.min(mid, fy), x + w - 1, Math.max(mid, fy) + 1, WmdUi.BLUE);
        g.fill(x - 1, mid, x + w + 1, mid + 1, WmdUi.TEXT_DIM);
        int oy = mid + Math.round(Mth.clamp(order, -1.0F, 1.0F) * (h / 2 - 1));
        g.fill(x - 2, oy, x + w + 2, oy + 1, WmdUi.TEXT);
    }

    static void hints(GuiGraphics g, Font font, float alpha) {
        if (alpha <= 0.02F) return;
        Component t = Component.translatable("hud.wmd.helm.hint");
        int w = g.guiWidth(), h = g.guiHeight(), tw = font.width(t);
        int a = Math.round(alpha * 255.0F);
        g.fill(w / 2 - tw / 2 - 4, h - 52, w / 2 + tw / 2 + 4, h - 41, (Math.round(alpha * 0xA0) << 24) | 0x0F1318);
        g.drawString(font, t, w / 2 - tw / 2, h - 50, (a << 24) | (WmdUi.TEXT & 0x00FFFFFF), false);
    }
}
