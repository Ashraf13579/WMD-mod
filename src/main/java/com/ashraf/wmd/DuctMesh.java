package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

public final class DuctMesh {

    public static final int FRAME = 0, GLASS = 1, RIM = 2, IN = 3, OUT = 4, OFF = 5, SCREEN = 6, DARK = 7;

    public static final class Quad {
        public final double[][] pos = new double[4][], nrm = new double[4][], uv = new double[4][];
        public int mat;
    }

    static final int[][] DIRS = { {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0} };
    static final double H = 4.0, RAIL = 0.75;

    private final List<Quad> out = new ArrayList<>();

    private void face(int axis, double c, double a0, double a1, double b0, double b1, int sign, int mat) {
        int ia = axis == 0 ? 1 : 0, ib = axis == 2 ? 1 : 2;
        double[][] p = new double[4][];
        double[][] ab = { {a0, b0}, {a1, b0}, {a1, b1}, {a0, b1} };
        for (int i = 0; i < 4; i++) { double[] v = new double[3]; v[axis] = c; v[ia] = ab[i][0]; v[ib] = ab[i][1]; p[i] = v; }
        double[] n = new double[3];
        n[axis] = sign;

        double[] e1 = sub(p[1], p[0]), e2 = sub(p[2], p[1]);
        double[] cr = { e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0] };
        boolean flip = cr[axis] * sign < 0;
        Quad q = new Quad();
        q.mat = mat;
        for (int i = 0; i < 4; i++) {
            double[] v = p[flip ? 3 - i : i];
            q.pos[i] = v;
            q.nrm[i] = n;
            q.uv[i] = uvOf(axis, v);
        }
        out.add(q);
    }

    private static double[] sub(double[] a, double[] b) { return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] }; }

    private static double[] uvOf(int axis, double[] v) {
        return switch (axis) {
            case 0 -> new double[] { v[2] / 16.0, 1 - v[1] / 16.0 };
            case 1 -> new double[] { v[0] / 16.0, v[2] / 16.0 };
            default -> new double[] { v[0] / 16.0, 1 - v[1] / 16.0 };
        };
    }

    private void box(double x0, double y0, double z0, double x1, double y1, double z1, int mat, int skip) {
        if ((skip & 1) == 0) face(1, y0, x0, x1, z0, z1, -1, mat);
        if ((skip & 2) == 0) face(1, y1, x0, x1, z0, z1, 1, mat);
        if ((skip & 4) == 0) face(2, z0, x0, x1, y0, y1, -1, mat);
        if ((skip & 8) == 0) face(2, z1, x0, x1, y0, y1, 1, mat);
        if ((skip & 16) == 0) face(0, x0, y0, y1, z0, z1, -1, mat);
        if ((skip & 32) == 0) face(0, x1, y0, y1, z0, z1, 1, mat);
    }

    private void box(double[] lo, double[] hi, int mat) { box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], mat, 0); }

    private void pane(int axis, double c, double a0, double a1, double b0, double b1) {
        face(axis, c, a0, a1, b0, b1, 1, GLASS);
        face(axis, c, a0, a1, b0, b1, -1, GLASS);
    }

    private static double[][] along(int d, double s0, double s1, double h) {
        double[] lo = { 8 - h, 8 - h, 8 - h }, hi = { 8 + h, 8 + h, 8 + h };
        int ax = d / 2 == 0 ? 1 : d / 2 == 1 ? 2 : 0, sg = DIRS[d][ax];
        double a = 8 + sg * s0, b = 8 + sg * s1;
        lo[ax] = Math.min(a, b);
        hi[ax] = Math.max(a, b);
        return new double[][] { lo, hi };
    }

    private static int axisOf(int d) { return d / 2 == 0 ? 1 : d / 2 == 1 ? 2 : 0; }

    private void ring(int d, double s0, double s1, double ho, double hi, int mat) {
        int ax = axisOf(d), a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
        double[][] span = along(d, s0, s1, ho);
        double[] lo = span[0], up = span[1];
        double[][] bars = { {8 - ho, 8 - hi, 8 - ho, 8 + ho}, {8 + hi, 8 + ho, 8 - ho, 8 + ho}, {8 - hi, 8 + hi, 8 - ho, 8 - hi}, {8 - hi, 8 + hi, 8 + hi, 8 + ho} };
        for (double[] b : bars) {
            double[] l = lo.clone(), h = up.clone();
            l[a1] = b[0]; h[a1] = b[1]; l[a2] = b[2]; h[a2] = b[3];
            box(l, h, mat);
        }
    }

    public static List<Quad> duct(int[] conn) {
        DuctMesh m = new DuctMesh();
        int[] c = conn.clone();
        boolean any = false;
        for (int v : c) any |= v != 0;
        if (!any) { c[2] = 1; c[3] = 1; }
        m.makeDuct(c);
        return m.out;
    }

    private void makeDuct(int[] c) {
        double lo = 8 - H, hi = 8 + H, rl = H - RAIL;

        for (int d = 0; d < 6; d++) {
            if (c[d] != 0) continue;
            int ax = axisOf(d);
            double plane = DIRS[d][ax] > 0 ? hi : lo;
            pane(ax, plane, 8 - rl, 8 + rl, 8 - rl, 8 + rl);
        }
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2)
            box(8 + sx * H - RAIL, 8 + sy * H - RAIL, 8 + sz * H - RAIL, 8 + sx * H + RAIL, 8 + sy * H + RAIL, 8 + sz * H + RAIL, FRAME, 0);
        for (int ax = 0; ax < 3; ax++) {
            int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
            for (int s1 = -1; s1 <= 1; s1 += 2) for (int s2 = -1; s2 <= 1; s2 += 2) {
                double[] l = new double[3], h = new double[3];
                l[ax] = 8 - rl; h[ax] = 8 + rl;
                l[a1] = 8 + s1 * H - RAIL; h[a1] = 8 + s1 * H + RAIL;
                l[a2] = 8 + s2 * H - RAIL; h[a2] = 8 + s2 * H + RAIL;
                box(l, h, FRAME);
            }
        }

        for (int d = 0; d < 6; d++) {
            if (c[d] == 0) continue;
            int ax = axisOf(d), a1 = (ax + 1) % 3, a2 = (ax + 2) % 3, sg = DIRS[d][ax];
            double end = c[d] == 1 ? 7.0 : c[d] >= 2 ? 6.0 : 8.0;
            for (int s1 = -1; s1 <= 1; s1 += 2) for (int s2 = -1; s2 <= 1; s2 += 2) {
                double[][] sp = along(d, H + RAIL, end, H);
                double[] l = sp[0], h = sp[1];
                l[a1] = 8 + s1 * H - RAIL; h[a1] = 8 + s1 * H + RAIL;
                l[a2] = 8 + s2 * H - RAIL; h[a2] = 8 + s2 * H + RAIL;
                box(l, h, FRAME);
            }
            double c0 = 8 + sg * H, c1 = 8 + sg * end;
            for (int s = -1; s <= 1; s += 2) {

                paneAlong(ax, a1, 8 + s * H, Math.min(c0, c1), Math.max(c0, c1), 8 - H + RAIL, 8 + H - RAIL);
                paneAlong(ax, a2, 8 + s * H, Math.min(c0, c1), Math.max(c0, c1), 8 - H + RAIL, 8 + H - RAIL);
            }
            if (c[d] == 1) ring(d, 7.0, 8.0, H + 0.9, H - 0.2, RIM);
            else {
                int mat = c[d] == 2 ? IN : c[d] == 3 ? OUT : OFF;
                ring(d, 6.0, 7.2, H + 1.6, H - 0.4, RIM);
                ring(d, 7.2, 8.0, H + 1.4, H - 0.4, mat);
            }
        }
    }

    private void paneAlong(int ax, int pax, double c, double s0, double s1, double t0, double t1) {
        int other = 3 - ax - pax;

        int first = Math.min(ax, other), second = Math.max(ax, other);
        double a0 = first == ax ? s0 : t0, a1 = first == ax ? s1 : t1, b0 = second == ax ? s0 : t0, b1 = second == ax ? s1 : t1;
        pane(pax, c, a0, a1, b0, b1);
    }

    public static List<Quad> sorter(int[] faces) {
        DuctMesh m = new DuctMesh();
        m.makeSorter(faces);
        return m.out;
    }

    private void makeSorter(int[] f) {
        box(2.5, 2.5, 2.5, 13.5, 12.5, 13.5, DARK, 0);
        pane(1, 13.5, 3.5, 12.5, 3.5, 12.5);
        box(6.5, 12.5, 3.5, 9.5, 13.0, 12.5, RIM, 0);
        for (int sx = 0; sx < 2; sx++) for (int sy = 0; sy < 2; sy++) for (int sz = 0; sz < 2; sz++)
            box(sx == 0 ? 2.0 : 12.5, sy == 0 ? 2.0 : 12.5, sz == 0 ? 2.0 : 12.5, sx == 0 ? 3.5 : 14.0, sy == 0 ? 3.5 : 14.0, sz == 0 ? 3.5 : 14.0, RIM, 0);
        for (int ax = 0; ax < 3; ax++) {
            int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
            for (int s1 = 0; s1 < 2; s1++) for (int s2 = 0; s2 < 2; s2++) {
                double[] l = new double[3], h = new double[3];
                l[ax] = 3.5; h[ax] = 12.5;
                l[a1] = s1 == 0 ? 2.0 : 12.5; h[a1] = s1 == 0 ? 3.5 : 14.0;
                l[a2] = s2 == 0 ? 2.0 : 12.5; h[a2] = s2 == 0 ? 3.5 : 14.0;
                box(l, h, RIM);
            }
        }
        for (int d = 2; d < 6; d++) {
            double[][] sp = along(d, 5.5, 5.8, 4.5);
            sp[0][1] = 11.0; sp[1][1] = 12.0;
            box(sp[0], sp[1], IN);
        }
        for (int d = 0; d < 6; d++) {
            if (f[d] == 0) continue;
            ring(d, 6.0, 7.0, H + 1.6, H - 0.4, FRAME);
            ring(d, 7.0, 8.0, H + 1.4, H - 0.4, f[d] == 1 ? IN : OUT);
            double[][] sp = along(d, 5.8, 6.0, H - 0.4);
            box(sp[0], sp[1], DARK);
        }
    }

    public static List<Quad> floodgate(int facing, boolean pouring) {
        DuctMesh m = new DuctMesh();
        m.makeFloodgate(pouring);
        m.face6(facing);
        return m.out;
    }

    private void makeFloodgate(boolean pouring) {
        box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0, DARK, 0);
        box(2.0, 1.0, 2.0, 14.0, 15.0, 15.0, FRAME, 0);
        for (double x : new double[] {1.0, 14.0}) for (double z : new double[] {2.0, 14.0})
            box(x, 1.0, z, x + 1.0, 15.0, z + 1.0, RIM, 0);
        box(1.0, 1.0, 1.0, 15.0, 3.0, 2.0, RIM, 0);
        box(1.0, 13.0, 1.0, 15.0, 15.0, 2.0, RIM, 0);
        box(1.0, 3.0, 1.0, 3.0, 13.0, 2.0, RIM, 0);
        box(13.0, 3.0, 1.0, 15.0, 13.0, 2.0, RIM, 0);
        for (int i = 0; i < 4; i++) box(3.0, 4.0 + i * 2.4, 1.4, 13.0, 4.9 + i * 2.4, 2.0, pouring ? SCREEN : DARK, 8);
        ring(3, 7.0, 8.0, 5.0, 3.0, IN);
    }

    private void face6(int facing) {
        switch (facing) {
            case 5 -> turn(1);
            case 3 -> turn(2);
            case 4 -> turn(3);
            case 0, 1 -> {
                for (Quad q : out) for (int i = 0; i < 4; i++) {
                    double[] p = q.pos[i], n = q.nrm[i];
                    double y = p[1] - 8, z = p[2] - 8;
                    q.pos[i] = facing == 1 ? new double[] { p[0], -z + 8, y + 8 } : new double[] { p[0], z + 8, -y + 8 };
                    q.nrm[i] = facing == 1 ? new double[] { n[0], -n[2], n[1] } : new double[] { n[0], n[2], -n[1] };
                }
            }
            default -> {}
        }
    }

    public static List<Quad> supply(int facing) {
        DuctMesh m = new DuctMesh();
        m.makeSupply();
        if (facing != 0) m.turn(facing);
        return m.out;
    }

    private void makeSupply() {
        box(0.5, 0.0, 0.5, 15.5, 2.0, 15.5, DARK, 0);
        box(1.5, 2.0, 1.5, 14.5, 14.0, 14.5, FRAME, 0);
        box(1.0, 14.0, 1.0, 15.0, 15.0, 15.0, RIM, 0);
        box(5.0, 15.0, 5.0, 11.0, 15.3, 11.0, IN, 0);
        box(3.5, 6.0, 1.1, 12.5, 12.0, 1.5, RIM, 8);
        face(2, 1.05, 4.2, 11.8, 6.7, 11.3, -1, SCREEN);
        for (int i = 0; i < 3; i++) box(4.0 + i * 3.0, 3.0, 1.1, 6.0 + i * 3.0, 4.0, 1.5, i == 0 ? IN : RIM, 8);
    }

    private void turn(int quarters) {
        for (Quad q : out) for (int i = 0; i < 4; i++) {
            double[] p = q.pos[i], n = q.nrm[i];
            double x = p[0] - 8, z = p[2] - 8, nx = n[0], nz = n[2];
            for (int k = 0; k < quarters; k++) { double t = x; x = -z; z = t; t = nx; nx = -nz; nz = t; }
            q.pos[i] = new double[] { x + 8, p[1], z + 8 };
            q.nrm[i] = new double[] { nx, n[1], nz };
        }
    }
}
