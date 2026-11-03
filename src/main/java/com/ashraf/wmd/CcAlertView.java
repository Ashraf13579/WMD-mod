package com.ashraf.wmd;

public final class CcAlertView {

    private CcAlertView() {}

    public static final long SHOW_MS = 8_000L;

    private static volatile CcAlertPayload last;
    private static volatile long receivedMs;

    public static volatile boolean fresh;

    public static void set(CcAlertPayload p) {
        last = p;
        receivedMs = System.currentTimeMillis();
        fresh = true;
    }

    public static CcAlertPayload live() {
        CcAlertPayload p = last;
        return p != null && System.currentTimeMillis() - receivedMs < SHOW_MS ? p : null;
    }

    public static long age() { return System.currentTimeMillis() - receivedMs; }

    public static void clear() { last = null; fresh = false; }
}
