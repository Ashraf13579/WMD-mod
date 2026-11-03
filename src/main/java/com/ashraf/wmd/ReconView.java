package com.ashraf.wmd;

public final class ReconView {

    private ReconView() {}

    public static volatile ReconMapPayload last;

    public static volatile ReconMapPayload map;

    public static volatile long receivedMs;
    public static volatile int version;

    public static void set(ReconMapPayload p) {
        last = p;
        receivedMs = System.currentTimeMillis();
        if (p.hasMap() && p.n() > 0 && p.col().length == p.n() * p.n()) { map = p; version++; }
        else if (!p.hasTarget() || p.snapTime() == 0L) { if (map != null) { map = null; version++; } }
    }

    public static boolean stale() {
        ReconMapPayload l = last, m = map;
        return l != null && l.snapTime() != 0L && (m == null || m.snapTime() != l.snapTime());
    }

    public static void clear() { last = null; map = null; version++; }
}
