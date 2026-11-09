package com.ashraf.wmd;

import java.util.List;

import net.minecraft.nbt.CompoundTag;

final class SonarPicture {

    private SonarPicture() {}

    static final int RING = 0x30FFFFFF, LABEL = 0xFF7F8C99, GREY = 0xFF8C98A4, BLUE = 0xFF6FB6FF, CYAN = 0xFF3AC2D5, DIM_CYAN = 0xFF5FCFDE,
            WARN = 0xFFE0A030, TEXT = 0xFFDDE6EE;

    static void map(LiveScreenRenderer.Canvas k, SonarConsoleBlockEntity c) {
        float w = k.width(), h = k.height(), mx = w / 2.0F, my = h / 2.0F, u = h / 82.0F;
        int cx = c.mapX(), cz = c.mapZ();
        List<CompoundTag> rows = c.contactRows(), fixes = c.fixRows(), sens = c.sensorRows();
        double ext = 40.0;
        for (CompoundTag t : sens) ext = Math.max(ext, Math.hypot(t.getInt("x") - cx, t.getInt("z") - cz));
        for (CompoundTag t : rows) if (t.getBoolean("f")) ext = Math.max(ext, Math.hypot(t.getInt("fx") - cx, t.getInt("fz") - cz));
        for (CompoundTag t : fixes) ext = Math.max(ext, Math.hypot(t.getInt("x") - cx, t.getInt("z") - cz));
        ext *= 1.1;
        double sc = (Math.min(w, h) / 2.0 - 5.0 * u) / ext;
        int step = 10;
        for (int n : new int[] { 10, 25, 50, 100, 250, 500, 1000, 2500, 5000 }) { step = n; if (n >= ext / 2.0) break; }
        for (int r = step; r * sc < w * 0.75; r += step) {
            float rp = (float) (r * sc);
            int dots = Math.max(24, (int) (rp / u * 1.2F));
            for (int i = 0; i < dots; i++) {
                double a = i * Math.PI * 2.0 / dots;
                float px = mx + (float) Math.cos(a) * rp, py = my + (float) Math.sin(a) * rp;
                if (px > 0 && px < w && py > 0 && py < h) k.dot(px, py, 0.5F * u, RING);
            }
            if (mx + rp + 12.0F * u < w) k.text(String.valueOf(r), mx + rp + 2.0F * u, my - 3.0F * u, 5.5F * u, 1.0E9F, false, LABEL);
        }
        k.text("N", mx, 1.0F * u, 5.5F * u, GREY);

        for (int i = 0; i < rows.size(); i++) {
            CompoundTag t = rows.get(i);
            if (!t.contains("ex")) continue;
            float sx = mx + (float) ((t.getInt("ex") - cx) * sc), sy = my + (float) ((t.getInt("ez") - cz) * sc);
            String id = String.valueOf(i + 1);
            if (t.getBoolean("f")) {
                float fx = mx + (float) ((t.getInt("fx") - cx) * sc), fy = my + (float) ((t.getInt("fz") - cz) * sc);
                k.line(sx, sy, fx, fy, 1.0F * u, 0.4F * u, 0x803AC2D5);
                k.fill(fx - 2.0F * u, fy - 2.0F * u, fx + 2.0F * u, fy + 2.0F * u, CYAN);
                k.text(id, fx + 3.0F * u, fy - 6.0F * u, 5.5F * u, 1.0E9F, false, CYAN);
            } else {
                double b = Math.toRadians(t.getInt("b") / 10.0);
                float ex = sx + (float) Math.sin(b) * w * 2.0F, ey = sy - (float) Math.cos(b) * w * 2.0F;
                dashed(k, sx, sy, ex, ey, w, h, u, 0x902C8F9E);
                float lx = sx + (float) Math.sin(b) * 18.0F * u, ly = sy - (float) Math.cos(b) * 18.0F * u;
                k.text(id, lx + 2.0F * u, ly - 2.0F * u, 5.5F * u, 1.0E9F, false, DIM_CYAN);
            }
        }

        for (int i = 0; i < fixes.size(); i++) {
            CompoundTag f = fixes.get(i);
            float fx = mx + (float) ((f.getInt("x") - cx) * sc), fy = my + (float) ((f.getInt("z") - cz) * sc);
            int col = f.getBoolean("s") ? WARN : BLUE;
            k.fill(fx - 2.0F * u, fy - 2.0F * u, fx + 2.0F * u, fy + 2.0F * u, col);
            k.text(String.valueOf((char) ('A' + Math.min(i, 25))), fx + 3.0F * u, fy + 1.0F * u, 5.5F * u, 1.0E9F, false, col);
        }

        for (CompoundTag t : sens) {
            float sx = mx + (float) ((t.getInt("x") - cx) * sc), sy = my + (float) ((t.getInt("z") - cz) * sc);
            if (t.getInt("k") == SonarNet.DOME) { k.fill(sx - 1.0F * u, sy - 2.0F * u, sx + 2.0F * u, sy + 3.0F * u, BLUE); k.fill(sx - 2.0F * u, sy - 1.0F * u, sx + 3.0F * u, sy + 2.0F * u, BLUE); }
            else k.fill(sx - 2.0F * u, sy - 2.0F * u, sx + 2.0F * u, sy + 2.0F * u, BLUE);
        }
        k.dot(mx, my, 1.4F * u, TEXT);
    }

