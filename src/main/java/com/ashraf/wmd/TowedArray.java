package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.world.phys.Vec3;

public final class TowedArray {

    private TowedArray() {}

    public static final double CRUMB_STEP = 0.75;

    public static void crumb(ArrayDeque<Vec3> crumbs, Vec3 head, double keep) {
        Vec3 last = crumbs.peekFirst();
        if (last != null && last.distanceToSqr(head) < CRUMB_STEP * CRUMB_STEP) return;
        crumbs.addFirst(head);
        double s = 0.0;
        Vec3 prev = null;
        for (Iterator<Vec3> it = crumbs.iterator(); it.hasNext(); ) {
            Vec3 c = it.next();
            if (prev != null) s += prev.distanceTo(c);
            prev = c;
            if (s > keep) it.remove();
        }
    }

    public static List<Vec3> points(ArrayDeque<Vec3> crumbs, Vec3 head, Vec3 aft, double out, double sink) {
        List<Vec3> pts = new ArrayList<>();
        List<Double> arc = new ArrayList<>();
        pts.add(head);
        arc.add(0.0);
        double left = Math.max(0.0, out), s = 0.0;
        Vec3 prev = head, dir = null;
        for (Vec3 c : crumbs) {
            if (left <= 1.0E-3) break;
            Vec3 seg = c.subtract(prev);
            double len = seg.length();
            if (len < 1.0E-3) continue;
            dir = seg.scale(1.0 / len);
            if (left <= len) { prev = prev.add(dir.scale(left)); s += left; left = 0.0; pts.add(prev); arc.add(s); break; }
            prev = c; s += len; left -= len;
            pts.add(prev); arc.add(s);
        }
        if (left > 1.0E-3) {
            Vec3 d = dir != null ? new Vec3(dir.x, 0.0, dir.z) : new Vec3(aft.x, 0.0, aft.z);
            if (d.lengthSqr() < 1.0E-6) d = new Vec3(0.0, 0.0, 1.0);
            d = d.normalize();
            prev = prev.add(d.scale(left)); s += left;
            pts.add(prev); arc.add(s);
        }
        double total = Math.max(1.0, out);
        for (int i = 1; i < pts.size(); i++) {
            Vec3 p = pts.get(i);
            pts.set(i, new Vec3(p.x, p.y - sink * Math.min(1.0, arc.get(i) / total), p.z));
        }
        return pts;
    }

    public static Vec3 tail(List<Vec3> pts) { return pts.get(pts.size() - 1); }

    public static double headingDiff(double a, double b) { return Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0); }
}
