package com.ashraf.wmd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WorkBudget {

    private WorkBudget() {}

    private static long budget = (long) (Balance.WORK_MS_MAX * 1.0E6), spent;

    @SubscribeEvent
    static void onTick(ServerTickEvent.Pre event) {
        double mspt = event.getServer().getAverageTickTimeNanos() / 1.0E6;
        double u = Math.max(0.0, Math.min(1.0, (mspt - Balance.WORK_MSPT_LOW) / (Balance.WORK_MSPT_HIGH - Balance.WORK_MSPT_LOW)));
        budget = (long) ((Balance.WORK_MS_MAX + (Balance.WORK_MS_MIN - Balance.WORK_MS_MAX) * u) * 1.0E6);
        spent = 0L;
    }

    static long start() { return System.nanoTime(); }

    static boolean more(long t0) { return spent + (System.nanoTime() - t0) < budget; }

    static void done(long t0) { spent += System.nanoTime() - t0; }

    static double ms() { return budget / 1.0E6; }
}
