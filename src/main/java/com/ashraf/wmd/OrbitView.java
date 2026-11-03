package com.ashraf.wmd;

import java.util.List;

public final class OrbitView {

    private OrbitView() {}

    private static volatile List<OrbitMath.Sat> sats = List.of();

    private static volatile long offsetMs;

    public static void set(OrbitPayload p) {
        sats = List.copyOf(p.sats());
        offsetMs = p.serverNowMs() - System.currentTimeMillis();
    }

    public static void clear() { sats = List.of(); offsetMs = 0L; }

    public static List<OrbitMath.Sat> sats() { return sats; }

    public static int count() {
        int n = 0;
        for (OrbitMath.Sat s : sats) if (!s.isCubesat()) n++;
        return n;
    }

    public static long now() { return System.currentTimeMillis() + offsetMs; }

    public static OrbitMath.Sat trackedBy(java.util.UUID owner, double x, double z) {
        if (owner == null) return null;
        long now = now();
        for (OrbitMath.Sat s : sats) if (owner.equals(s.owner()) && now < s.endMs() && OrbitMath.isOverhead(s, x, z, now)) return s;
        return null;
    }
}
