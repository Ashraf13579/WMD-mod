package com.ashraf.wmd;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

final class FireControlPicture {

    private FireControlPicture() {}

    private static final String P = "gui.wmd.fcc.";
    static final int RING = 0x30FFFFFF, LABEL = 0xFF7F8C99, GREY = 0xFF8C98A4, BLUE = 0xFF6FB6FF, SEL = 0xFFF2FAFF, RED = 0xFFFF5A4A,
            CYAN = 0xFF3AC2D5, GOOD = 0xFF4CD07A, WARN = 0xFFE0A030, BAD = 0xFFE05040, TEXT = 0xFFDDE6EE;

    private static double hyp(int x, int z, FireControlConsoleBlockEntity c) { return Math.hypot(x - c.mapX(), z - c.mapZ()); }

    private static double scale(float w, float h, FireControlConsoleBlockEntity c) {
        float u = h / 60.0F;
        double ext = 40.0;
        for (CompoundTag t : c.launcherRows()) ext = Math.max(ext, hyp(t.getInt("x"), t.getInt("z"), c));
        for (CompoundTag t : c.targetRows()) ext = Math.max(ext, hyp(t.getInt("x"), t.getInt("z"), c));
        ext *= 1.15;
        return (Math.min(w, h) / 2.0 - 4.0 * u) / ext;
    }

    static int targetAt(float w, float h, FireControlConsoleBlockEntity c, double px, double py) {
        float mx = w / 2.0F, my = h / 2.0F, u = h / 60.0F;
        double sc = scale(w, h, c), best = 6.0 * u;
        int pick = -1;
        List<CompoundTag> tg = c.targetRows();
        for (int i = 0; i < tg.size(); i++) {
            double tx = mx + (tg.get(i).getInt("x") - c.mapX()) * sc, ty = my + (tg.get(i).getInt("z") - c.mapZ()) * sc;
            double d = Math.hypot(px - tx, py - ty);
            if (d <= best) { best = d; pick = i; }
        }
        return pick;
    }

    static void map(LiveScreenRenderer.Canvas k, FireControlConsoleBlockEntity c, double tt) {
        float w = k.width(), h = k.height(), mx = w / 2.0F, my = h / 2.0F, u = h / 60.0F;
        List<CompoundTag> rows = c.launcherRows(), tg = c.targetRows(), tp = c.torpedoRows();
        double sc = scale(w, h, c), ext = (Math.min(w, h) / 2.0 - 4.0 * u) / sc;
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
            if (mx + rp + 10.0F * u < w) k.text(String.valueOf(r), mx + rp + 2.0F * u, my - 3.0F * u, 5.0F * u, 1.0E9F, false, LABEL);
        }
        k.text("N", mx, 1.0F * u, 5.0F * u, GREY);
        CompoundTag pick = c.targetRow();
        float tx = 0, ty = 0;
        if (pick != null) { tx = mx + (float) ((pick.getInt("x") - c.mapX()) * sc); ty = my + (float) ((pick.getInt("z") - c.mapZ()) * sc); }

        for (int i = 0; i < rows.size() && i < Balance.FCC_ROWS; i++) {
            CompoundTag r = rows.get(i);
            float lx = mx + (float) ((r.getInt("x") - c.mapX()) * sc), ly = my + (float) ((r.getInt("z") - c.mapZ()) * sc);
            boolean sel = i == c.selected();
            int col = sel ? SEL : BLUE;
            if (pick != null) k.line(lx, ly, tx, ty, 3.0F * u, 0.3F * u, r.getBoolean("arc") || r.getBoolean("far") ? 0x50E05040 : 0x60FF5A4A);
            double yaw = Math.toRadians(r.getInt("yaw") / 10.0);
            if (!r.getBoolean("cell") && !r.getBoolean("bat"))
                k.line(lx, ly, lx + (float) Math.sin(yaw) * 12.0F * u, ly - (float) Math.cos(yaw) * 12.0F * u, 1.0F * u, 0.35F * u, (col & 0x00FFFFFF) | 0xB0000000);
            k.fill(lx - 1.5F * u, ly - 1.5F * u, lx + 1.5F * u, ly + 1.5F * u, col);
            k.text(String.valueOf(i + 1), lx + 2.5F * u, ly + 0.5F * u, 5.0F * u, 1.0E9F, false, col);
        }

