package com.ashraf.wmd;

public final class StrikeToastView {

    private StrikeToastView() {}

    public static final long SHOW_MS = 6_000L;

    public static volatile StrikeToastPayload card;
    public static volatile long shownMs;

    public static volatile boolean fresh;

    public static void set(StrikeToastPayload p) {
        card = p;
        shownMs = net.minecraft.Util.getMillis();
        fresh = true;
    }

    public static StrikeToastPayload live() {
        StrikeToastPayload c = card;
        return c != null && net.minecraft.Util.getMillis() - shownMs < SHOW_MS ? c : null;
    }

    public static long age() { return net.minecraft.Util.getMillis() - shownMs; }

    public static void clear() { card = null; fresh = false; }
}
