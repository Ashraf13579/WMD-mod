package com.ashraf.wmd;

public final class FlashBlindView {

    private FlashBlindView() {}

    public static volatile float strength;
    public static volatile long startMs, lengthMs;

    public static void set(FlashBlindPayload p) {
        long now = net.minecraft.Util.getMillis();
        if (alpha(now) > p.strength()) return;
        strength = p.strength();
        startMs = now;
        lengthMs = p.ticks() * 50L;
    }

    public static float alpha(long now) {
        long t = now - startMs;
        if (lengthMs <= 0 || t < 0 || t >= lengthMs) return 0.0F;
        float k = t / (float) lengthMs;
        if (k < 0.25F) return strength;
        float f = 1.0F - (k - 0.25F) / 0.75F;
        return strength * f * f;
    }

    public static void clear() { strength = 0.0F; lengthMs = 0L; }
}
