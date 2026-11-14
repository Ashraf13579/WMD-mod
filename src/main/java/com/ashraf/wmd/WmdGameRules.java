package com.ashraf.wmd;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class WmdGameRules {

    private WmdGameRules() {}

    public static final GameRules.Key<GameRules.BooleanValue> AIRCRAFT_CRASH_DAMAGE =
            GameRules.register("wmdAircraftCrashDamage", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    static void init() {}

    static boolean crashDamage(Level level) {
        try { return level == null || level.getGameRules().getBoolean(AIRCRAFT_CRASH_DAMAGE); } catch (Throwable t) { return true; }
    }
}
