package com.ashraf.wmd;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Missiles {

    private Missiles() {}

    public static final int COUNTDOWN_TICKS = 200;
    public static final int IGNITION_TICKS = 20;
    public static final int LAUNCH_TICKS = 260;
    public static final int COOLDOWN_TICKS = 1200;
    public static final int MIN_DISTANCE = 64;
    public static final int EXHAUST_RADIUS = 15;
    public static final int EXHAUST_TICKS = 140;

    public static int height(int tier) { return switch (tier) { case 1 -> 10; case 2 -> 15; case 3 -> 20; default -> 34; }; }

    public static int radiusUnits(int tier) { return switch (tier) { case 1 -> 9; case 2 -> 12; default -> 16; }; }

    public static int prepTicks(int tier) { return switch (tier) { case 1 -> 600; case 2 -> 1200; case 3 -> 2400; default -> 4800; }; }
    public static int loadTicks(int tier) { return prepTicks(tier) * 2 / 5; }
    public static int fuelTicks(int tier) { return prepTicks(tier) - loadTicks(tier); }

    public static int fuelNeeded(int tier) { return Balance.MISSILE_FUEL_MB[Math.max(0, Math.min(2, tier - 1))]; }

    public static int fePerTick(int tier) { return switch (tier) { case 1 -> 500; case 2 -> 1_000; case 3 -> 2_000; default -> 4_000; }; }

    public static float ascent(float ticksSinceIgnition) {
        float t = Math.max(0.0F, ticksSinceIgnition - IGNITION_TICKS) / 20.0F;
        if (t <= 5.0F) return 0.75F * t * t;
        float u = t - 5.0F;
        return 18.75F + 7.5F * u + 3.0F * u * u + 0.8F * u * u * u;
    }

    public static int launchTicks(boolean hyper) { return hyper ? IGNITION_TICKS + Balance.HYPER_BOOST_TICKS : LAUNCH_TICKS; }

    public static float ascent(float ticksSinceIgnition, boolean hyper) {
        if (!hyper) return ascent(ticksSinceIgnition);
        float t = Math.max(0.0F, ticksSinceIgnition - IGNITION_TICKS) / 20.0F;
        return 5.0F * t * t + 3.9F * t * t * t;
    }

    public static double craterRadius(int tier) { return switch (tier) { case 1 -> 10.0; case 2 -> 20.0; case 3 -> 40.0; default -> 80.0; }; }

    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof LaunchMissileItem m ? m.getTier() : 0;
    }

    public static Item itemFor(int tier) {
        return switch (tier) {
            case 1 -> Wmd.MISSILE_T1.get();
            case 2 -> Wmd.MISSILE_T2.get();
            default -> Wmd.MISSILE_T3.get();
        };
    }

    public static final float MOTOR_L = 36.0F, AIR_L = 84.0F, GUID_L = 14.0F, WAR_L = 27.0F;
    public static final float CORE_TOP = MOTOR_L * 2.5F + AIR_L * 2.5F, INTER_H = 40.0F, UP_SCALE = 0.62F, UP_AIR = AIR_L * 2.5F * UP_SCALE, UP_HEAD = 1.5F;
    public static final float STACK_TOP = CORE_TOP + INTER_H + UP_AIR + GUID_L * UP_HEAD + WAR_L * UP_HEAD;

    public static final float PAYLOAD_H = 84.6F, CAPSULE_H = 80.0F;

    public static float partsLength(int calibre) { return (MOTOR_L + AIR_L + GUID_L + WAR_L) * (float) BayCalibre.len(calibre) / 16.0F; }

    public static float stackLength() { return STACK_TOP / 16.0F; }

    public static float stackLength(MissileBuild b) { return Satellites.isSatellite(b) || StationKit.isKit(b) || Cargo.isCargo(b) ? (CORE_TOP + PAYLOAD_H) / 16.0F : Crew.isCrew(b) ? (CORE_TOP + CAPSULE_H) / 16.0F : stackLength(); }

    public static float lengthOf(MissileBuild b) { return b.staged() ? stackLength(b) : b.isRv() ? 1.4F : partsLength(b.calibre()); }
}
