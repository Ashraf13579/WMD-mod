package com.ashraf.wmd;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ImpactProfiler {

    private ImpactProfiler() {}

    static final int CRATER = 0, SHOCK = 1, WAVE = 2, GLASS = 3, TASKS = 4, DEBRIS = 5, SNAPSHOT = 6, LIGHT = 7, RINGS = 8, COLLAPSE = 9, SHOCKWAVE = 10, N = 11;
    private static final String[] NAMES = { "crater", "shock ring", "pressure wave", "far glass", "other tasks", "debris", "snapshot", "lit sends",
            "damage rings", "collapse", "shockwave" };
    private static final double WATCH_R = 60.0, SLOW_MS = 45.0;
    private static final long[] NS = new long[N];
    private static int watchTicks;
    private static String what = "";

    static void watch(String type, double craterRadius) {
        if (craterRadius < WATCH_R) return;
        watchTicks = 1200;
        what = type + " r" + (int) craterRadius;
        java.util.Arrays.fill(NS, 0L);
    }

    static void watchAny(String type) {
        if (watchTicks <= 0) { what = type; java.util.Arrays.fill(NS, 0L); }
        watchTicks = 1200;
    }

    static boolean on() { return watchTicks > 0; }

    static long start() { return watchTicks > 0 ? System.nanoTime() : 0L; }

    private static final double SLOW_CALL_MS = 25.0;
    private static final long[] LAST_SLOW = new long[N];

    static void add(int part, long t0) {
        if (t0 == 0L) return;
        long dt = System.nanoTime() - t0;
        NS[part] += dt;
        if (dt > SLOW_CALL_MS * 1.0E6 && System.nanoTime() - LAST_SLOW[part] > 1_000_000_000L) {
            LAST_SLOW[part] = System.nanoTime();
            Wmd.LOGGER.warn("[impact-slow] {} took {} ms in one tick ({})", NAMES[part], dt / 1_000_000L, what);
        }
    }

    static void task(int kind, long t0) {
        add(kind == WarheadFx.SHOCK ? SHOCK : kind == WarheadFx.BLAST_WAVE ? WAVE : kind == WarheadFx.GLASS_SHOCK ? GLASS : TASKS, t0);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (watchTicks <= 0) return;
        watchTicks--;
        if (watchTicks % 20 != 0) return;
        MinecraftServer server = event.getServer();
        double mspt = server.getAverageTickTimeNanos() / 1.0E6;
        if (mspt > SLOW_MS) {
            int entities = 0, falling = 0, bodies = 0, items = 0;
            for (var l : server.getAllLevels()) {
                for (var e : l.getAllEntities()) {
                    entities++;
                    if (e instanceof net.minecraft.world.entity.item.FallingBlockEntity) falling++;
                    else if (e instanceof net.minecraft.world.entity.item.ItemEntity) items++;
                }
                bodies += Debris.liveCount(l);
            }
            long ours = 0L;
            for (long v : NS) ours += v;
            StringBuilder sb = new StringBuilder("[impact-prof] ").append(what).append(" | avg tick ").append(String.format("%.1f", mspt)).append(" ms | last second:");
            for (int i = 0; i < N; i++) if (NS[i] > 500_000L) sb.append(' ').append(NAMES[i]).append(' ').append(NS[i] / 1_000_000L).append(" ms,");

            sb.append(String.format(" | WMD %.1f ms/tick, rest %.1f ms/tick", ours / 20.0E6, Math.max(0.0, mspt - ours / 20.0E6)));
            sb.append(" | entities ").append(entities).append(", items ").append(items).append(", falling blocks ").append(falling).append(", Sable debris bodies ").append(bodies);
            Wmd.LOGGER.info(sb.toString());
        }
        java.util.Arrays.fill(NS, 0L);
    }
}
