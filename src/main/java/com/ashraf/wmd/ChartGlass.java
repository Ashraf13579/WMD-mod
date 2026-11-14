package com.ashraf.wmd;

final class ChartGlass {

    private ChartGlass() {}

    static final int[] BANDS = { 6, 12, 20, 30, 45, 60, 80 };
    static final int[] BAND_RGB = { 0xACDCE8, 0x8CC6DE, 0x6CACD2, 0x508EC4, 0x3A70B2, 0x2A5698, 0x1E3E7A, 0x142A5C };
    static final int UNKNOWN = 0xFF1C222A, HATCH = 0xFF2C343E, OLD_TINT = 0xFF5A6470;

    static int band(int depth) {
        for (int i = 0; i < BANDS.length; i++) if (depth < BANDS[i]) return i;
        return BANDS.length;
    }

    static int colour(int v, int x, int y, float dim) {
        int rgb;
        if (v == 0) return ((x + y) % 3 == 0) ? HATCH : UNKNOWN;
        if ((v & SeaChart.SEA) != 0) rgb = BAND_RGB[band(v & 0xFFFF)];
        else if ((v & SeaChart.LAND) != 0) rgb = v & 0xFFFFFF;
        else rgb = ((x + y) % 3 == 0) ? 0x2C343E : WmdUi.scale(0xFF000000 | (v & 0xFFFFFF), 0.55F) & 0xFFFFFF;
        if (dim < 1.0F) rgb = WmdUi.scale(0xFF000000 | rgb, dim) & 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    static boolean edge(int a, int b) {
        boolean sa = (a & SeaChart.SEA) != 0, sb = (b & SeaChart.SEA) != 0;
        if (sa != sb) return a != 0 && b != 0;
        return sa && band(a & 0xFFFF) != band(b & 0xFFFF);
    }

    static void paint(LiveScreenRenderer.Canvas c, int[] cells, int w, int h, int x0, int z0, int pageW, int pageD, double sx, double sz, float hdg,
                      int[] route, int[] track, boolean night, boolean letGo, double tt) {
        float cw = c.width() / w, ch = c.height() / h, dim = night ? 0.55F : 1.0F;
        if (cells.length == w * h) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int v = cells[y * w + x], col = colour(v, x, y, dim);
                c.fill(x * cw, y * ch, (x + 1) * cw, (y + 1) * ch, col);
                if (x > 0 && edge(v, cells[y * w + x - 1])) c.fill(x * cw, y * ch, x * cw + cw * 0.18F, (y + 1) * ch, WmdUi.scale(col, 0.7F));
                if (y > 0 && edge(v, cells[(y - 1) * w + x])) c.fill(x * cw, y * ch, (x + 1) * cw, y * ch + ch * 0.18F, WmdUi.scale(col, 0.7F));
            }
        } else c.fill(0, 0, c.width(), c.height(), UNKNOWN);
        float kx = c.width() / pageW, kz = c.height() / pageD;
        int white = night ? 0xFFC8A0A0 : 0xFFFFFFFF;
        for (int i = 0; i + 1 < track.length; i += 2) c.dot((track[i] - x0) * kx, (track[i + 1] - z0) * kz, 0.8F, white);
        if (route.length >= 2) {
            float px = (float) ((sx - x0) * kx), pz = (float) ((sz - z0) * kz);
            for (int i = 0; i + 1 < route.length; i += 2) {
                float qx = (route[i] - x0) * kx, qz = (route[i + 1] - z0) * kz;
                c.line(px, pz, qx, qz, 1.6F, 0.6F, night ? 0xFFD0B0B0 : 0xFFC8E2FF);
                c.dot(qx, qz, 1.6F, 0xFFD58A3A);
                px = qx; pz = qz;
            }
        }
        if (!Double.isNaN(sx)) {
            float bx = (float) ((sx - x0) * kx), bz = (float) ((sz - z0) * kz), pulse = 0.85F + 0.15F * (float) Math.sin(tt * 0.15);
            c.dot(bx, bz, 4.2F * pulse, night ? 0x60FF6050 : 0x6050C8FF);
            c.dot(bx, bz, 1.8F, night ? 0xFFFFB0A0 : 0xFFE0FAFF);
            double r = Math.toRadians(hdg);
            c.line(bx, bz, bx + (float) Math.sin(r) * 6.0F, bz - (float) Math.cos(r) * 6.0F, 1.0F, 0.5F, night ? 0xFFFFB0A0 : 0xFFFFFFFF);
        }
        if (letGo && ((long) (tt / 6.0)) % 2 == 0) {
            float t = 1.2F;
            c.fill(0, 0, c.width(), t, WmdUi.WARN); c.fill(0, c.height() - t, c.width(), c.height(), WmdUi.WARN);
            c.fill(0, 0, t, c.height(), WmdUi.WARN); c.fill(c.width() - t, 0, c.width(), c.height(), WmdUi.WARN);
        }
    }
}
