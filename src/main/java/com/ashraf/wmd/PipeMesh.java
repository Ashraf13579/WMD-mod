package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

public final class PipeMesh {

    public static final int PIPE = 0, COLLAR = 1, BOLT = 2, DARK = 3;

    public static final class Quad {
        public final double[][] pos = new double[4][], nrm = new double[4][];
        public final double[][] uv = new double[4][];
        public int mat;
    }

    static final double[][] DIRS = { {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0} };

    static final int SIDES = 16, BEND_STEPS = 12;
    static final double R_PIPE = 3.0, R_BEND = 8.0, R_HUB = 4.2;
    static final double BAND_R = 3.6, BAND_W = 1.0;
    static final double FL_R = 4.8, FL_IN = 6.0;

    private final List<Quad> out = new ArrayList<>();
    private static final double[] C = {8, 8, 8};

    static double[] v(double x, double y, double z) { return new double[] {x, y, z}; }
    static double[] add(double[] a, double[] b) { return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]); }
    static double[] sub(double[] a, double[] b) { return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]); }
    static double[] mul(double[] a, double s) { return v(a[0] * s, a[1] * s, a[2] * s); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]); }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return l < 1e-12 ? v(0, 1, 0) : mul(a, 1 / l); }

    static double[][] frame(double[] t) {
        double[] ref = Math.abs(t[1]) < 0.9 ? v(0, 1, 0) : v(1, 0, 0);
        double[] u = norm(cross(t, ref));
        return new double[][] {u, cross(t, u)};
    }

    private void quad(double[][] p, double[][] n, double[][] uv, double[] facing, int mat) {
        double[] nw = v(0, 0, 0);
        for (int i = 0; i < 4; i++) {
            double[] a = p[i], b = p[(i + 1) % 4];
            nw[0] += (a[1] - b[1]) * (a[2] + b[2]);
            nw[1] += (a[2] - b[2]) * (a[0] + b[0]);
            nw[2] += (a[0] - b[0]) * (a[1] + b[1]);
        }
        Quad q = new Quad();
        q.mat = mat;
        boolean flip = dot(nw, facing) < 0;
        for (int i = 0; i < 4; i++) {
            int k = flip ? 3 - i : i;
            q.pos[i] = p[k]; q.nrm[i] = n[k]; q.uv[i] = uv[k];
        }
        out.add(q);
    }

    private static double[] ring(double[] c, double[][] f, double r, int i) {
        double a = 2 * Math.PI * i / SIDES + Math.PI / SIDES;
        return add(c, add(mul(f[0], r * Math.cos(a)), mul(f[1], r * Math.sin(a))));
    }

    private static double[] ringN(double[][] f, int i) {
        double a = 2 * Math.PI * i / SIDES + Math.PI / SIDES;
        return add(mul(f[0], Math.cos(a)), mul(f[1], Math.sin(a)));
    }

    private void tube(List<double[]> cs, List<double[][]> fs, double r, int mat) {
        double s = 0;
        for (int k = 0; k + 1 < cs.size(); k++) {
            double len = Math.sqrt(dot(sub(cs.get(k + 1), cs.get(k)), sub(cs.get(k + 1), cs.get(k))));
            double u0 = s / 16.0, u1 = (s + len) / 16.0;
            s += len;
            for (int i = 0; i < SIDES; i++) {
                double[][] p = {ring(cs.get(k), fs.get(k), r, i), ring(cs.get(k), fs.get(k), r, i + 1),
                        ring(cs.get(k + 1), fs.get(k + 1), r, i + 1), ring(cs.get(k + 1), fs.get(k + 1), r, i)};
                double[][] n = {ringN(fs.get(k), i), ringN(fs.get(k), i + 1), ringN(fs.get(k + 1), i + 1), ringN(fs.get(k + 1), i)};
                double v0 = (double) i / SIDES, v1 = (double) (i + 1) / SIDES;
                double[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
                quad(p, n, uv, add(add(n[0], n[1]), add(n[2], n[3])), mat);
            }
        }
    }

    private void straight(double[] a, double[] b, double r, int mat) {
        double[][] f = frame(norm(sub(b, a)));
        tube(List.of(a, b), List.of(f, f), r, mat);
    }

    private void annulus(double[] c, double[] n, double rIn, double rOut, int mat) {
        double[][] f = frame(n);
        for (int i = 0; i < SIDES; i++) {
            double[][] p = {ring(c, f, rIn, i), ring(c, f, rOut, i), ring(c, f, rOut, i + 1), ring(c, f, rIn, i + 1)};
            double[][] uv = new double[4][];
            for (int k = 0; k < 4; k++) {
                double[] d = sub(p[k], c);
                uv[k] = new double[] {0.5 + dot(d, f[0]) / 16.0, 0.5 + dot(d, f[1]) / 16.0};
            }
            quad(p, new double[][] {n, n, n, n}, uv, n, mat);
        }
    }

    private void band(double[] t, double s0, double s1, double r, double rIn, int mat, boolean innerFace, boolean outerFace) {
        double[] a = add(C, mul(t, s0)), b = add(C, mul(t, s1));
        straight(a, b, r, mat);
        if (innerFace) annulus(a, mul(t, -1), rIn, r, mat);
        if (outerFace) annulus(b, t, rIn, r, mat);
    }

    private void box(double[] c, double[][] ax, double[] h, int mat) {
        for (int k = 0; k < 3; k++) {
            for (int sg = -1; sg <= 1; sg += 2) {
                double[] n = mul(ax[k], sg);
                double[] fc = add(c, mul(n, h[k]));
                double[] e1 = mul(ax[(k + 1) % 3], h[(k + 1) % 3]), e2 = mul(ax[(k + 2) % 3], h[(k + 2) % 3]);
                double[][] p = {sub(sub(fc, e1), e2), add(sub(fc, e1), e2), add(add(fc, e1), e2), sub(add(fc, e1), e2)};
                double[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
                quad(p, new double[][] {n, n, n, n}, uv, n, mat);
            }
        }
    }

    private void sphere(double[] c, double r, int mat) {
        int stacks = 10;
        double[][] f = {v(1, 0, 0), v(0, 0, 1)};
        for (int k = 0; k < stacks; k++) {
            double a0 = -Math.PI / 2 + Math.PI * k / stacks, a1 = -Math.PI / 2 + Math.PI * (k + 1) / stacks;
            for (int i = 0; i < SIDES; i++) {
                double[][] n = {sph(f, a0, i), sph(f, a0, i + 1), sph(f, a1, i + 1), sph(f, a1, i)};
                double[][] p = new double[4][];
                for (int q = 0; q < 4; q++) p[q] = add(c, mul(n[q], r));
                double[][] uv = {{(double) i / SIDES, (double) k / stacks}, {(double) (i + 1) / SIDES, (double) k / stacks},
                        {(double) (i + 1) / SIDES, (double) (k + 1) / stacks}, {(double) i / SIDES, (double) (k + 1) / stacks}};
                quad(p, n, uv, add(add(n[0], n[1]), add(n[2], n[3])), mat);
            }
        }
    }

    private static double[] sph(double[][] f, double lat, int i) {
        double a = 2 * Math.PI * i / SIDES;
        return add(mul(add(mul(f[0], Math.cos(a)), mul(f[1], Math.sin(a))), Math.cos(lat)), v(0, Math.sin(lat), 0));
    }

    private void arm(double[] t, double s0, double s1) { straight(add(C, mul(t, s0)), add(C, mul(t, s1)), R_PIPE, PIPE); }

    private void bend(double[] a, double[] b) {
        double[] centre = add(C, add(mul(a, R_BEND), mul(b, R_BEND)));
        double[] u = norm(cross(a, b));
        List<double[]> cs = new ArrayList<>();
        List<double[][]> fs = new ArrayList<>();
        for (int k = 0; k <= BEND_STEPS; k++) {
            double th = Math.PI / 2 * k / BEND_STEPS;
            double[] p = sub(centre, mul(add(mul(b, Math.cos(th)), mul(a, Math.sin(th))), R_BEND));
            double[] t = sub(mul(b, Math.sin(th)), mul(a, Math.cos(th)));
            cs.add(p);
            fs.add(new double[][] {u, cross(t, u)});
        }
        tube(cs, fs, R_PIPE, PIPE);
    }

    private void fitting(double[] t, int kind) {
        if (kind == 1) {
            band(t, 8 - BAND_W, 8, BAND_R, R_PIPE, COLLAR, true, false);
        } else if (kind == 2) {
            band(t, FL_IN, 7.0, FL_R, R_PIPE, COLLAR, true, false);
            band(t, 7.0, 7.2, FL_R - 0.35, R_PIPE, DARK, false, false);
            band(t, 7.2, 8.0, FL_R, R_PIPE, COLLAR, false, true);
            double[][] f = frame(t);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + Math.PI / 8;
                double[] rad = add(mul(f[0], Math.cos(a)), mul(f[1], Math.sin(a)));
                double[] tan = cross(t, rad);
                double[] c = add(C, add(mul(t, FL_IN - 0.35), mul(rad, 3.95)));
                box(c, new double[][] {t, rad, tan}, new double[] {0.36, 0.55, 0.55}, BOLT);
            }
        }
    }

    private void blindCap(double[] t, double s) {
        double[] o = mul(t, -1);
        band(o, -s, -s + 1.5, 4.0, 0, COLLAR, true, true);
        band(o, -s + 1.5, -s + 2.0, 1.4, 0, COLLAR, false, true);
        double[][] f = frame(o);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            double[] rad = add(mul(f[0], Math.cos(a)), mul(f[1], Math.sin(a)));
            box(add(C, add(mul(o, -s + 1.5 + 0.3), mul(rad, 2.8))), new double[][] {o, rad, cross(o, rad)}, new double[] {0.3, 0.5, 0.5}, BOLT);
        }
    }

    public static List<Quad> build(int[] conn) {
        PipeMesh m = new PipeMesh();
        m.make(conn);
        return m.out;
    }

    public static final int WHEEL_ON = 4, WHEEL_OFF = 5, ARROW = 6;

    public static List<Quad> valve(int facing, int front, int back, boolean open, boolean oneWay) {
        PipeMesh m = new PipeMesh();
        int[] conn = new int[6];
        conn[facing] = Math.max(1, front);
        conn[facing ^ 1] = Math.max(1, back);
        m.make(conn);
        double[] t = DIRS[facing];
        double[] up = Math.abs(t[1]) > 0.5 ? v(0, 0, -1) : v(0, 1, 0);
        double[] side = cross(t, up);
        m.box(C, new double[][] {t, up, side}, new double[] {3.5, 4.0, 4.0}, COLLAR);
        m.straight(add(C, mul(up, 4.0)), add(C, mul(up, 6.2)), 0.6, BOLT);
        double[] wc = add(C, mul(up, 6.2));
        List<double[]> cs = new ArrayList<>();
        List<double[][]> fs = new ArrayList<>();
        for (int k = 0; k <= 24; k++) {
            double a = 2 * Math.PI * k / 24;
            double[] rad = add(mul(t, Math.cos(a)), mul(side, Math.sin(a)));
            cs.add(add(wc, mul(rad, 2.6)));
            fs.add(new double[][] {rad, up});
        }
        int wheel = open ? WHEEL_ON : WHEEL_OFF;
        m.tube(cs, fs, 0.45, wheel);
        m.box(wc, new double[][] {t, up, side}, new double[] {2.4, 0.3, 0.3}, wheel);
        m.box(wc, new double[][] {t, up, side}, new double[] {0.3, 0.3, 2.4}, wheel);
        if (oneWay) {
            for (int sg = -1; sg <= 1; sg += 2) {
                double[] s = mul(side, sg);
                double[] base = add(C, mul(s, 4.05));
                m.box(add(base, mul(t, -0.8)), new double[][] {t, up, s}, new double[] {1.6, 0.45, 0.06}, ARROW);
                for (int k = 0; k < 4; k++) {
                    double w = 1.6 - k * 0.45;
                    m.box(add(base, mul(t, 1.0 + k * 0.45)), new double[][] {t, up, s}, new double[] {0.24, w, 0.06}, ARROW);
                }
            }
        }
        return m.out;
    }

    private void make(int[] conn) {
        List<Integer> cs = new ArrayList<>();
        for (int d = 0; d < 6; d++) if (conn[d] != 0) cs.add(d);
        int n = cs.size();
        if (n == 0) {
            double[] t = DIRS[3];
            arm(t, -6.0, 6.0);
            blindCap(mul(t, -1), 6.0);
            blindCap(t, 6.0);
            return;
        }
        if (n == 1) {
            double[] t = DIRS[cs.get(0)];
            arm(t, -0.8, 8.0);
            blindCap(t, -0.8);
            fitting(t, conn[cs.get(0)]);
            return;
        }
        int a = cs.get(0), b = cs.get(1);
        if (n == 2 && (a ^ 1) == b) {
            arm(DIRS[a], 0, 8);
            arm(DIRS[b], 0, 8);
        } else if (n == 2) {
            bend(DIRS[a], DIRS[b]);
        } else {
            int through = -1;
            for (int d : cs) if (d % 2 == 0 && conn[d + 1] != 0) { through = d; break; }
            if (n == 3 && through >= 0) {
                arm(DIRS[through], 0, 8);
                arm(DIRS[through + 1], 0, 8);
                for (int d : cs) {
                    if (d == through || d == through + 1) continue;
                    arm(DIRS[d], 2.0, 8);
                    band(DIRS[d], 2.6, 4.0, 3.75, R_PIPE, COLLAR, true, true);
                }
            } else {
                sphere(C, R_HUB, COLLAR);
                for (int d : cs) arm(DIRS[d], 3.0, 8);
            }
        }
        for (int d : cs) fitting(DIRS[d], conn[d]);
    }
}