        for (int i = 0; i < tg.size(); i++) {
            CompoundTag t = tg.get(i);
            if (t == pick) continue;
            float px = mx + (float) ((t.getInt("x") - c.mapX()) * sc), py = my + (float) ((t.getInt("z") - c.mapZ()) * sc);
            k.fill(px - 1.2F * u, py - 1.2F * u, px + 1.2F * u, py + 1.2F * u, GREY);
            k.text(String.valueOf(i + 1), px + 2.0F * u, py - 5.0F * u, 4.5F * u, 1.0E9F, false, GREY);
        }
        if (pick != null) {
            boolean on = ((long) (tt / 8.0)) % 2 == 0;
            for (int d = 0; d <= 3; d++) k.fill(tx - (3 - d) * u, ty - d * u - 0.5F * u, tx + (3 - d) * u, ty + d * u + 0.5F * u, on ? RED : 0xC0B03A30);
            k.text(String.valueOf(c.target() + 1), tx + 3.5F * u, ty - 6.0F * u, 5.0F * u, 1.0E9F, false, RED);
        }

        for (CompoundTag t : tp) {
            float px = mx + (float) ((t.getInt("x") - c.mapX()) * sc), py = my + (float) ((t.getInt("z") - c.mapZ()) * sc);
            k.dot(px, py, 0.9F * u, CYAN);
        }

