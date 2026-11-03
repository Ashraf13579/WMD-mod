package com.ashraf.wmd;

import net.minecraft.client.Minecraft;

public final class ClientClock {

    private ClientClock() {}

    private static long mono = Long.MIN_VALUE;
    private static boolean held;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { mono = Long.MIN_VALUE; held = false; return; }
        long raw = mc.level.getGameTime();
        held = raw < mono;
        if (!held) mono = raw;
    }

    static long ticks() {
        Minecraft mc = Minecraft.getInstance();
        if (mono == Long.MIN_VALUE) return mc.level == null ? 0L : mc.level.getGameTime();
        return mono;
    }

    static double now(float partial) { return ticks() + (held ? 0.0 : partial); }
}
