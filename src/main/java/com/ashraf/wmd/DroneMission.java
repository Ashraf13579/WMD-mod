package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;

public record DroneMission(int[] xs, int[] zs, int tx, int tz, boolean hasTarget) {

    public static final int WAYPOINTS = 4;
    public static final DroneMission NONE = new DroneMission(new int[0], new int[0], 0, 0, false);

    static DroneMission of(int[] d) {
        if (d == null || d.length < 3) return null;
        int n = d[0];
        if (n < 0 || n > WAYPOINTS || d.length != 3 + 2 * n) return null;
        int[] xs = new int[n], zs = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = d[1 + 2 * i]; zs[i] = d[2 + 2 * i]; }
        return new DroneMission(xs, zs, d[1 + 2 * n], d[2 + 2 * n], true);
    }

    DroneMission withTarget(int x, int z) { return new DroneMission(xs, zs, x, z, true); }

    int length(double x, double z) {
        double l = 0.0, px = x, pz = z;
        for (int i = 0; i < xs.length; i++) { l += Math.hypot(xs[i] + 0.5 - px, zs[i] + 0.5 - pz); px = xs[i] + 0.5; pz = zs[i] + 0.5; }
        return (int) Math.round(l + Math.hypot(tx + 0.5 - px, tz + 0.5 - pz));
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putIntArray("xs", xs); t.putIntArray("zs", zs); t.putInt("tx", tx); t.putInt("tz", tz); t.putBoolean("has", hasTarget);
        return t;
    }

    static DroneMission load(CompoundTag t) {
        if (t == null || !t.getBoolean("has")) return NONE;
        int[] xs = t.getIntArray("xs"), zs = t.getIntArray("zs");
        int n = Math.min(WAYPOINTS, Math.min(xs.length, zs.length));
        return new DroneMission(java.util.Arrays.copyOf(xs, n), java.util.Arrays.copyOf(zs, n), t.getInt("tx"), t.getInt("tz"), true);
    }
}