    private static void dashed(LiveScreenRenderer.Canvas k, float x0, float y0, float x1, float y1, float w, float h, float u, int col) {
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, Math.min(600, (int) (d / u)));
        for (int i = 0; i <= n; i++) {
            if ((i / 3) % 2 == 1) continue;
            float x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n;
            if (x < 0 || x > w || y < 0 || y > h) { if (i > 0) break; continue; }
            k.dot(x, y, 0.4F * u, col);
        }
    }

    static void history(LiveScreenRenderer.Canvas k, SonarConsoleBlockEntity c) {
        float w = k.width(), h = k.height(), lab = h * 0.12F;
        for (int q = 1; q < 4; q++) k.fill(w * q / 4.0F, 0, w * q / 4.0F + w * 0.008F, h - lab, 0x22FFFFFF);
        List<int[]> fall = c.waterfall();
        float rowH = (h - lab) / Math.max(1, Balance.SONAR_WATERFALL_ROWS);
        for (int r = 0; r < fall.size() && r < Balance.SONAR_WATERFALL_ROWS; r++) {
            float ry = r * rowH;
            for (int v : fall.get(r)) {
                int b10 = v & 0xFFF, lv = (v >>> 12) & 0xFF;
                float px = Math.min(w * 0.985F, b10 * w / 3600.0F);
                float t = Math.max(0.0F, Math.min(1.0F, (lv - (float) Balance.NOISE_FLOOR_DB) / 60.0F));
                int a = 0x60 + (int) (t * 0x9F);
                k.fill(px, ry, px + w * 0.0125F, ry + rowH, (a << 24) | (t > 0.7F ? 0xE8FBFF : 0x3AC2D5));
            }
        }
        String[] d = { "N", "E", "S", "W", "N" };
        for (int q = 0; q < 5; q++) k.text(d[q], Math.max(lab * 0.4F, Math.min(w - lab * 0.4F, w * q / 4.0F)), h - lab * 0.95F, lab * 0.85F, GREY);
    }

    static final class Renderer extends LiveScreenRenderer<SonarConsoleBlockEntity> {
        Renderer() {
            super("sonar_console", 2, 2, new Rect(-5.0F, 13.4F, 13.6F, 25.2F, -3.6F), new Rect(15.0F, 21.6F, 18.0F, 24.6F, -3.6F));
        }

        @Override
        protected void paint(SonarConsoleBlockEntity be, int screen, Canvas c, double tt) {
            if (screen == 0) map(c, be);
            else if (hullShown(be, tt)) HullPage.paint(c, be.hullInfo(), true);
            else history(c, be);
        }

        private static boolean hullShown(SonarConsoleBlockEntity be, double tt) {
            net.minecraft.nbt.CompoundTag h = be.hullInfo();
            return h.getBoolean("on") && (HullPage.alarm(h) || ((long) (tt / HULL_SWAP)) % 2L == 1L);
        }

        private static final double HULL_SWAP = 120.0;

        @Override
        protected int background(SonarConsoleBlockEntity be, int screen) {
            return screen == 1 && HullPage.alarm(be.hullInfo()) ? 0xF0280A0A : super.background(be, screen);
        }
    }
}
