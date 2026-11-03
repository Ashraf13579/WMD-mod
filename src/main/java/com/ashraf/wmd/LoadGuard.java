package com.ashraf.wmd;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class LoadGuard {

    private LoadGuard() {}

    public static final int NORMAL = 0, BUSY = 1, OVERLOADED = 2;

    private static int level;
    private static double mspt;

    public static int level() { return level; }

    public static double mspt() { return mspt; }

    static void clear() { level = NORMAL; mspt = 0.0; }

    public static int scale(int full, double overloaded) {
        return switch (level) {
            case BUSY -> Math.max(0, full / 2);
            case OVERLOADED -> Math.max(0, (int) Math.floor(full * overloaded));
            default -> full;
        };
    }

    public static boolean overloaded() { return level == OVERLOADED; }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        if (!Config.Server.loadGuard()) { if (level != NORMAL) { level = NORMAL; Wmd.LOGGER.info("[load-guard] off (config): normal"); } return; }
        mspt = server.getAverageTickTimeNanos() / 1.0E6;
        double busy = Config.Server.loadGuardBusyMs(), over = Config.Server.loadGuardOverloadMs(), back = Balance.LOADGUARD_RECOVER;
        int next = level;
        if (mspt >= over) next = OVERLOADED;
        else if (mspt >= busy) next = level == OVERLOADED && mspt > over * back ? OVERLOADED : BUSY;
        else if (level == BUSY && mspt > busy * back) next = BUSY;
        else if (level == OVERLOADED && mspt > over * back) next = OVERLOADED;
        else next = mspt > busy * back && level != NORMAL ? BUSY : NORMAL;
        if (next != level) {
            Wmd.LOGGER.info("[load-guard] {} -> {} (avg tick {} ms)", name(level), name(next), String.format("%.1f", mspt));
            level = next;
        }
    }

    public static String name(int l) { return l == OVERLOADED ? "overloaded" : l == BUSY ? "busy" : "normal"; }
}
