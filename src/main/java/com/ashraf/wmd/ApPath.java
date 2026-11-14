package com.ashraf.wmd;

final class ApPath {

    final int s1, s2;
    final double c1x, c1z, c2x, c2z, t1x, t1z, t2x, t2z, segHdg, r, arc1, straight, arc2;

    int seg;

    private ApPath(int s1, int s2, double c1x, double c1z, double c2x, double c2z, double t1x, double t1z, double t2x, double t2z, double segHdg, double r,
                   double arc1, double straight, double arc2) {
        this.s1 = s1; this.s2 = s2; this.c1x = c1x; this.c1z = c1z; this.c2x = c2x; this.c2z = c2z; this.t1x = t1x; this.t1z = t1z; this.t2x = t2x; this.t2z = t2z;
        this.segHdg = segHdg; this.r = r; this.arc1 = arc1; this.straight = straight; this.arc2 = arc2;
    }

    double length() { return r * Math.toRadians(arc1 + arc2) + straight; }

    String name() { return (s1 > 0 ? "R" : "L") + "S" + (s2 > 0 ? "R" : "L"); }

    static double hdgOf(double dx, double dz) { return (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0; }

    private static double mod360(double a) { return ((a % 360.0) + 360.0) % 360.0; }

    static double turn(double a, double b, int s) { return s > 0 ? mod360(b - a) : mod360(a - b); }

    static ApPath plan(double px, double pz, double h0, double fx, double fz, double hf, double r) {
        if (!(r > 0.5)) return null;
        double d0x = Math.sin(Math.toRadians(h0)), d0z = -Math.cos(Math.toRadians(h0)), dfx = Math.sin(Math.toRadians(hf)), dfz = -Math.cos(Math.toRadians(hf));
        ApPath best = null;
        for (int s1 = -1; s1 <= 1; s1 += 2) for (int s2 = -1; s2 <= 1; s2 += 2) {
            double c1x = px + s1 * -d0z * r, c1z = pz + s1 * d0x * r;
            double c2x = fx + s2 * -dfz * r, c2z = fz + s2 * dfx * r;
            double vx = c2x - c1x, vz = c2z - c1z, dd = Math.hypot(vx, vz);
            double dx, dz, straight;
            if (s1 == s2) {
                if (dd < 1.0E-6) { dx = d0x; dz = d0z; straight = 0.0; }
                else { dx = vx / dd; dz = vz / dd; straight = dd; }
            } else {
                if (dd < 2.0 * r) continue;
                straight = Math.sqrt(dd * dd - 4.0 * r * r);
                double th = Math.atan2(2.0 * r, straight) * (s1 > 0 ? 1.0 : -1.0);
                double ux = vx / dd, uz = vz / dd, rx = -uz, rz = ux;
                dx = Math.cos(th) * ux + Math.sin(th) * rx; dz = Math.cos(th) * uz + Math.sin(th) * rz;
            }
            double rdx = -dz, rdz = dx;
            double t1x = c1x - s1 * rdx * r, t1z = c1z - s1 * rdz * r, t2x = c2x - s2 * rdx * r, t2z = c2z - s2 * rdz * r;
            double seg = hdgOf(dx, dz);
            ApPath p = new ApPath(s1, s2, c1x, c1z, c2x, c2z, t1x, t1z, t2x, t2z, seg, r, turn(h0, seg, s1), straight, turn(seg, hf, s2));
            if (best == null || p.length() < best.length()) best = p;
        }
        return best;
    }

    double[][] points(double step) {
        java.util.ArrayList<double[]> out = new java.util.ArrayList<>();
        arcPoints(out, c1x, c1z, hdgOf(t1x - c1x, t1z - c1z) - s1 * arc1, s1, arc1, step);
        int n = (int) Math.max(1, Math.ceil(straight / step));
        for (int i = 0; i <= n; i++) out.add(new double[] { t1x + (t2x - t1x) * i / n, t1z + (t2z - t1z) * i / n });
        arcPoints(out, c2x, c2z, hdgOf(t2x - c2x, t2z - c2z), s2, arc2, step);
        return out.toArray(new double[0][]);
    }

    private void arcPoints(java.util.List<double[]> out, double cx, double cz, double b0, int s, double arcDeg, double step) {
        int n = (int) Math.max(1, Math.ceil(Math.toRadians(arcDeg) * r / step));
        for (int i = 0; i <= n; i++) {
            double b = Math.toRadians(b0 + s * arcDeg * i / n);
            out.add(new double[] { cx + Math.sin(b) * r, cz - Math.cos(b) * r });
        }
    }

    static double arcHeading(double x, double z, double cx, double cz, double r, int s, double kCorr) {
        double dx = x - cx, dz = z - cz, d = Math.hypot(dx, dz);
        double b = hdgOf(dx, dz);
        double corr = Math.max(-45.0, Math.min(45.0, kCorr * (d - r) / Math.max(1.0, r)));
        return s > 0 ? b + 90.0 + corr : b - 90.0 - corr;
    }
}
