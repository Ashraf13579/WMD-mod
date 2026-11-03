package com.ashraf.wmd;

public final class GpsView {

    private GpsView() {}

    public static final long STALE_MS = 5_000L;

    private static volatile GpsPayload last = GpsPayload.OFF;
    private static volatile long receivedMs;

    public static void set(GpsPayload p) {
        last = p;
        receivedMs = System.currentTimeMillis();
    }

    public static void clear() { last = GpsPayload.OFF; receivedMs = 0L; }

    public static GpsPayload live() {
        GpsPayload p = last;
        return p.on() && System.currentTimeMillis() - receivedMs < STALE_MS ? p : null;
    }
}