        k.dot(mx, my, 1.1F * u, TEXT);
    }

    static void solution(LiveScreenRenderer.Canvas k, FireControlConsoleBlockEntity c, double tt) {
        float w = k.width(), h = k.height();
        List<CompoundTag> rows = c.launcherRows();
        int n = Math.max(1, Math.min(Balance.FCC_ROWS, rows.size()));
        float line = h / (n + 2.2F), size = Math.min(line * 0.78F, w * 0.11F);
        CompoundTag t = c.targetRow();
        k.text((t == null ? Component.translatable(c.targetRows().isEmpty() ? P + "no_targets" : P + "pick_target", c.targetRows().size())
                : targetWords(c, t, c.target())).getString(), w / 2.0F, line * 0.25F, size, w * 0.94F, true, t == null ? GREY : RED);
        k.fill(w * 0.03F, line * 1.15F, w * 0.97F, line * 1.15F + Math.max(0.3F, line * 0.05F), 0x40FFFFFF);
        if (rows.isEmpty()) { k.text(Component.translatable(P + "none").getString(), w / 2.0F, line * 1.6F, size, w * 0.94F, true, GREY); return; }
        for (int i = 0; i < rows.size() && i < Balance.FCC_ROWS; i++) {
            CompoundTag r = rows.get(i);
            float y = line * (1.4F + i);
            boolean sel = i == c.selected();
            if (sel) k.fill(w * 0.02F, y - line * 0.08F, w * 0.98F, y + line * 0.92F, 0x402D6BB0);
            k.fill(w * 0.04F, y + line * 0.1F, w * 0.04F + line * 0.3F, y + line * 0.75F, lamp(r.getInt("st"), tt));
            k.text(launcherWords(r, i).getString(), w * 0.04F + line * 0.55F, y + (line - size) / 2.0F, size, w * 0.92F - line * 0.55F, false, sel ? TEXT : GREY);
        }
        if (tt - c.lastShot() < 40 && ((long) (tt / 4.0)) % 2 == 0) {
            float b = Math.max(0.4F, w * 0.012F);
            k.fill(0, 0, w, b, RED); k.fill(0, h - b, w, h, RED); k.fill(0, 0, b, h, RED); k.fill(w - b, 0, w, h, RED);
        }
    }

    static int lamp(int st, double tt) {
        if (st == TorpedoLauncherBlockEntity.S_READY) return GOOD;
        if (st == TorpedoLauncherBlockEntity.S_NO_POWER || st == TorpedoLauncherBlockEntity.S_BLOCKED) return BAD;
        if (st == TorpedoLauncherBlockEntity.S_EMPTY) return 0xFF5A6470;
        return ((long) (tt / 6.0)) % 2 == 0 ? WARN : 0xFF6A5020;
    }

    static Component targetWords(FireControlConsoleBlockEntity c, CompoundTag t, int index) {
        int x = t.getInt("x"), z = t.getInt("z");
        String dist = String.format("%,d", (int) Math.round(hyp(x, z, c)));
        Component dir = SonarScreen.compass((Math.toDegrees(Math.atan2(x - c.mapX(), -(z - c.mapZ()))) + 360.0) % 360.0);
        String n = t.getString("n");
        if (t.getBoolean("f")) return Component.translatable(P + "t_fix", index + 1, dist, dir);
        if (n.startsWith("wreck:")) return Component.translatable(P + "t_wreck", index + 1, dist, dir);
        if (n.startsWith("mine:")) return Component.translatable(P + "t_mine", index + 1, t.getInt("d"), dist, dir);
        if (t.getBoolean("s")) return Component.translatable(P + "t_sub", index + 1, t.getInt("d"), dist, dir);
        return Component.translatable(P + "t_ship", index + 1, dist, dir);
    }

    static Component launcherWords(CompoundTag r, int i) {
        if (r.getBoolean("bat"))
            return Component.translatable(P + "bat_row", i + 1, r.getInt("n"), CoastalBatteryBlockEntity.CANISTERS, Component.translatable("gui.wmd.coastal.st." + r.getInt("cbs")));
        if (r.getBoolean("cell")) {
            int rk = r.contains("rk") ? r.getInt("rk") : 0;
            Component what = r.getInt("n") <= 0 ? Component.translatable(P + "empty") : Component.translatable(P + (rk == LaunchCellBlockEntity.R_SLBM ? "slbm" : rk == LaunchCellBlockEntity.R_CRUISE ? "cruise" : "rocket"));
            return Component.translatable(P + "cell_row", i + 1, what, Component.translatable("gui.wmd.lc.st." + r.getInt("cst")));
        }
        int k = r.getInt("k"), d = r.getInt("d"), g = r.getInt("g");
        Component torp = k < 0 ? Component.translatable(P + "empty") : Component.literal(TorpedoKind.of(k).label() + " x" + r.getInt("n"));
        Component depth = d <= 0 ? Component.translatable(P + "d_launch") : Component.translatable(P + "d", d);
        Component gyro = g == 0 ? Component.translatable(P + "g0") : Component.translatable(P + "g", (g > 0 ? "+" : "") + g);
        Component line = Component.translatable(P + "row", i + 1, Component.translatable(P + (r.getBoolean("deck") ? "deck" : "tube")), torp, depth, gyro);
        if (r.getBoolean("arc")) return line.copy().append(Component.translatable(P + "no_shot"));
        if (r.getBoolean("far")) return line.copy().append(Component.translatable(P + "too_far"));
        return line;
    }

    static Component strikeWords(CompoundTag r, int i) {
        boolean bat = r.getBoolean("bat");
        int rk = r.contains("rk") ? r.getInt("rk") : 0, rc = r.getInt("rc");
        Component dist = rc == FireControlConsoleBlockEntity.RC_NONE ? Component.literal("-") : Component.translatable(P + "s_m", String.format("%,d", r.getInt("dist")));
        Component line = bat ? Component.translatable(P + "s_bat", i + 1, r.getInt("n") + "/" + CoastalBatteryBlockEntity.CANISTERS, dist)
                : Component.translatable(P + "s_cell", i + 1, r.getInt("n") <= 0 ? Component.translatable(P + "empty")
                        : Component.translatable(P + (rk == LaunchCellBlockEntity.R_SLBM ? "slbm" : rk == LaunchCellBlockEntity.R_CRUISE ? "cruise" : "rocket")), dist);
        if (rc == FireControlConsoleBlockEntity.RC_FAR) return line.copy().append(Component.translatable(P + "too_far"));
        if (rc == FireControlConsoleBlockEntity.RC_CLOSE) return line.copy().append(Component.translatable(P + "too_close"));
        return line;
    }

    static Component rangeWords(CompoundTag r, int i) {
        if (r.getInt("n") <= 0 || r.getInt("rng") <= 0) return Component.translatable(P + "s_range0", i + 1);
        return Component.translatable(P + "s_range", i + 1, String.format("%,d", r.getInt("min")), String.format("%,d", r.getInt("rng")));
    }

    static Component statusWords(CompoundTag r) {
        return r.getBoolean("bat") ? Component.translatable("gui.wmd.coastal.st." + r.getInt("cbs")) : Component.translatable("gui.wmd.lc.st." + r.getInt("cst"));
    }

    static Component aimWords(CompoundTag r) {
        int src = r.getInt("src");
        if (src == LaunchCellBlockEntity.SRC_OWN)
            return r.getBoolean("own") ? Component.translatable(P + (r.getBoolean("bat") ? "s_aim.typed" : "s_aim.1"), r.getInt("ox"), r.getInt("oz")) : Component.translatable(P + "s_aim.0");
        return Component.translatable(P + "s_aim." + Math.max(0, Math.min(3, src)));
    }

    static final class Renderer extends LiveScreenRenderer<FireControlConsoleBlockEntity> {
        Renderer() {
            super("fire_control_console", 2, 2, new Rect(-5.0F, 10.6F, 13.0F, 25.2F, -3.6F), new Rect(13.0F, 21.6F, 19.6F, 25.2F, -3.6F));
        }

        @Override
        protected void paint(FireControlConsoleBlockEntity be, int screen, Canvas c, double tt) {
            if (screen == 0) map(c, be, tt);
            else solution(c, be, tt);
        }
    }
}
