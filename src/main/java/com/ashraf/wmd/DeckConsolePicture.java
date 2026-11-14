package com.ashraf.wmd;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class DeckConsolePicture {

    private DeckConsolePicture() {}

    static final int BG = 0xFF0E1A22, GRID = 0xFF13222C, DECK = 0xFF3A4552, LIFT = 0xFF56616E, LIFT_DOWN = 0xFF2A323C, TRACK = 0xFF55606C, BLUE = 0xFF6FA8FF,
            WIRE = 0xFFDCE3EA, ORANGE = 0xFFD58A3A, ORANGE_L = 0xFFF2C08A, CRAFT = 0xFFAFC0D2, CRAFT_SEL = 0xFFDCE3EA, MUTED = 0xFF8E99A6, HI = 0xFF6FA8FF,
            AMBER = 0xFFE0A030;

    static final int[] LENS = { 0xFF4080FF, 0xFFFFF8E0, 0xFFFF2619, 0xFF33FF59, 0xFFFF9926 };

    static double[] frame(ListTag marks, float w, float h) {
        double x0 = -4, x1 = 4, z0 = -4, z1 = 4;
        for (Tag tg : marks) {
            CompoundTag t = (CompoundTag) tg;
            int x = t.getInt("x"), z = t.getInt("z");
            double[] e = extent(t);
            x0 = Math.min(x0, Math.min(x, e[0])); x1 = Math.max(x1, Math.max(x + 1, e[0])); z0 = Math.min(z0, Math.min(z, e[1])); z1 = Math.max(z1, Math.max(z + 1, e[1]));
        }
        double dx = Math.max(24.0, x1 - x0) * 1.12, dz = Math.max(24.0, z1 - z0) * 1.12;
        return new double[] { Math.min(w / dx, h / dz), (x0 + x1) / 2.0, (z0 + z1) / 2.0 };
    }

    private static double[] extent(CompoundTag t) {
        int k = t.getByte("k"), x = t.getInt("x"), z = t.getInt("z"), a = t.getInt("a");
        if (k == DeckConsoleBlockEntity.K_LIFT) return new double[] { x + a, z + t.getInt("b") };
        if (k == DeckConsoleBlockEntity.K_TURN) return new double[] { x + a, z + a };
        if (k == DeckConsoleBlockEntity.K_CAT || k == DeckConsoleBlockEntity.K_WIRE) {
            Direction d = Direction.from2DDataValue(t.getInt("f"));
            return new double[] { x + 0.5 + d.getStepX() * a, z + 0.5 + d.getStepZ() * a };
        }
        if (k == DeckConsoleBlockEntity.K_RAMP || k == DeckConsoleBlockEntity.K_DOOR) {
            Direction d = k == DeckConsoleBlockEntity.K_DOOR ? Direction.from2DDataValue(t.getInt("f")) : Direction.from2DDataValue(t.getInt("f")).getClockWise();
            return new double[] { x + 0.5 + d.getStepX() * a, z + 0.5 + d.getStepZ() * a };
        }
        return new double[] { x + 1, z + 1 };
    }

    static int markAt(ListTag marks, float w, float h, double px, double py) {
        double[] f = frame(marks, w, h);
        int pick = -1;
        double best = 5.0;
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag t = marks.getCompound(i);
            double[] e = extent(t);
            double ax = sx(t.getInt("x") + 0.5, f, w), az = sz(t.getInt("z") + 0.5, f, h), bx = sx(e[0], f, w), bz = sz(e[1], f, h);
            double d = segDist(px, py, ax, az, bx, bz);
            if (d < best) { best = d; pick = i; }
        }
        return pick;
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay, l = vx * vx + vy * vy;
        double t = l <= 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (py - ay) * vy) / l));
        return Math.hypot(px - (ax + vx * t), py - (ay + vy * t));
    }

    static float[] centre(ListTag marks, float w, float h, CompoundTag t) {
        double[] f = frame(marks, w, h);
        return new float[] { sx(t.getInt("x") + 0.5, f, w), sz(t.getInt("z") + 0.5, f, h) };
    }

    private static float sx(double x, double[] f, float w) { return (float) (w / 2.0 + (x - f[1]) * f[0]); }
    private static float sz(double z, double[] f, float h) { return (float) (h / 2.0 + (z - f[2]) * f[0]); }

    static boolean hi(CompoundTag t, int hiKind, long hiPos, int hiCraft) {
        int k = t.getByte("k");
        if (hiKind < 0 || k != hiKind) return false;
        if (k == DeckConsoleBlockEntity.K_LIGHT || k == DeckConsoleBlockEntity.K_WIRE) return hiPos == 0L || t.getLong("p") == hiPos;
        if (k == DeckConsoleBlockEntity.K_CRAFT) return t.getInt("i") == hiCraft;
        return t.getLong("p") == hiPos;
    }

    static void map(LiveScreenRenderer.Canvas k, ListTag marks, int hiKind, long hiPos, int hiCraft, double tt) {
        float w = k.width(), h = k.height();
        k.fill(0, 0, w, h, BG);
        double[] f = frame(marks, w, h);
        float sc = (float) f[0];
        int step = sc * 8 >= 6 ? 8 : 16;
        for (int gx = (int) Math.floor((f[1] - w / 2.0 / sc) / step) * step; sx(gx, f, w) < w; gx += step) { float x = sx(gx, f, w); if (x >= 0) k.fill(x, 0, x + 0.6F, h, GRID); }
        for (int gz = (int) Math.floor((f[2] - h / 2.0 / sc) / step) * step; sz(gz, f, h) < h; gz += step) { float z = sz(gz, f, h); if (z >= 0) k.fill(0, z, w, z + 0.6F, GRID); }
        float u = Math.max(1.0F, Math.min(sc, 4.0F));

        for (Tag tg : marks) {
            CompoundTag t = (CompoundTag) tg;
            if (t.getByte("k") != DeckConsoleBlockEntity.K_LIFT) continue;
            float x0 = sx(t.getInt("x"), f, w), z0 = sz(t.getInt("z"), f, h), x1 = sx(t.getInt("x") + t.getInt("a"), f, w), z1 = sz(t.getInt("z") + t.getInt("b"), f, h);
            int s = t.getInt("s");
            boolean hl = hi(t, hiKind, hiPos, hiCraft);
            k.fill(x0 - 1, z0 - 1, x1 + 1, z1 + 1, hl ? HI : MUTED);
            k.fill(x0, z0, x1, z1, s == DeckLiftBlockEntity.UP ? LIFT : s == DeckLiftBlockEntity.DOWN ? LIFT_DOWN : AMBER);
            if (x1 - x0 > 14) k.text("LIFT", (x0 + x1) / 2, (z0 + z1) / 2 - 3, 6.0F, x1 - x0 - 2, true, 0xFFDCE3EA);
        }
        for (Tag tg : marks) {
            CompoundTag t = (CompoundTag) tg;
            int kind = t.getByte("k");
            boolean hl = hi(t, hiKind, hiPos, hiCraft);
            float x = sx(t.getInt("x") + 0.5, f, w), z = sz(t.getInt("z") + 0.5, f, h);
            double[] e = extent(t);
            float ex = sx(e[0], f, w), ez = sz(e[1], f, h);
            switch (kind) {
                case DeckConsoleBlockEntity.K_CAT -> {
                    int s = t.getInt("s");
                    k.line(x, z, ex, ez, 0.8F, hl ? 1.2F : 0.8F, hl ? HI : TRACK);
                    float sh = s == CatapultTrackBlockEntity.STROKE ? (float) ((tt % 20) / 20.0) : 0.0F;
                    k.dot(x + (ex - x) * sh, z + (ez - z) * sh, 1.4F, s == CatapultTrackBlockEntity.HOOKED ? ORANGE_L : s == CatapultTrackBlockEntity.IDLE ? MUTED : WIRE);
                }
                case DeckConsoleBlockEntity.K_WIRE -> k.line(x, z, ex, ez, 0.8F, 0.5F, hl ? HI : t.getInt("s") == 0 ? WIRE : AMBER);
                case DeckConsoleBlockEntity.K_TURN -> {
                    float x0 = sx(t.getInt("x"), f, w), z0 = sz(t.getInt("z"), f, h), cx = (x0 + ex) / 2.0F, cz = (z0 + ez) / 2.0F, rr = Math.abs(ex - x0) / 2.0F;
                    int col = hl ? HI : t.getInt("s") == TurntableBlockEntity.S_TURNING ? AMBER : LIFT;
                    k.line(x0, z0, ex, z0, 0.6F, 0.6F, col); k.line(ex, z0, ex, ez, 0.6F, 0.6F, col); k.line(ex, ez, x0, ez, 0.6F, 0.6F, col); k.line(x0, ez, x0, z0, 0.6F, 0.6F, col);
                    double ha = Math.toRadians(t.getInt("h"));
                    k.line(cx, cz, cx + (float) Math.sin(ha) * rr * 0.9F, cz - (float) Math.cos(ha) * rr * 0.9F, 0.8F, 0.8F, BLUE);
                    k.dot(cx, cz, 1.0F, col);
                }
                case DeckConsoleBlockEntity.K_LIGHT -> {
                    int m = Math.max(0, Math.min(4, t.getInt("m")));
                    boolean on = t.getBoolean("on");
                    if (m == DeckLightBlockEntity.BEACON) {
                        float r = 1.6F;
                        k.fill(x - r, z - 0.6F, x + r, z + 0.6F, on ? ORANGE : MUTED);
                        k.fill(x - 0.6F, z - r, x + 0.6F, z + r, on ? ORANGE : MUTED);
                    } else k.dot(x, z, 0.7F, on ? LENS[m] : 0xFF2A323C);
                    if (hl) k.dot(x, z, 0.3F, HI);
                }
                case DeckConsoleBlockEntity.K_RAMP, DeckConsoleBlockEntity.K_DOOR -> {
                    boolean door = kind == DeckConsoleBlockEntity.K_DOOR;
                    if (door) k.line(x, z, ex, ez, 2.0F, 0.6F, hl ? HI : ORANGE);
                    else k.line(x, z, ex, ez, 0.6F, hl ? 1.1F : 0.8F, hl ? HI : t.getInt("s") == CargoRampBlockEntity.S_CLOSED ? ORANGE : ORANGE_L);
                }
                case DeckConsoleBlockEntity.K_CRAFT -> {
                    int c = hl ? CRAFT_SEL : CRAFT;
                    float r = Math.max(2.0F, u * 1.5F);
                    k.fill(x - r, z - 0.5F, x + r, z + 0.5F, c);
                    k.fill(x - 0.5F, z - r, x + 0.5F, z + r * 0.6F, c);
                    k.fill(x - r * 0.5F, z + r * 0.5F, x + r * 0.5F, z + r * 0.5F + 0.8F, c);
                    if (hl) { k.fill(x - r - 1, z - r - 1, x + r + 1, z - r - 0.4F, HI); k.fill(x - r - 1, z + r + 0.4F, x + r + 1, z + r + 1, HI); }
                }
                case DeckConsoleBlockEntity.K_ARM, DeckConsoleBlockEntity.K_DEPOT, DeckConsoleBlockEntity.K_GANTRY -> {
                    String l = kind == DeckConsoleBlockEntity.K_ARM ? "A" : kind == DeckConsoleBlockEntity.K_DEPOT ? "D" : "G";
                    k.fill(x - 2.2F, z - 2.2F, x + 2.2F, z + 2.2F, 0xFF2A3340);
                    k.text(l, x, z - 1.8F, 3.6F, 4.0F, true, CRAFT);
                }
                default -> { }
            }
        }

        float cx = sx(0.5, f, w), cz = sz(0.5, f, h);
        k.fill(cx - 1.6F, cz - 0.4F, cx + 1.6F, cz + 0.6F, BLUE);
        k.fill(cx - 0.8F, cz - 1.4F, cx + 0.8F, cz - 0.4F, BLUE);
        k.text("N", w - 4.0F, 1.0F, 5.0F, 6.0F, true, 0xFFE07070);
    }
}
