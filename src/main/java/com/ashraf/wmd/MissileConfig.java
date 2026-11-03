package com.ashraf.wmd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MissileConfig {

    private MissileConfig() {}

    static final class Values {
        ModConfigSpec.ConfigValue<List<? extends Number>> airframe, motor, guidance, baseRange, speed, health, fuelRate;
        ModConfigSpec.DoubleValue divertWeight, divertRange, exponent, rangeMin, rangeMax, fuelBase, fuelPerBlock;
        final Map<String, ModConfigSpec.DoubleValue> warhead = new LinkedHashMap<>();
    }

    private static ModConfigSpec.ConfigValue<List<? extends Number>> list(ModConfigSpec.Builder b, String key, String comment, double[] def) {
        return b.comment(comment).defineList(key, java.util.Arrays.stream(def).boxed().toList(), () -> 1.0, o -> o instanceof Number n && n.doubleValue() >= 0.0);
    }

    static Values define(ModConfigSpec.Builder b) {
        Values v = new Values();
        b.comment("Missile stats: weight, range, speed, health, jet fuel (docs/machine_balance.md 'Missile stats').").push("missiles");
        b.comment("Weights of the parts (arbitrary units).").push("weights");
        v.airframe = list(b, "airframe", "Airframe weight for tier 1, 2, 3.", Balance.WEIGHT_AIRFRAME);
        v.motor = list(b, "motor", "Motor weight for tier 1, 2, 3.", Balance.WEIGHT_MOTOR);
        v.guidance = list(b, "guidance", "Guidance weight for tier 1, 2, 3.", Balance.WEIGHT_GUIDANCE);
        v.divertWeight = b.comment("Weight of the divert motor.").defineInRange("divertMotor", Balance.WEIGHT_DIVERT, 0.0, 100.0);
        b.comment("Weight of each warhead type.").push("warheads");
        for (WarheadType t : WarheadType.values()) v.warhead.put(t.id, b.defineInRange(t.id, Balance.warheadWeight(t.id), 0.0, 100.0));
        b.pop().pop();
        b.comment("Range in blocks: the motor tier sets the base; weight lowers or raises it.").push("range");
        v.baseRange = list(b, "baseRange", "Base range (blocks) of a standard build with a tier 1, 2, 3 motor.", Balance.BASE_RANGE);
        v.exponent = b.comment("range = base x (standard weight / weight) ^ exponent. Higher = weight matters more.").defineInRange("weightExponent", Balance.RANGE_EXPONENT, 0.0, 5.0);
        v.rangeMin = b.comment("Lowest share of the base range (a very heavy build).").defineInRange("minShare", Balance.RANGE_MIN, 0.0, 1.0);
        v.rangeMax = b.comment("Highest share of the base range (a very light build).").defineInRange("maxShare", Balance.RANGE_MAX, 1.0, 5.0);
        v.divertRange = b.comment("Share of the range the divert motor keeps.").defineInRange("divertShare", Balance.DIVERT_RANGE, 0.0, 1.0);
        b.pop();
        b.comment("Cruise speed factor of a tier 1, 2, 3 motor (1 = the old fixed speed).").push("speed");
        v.speed = list(b, "speed", "Speed factor for motor tier 1, 2, 3.", Balance.SPEED);
        b.pop();
        b.comment("Airframe health for tier 1, 2, 3 (used by CIWS / interceptors later).").push("health");
        v.health = list(b, "health", "Health for airframe tier 1, 2, 3.", Balance.HEALTH);
        b.pop();
        b.comment("Jet fuel one missile takes: fuelBase + fuelPerBlock x its range in blocks (mB).").push("fuel");
        v.fuelBase = b.defineInRange("fuelBase", Balance.FUEL_BASE_MB, 0.0, 100_000.0);
        v.fuelPerBlock = b.defineInRange("fuelPerBlock", Balance.FUEL_PER_BLOCK, 0.0, 100.0);
        v.fuelRate = list(b, "padRate", "How fast a pad of tier 1, 2, 3 fills the missile with jet fuel (mB per tick). The fuelling stage lasts fuel needed / rate.", Balance.FUEL_RATE);
        b.pop().pop();
        return v;
    }

    private static boolean loaded() { return Config.SPEC.isLoaded(); }

    private static double at(ModConfigSpec.ConfigValue<List<? extends Number>> v, double[] def, int tier) {
        int i = Mth.clamp(tier, 1, 4) - 1;
        if (!loaded()) return def[Math.min(i, def.length - 1)];
        List<? extends Number> l = v.get();
        return i < l.size() ? l.get(i).doubleValue() : def[Math.min(i, def.length - 1)];
    }

    public static double weightAirframe(int tier) { return at(Config.MISSILES.airframe, Balance.WEIGHT_AIRFRAME, tier); }
    public static double weightMotor(int tier) { return at(Config.MISSILES.motor, Balance.WEIGHT_MOTOR, tier); }
    public static double weightGuidance(int tier) { return at(Config.MISSILES.guidance, Balance.WEIGHT_GUIDANCE, tier); }
    public static double baseRange(int tier) { return at(Config.MISSILES.baseRange, Balance.BASE_RANGE, tier); }
    public static double fuelRate(int padTier) { return Math.max(0.1, at(Config.MISSILES.fuelRate, Balance.FUEL_RATE, padTier)); }
    public static double speed(int tier) { return at(Config.MISSILES.speed, Balance.SPEED, tier); }
    public static double health(int tier) { return at(Config.MISSILES.health, Balance.HEALTH, tier); }
    public static double weightDivert() { return loaded() ? Config.MISSILES.divertWeight.get() : Balance.WEIGHT_DIVERT; }
    public static double weightWarhead(String id) {
        ModConfigSpec.DoubleValue v = Config.MISSILES.warhead.get(id);
        return loaded() && v != null ? v.get() : Balance.warheadWeight(id);
    }
    public static double exponent() { return loaded() ? Config.MISSILES.exponent.get() : Balance.RANGE_EXPONENT; }
    public static double rangeMin() { return loaded() ? Config.MISSILES.rangeMin.get() : Balance.RANGE_MIN; }
    public static double rangeMax() { return loaded() ? Config.MISSILES.rangeMax.get() : Balance.RANGE_MAX; }
    public static double divertRange() { return loaded() ? Config.MISSILES.divertRange.get() : Balance.DIVERT_RANGE; }
    public static double fuelBase() { return loaded() ? Config.MISSILES.fuelBase.get() : Balance.FUEL_BASE_MB; }
    public static double fuelPerBlock() { return loaded() ? Config.MISSILES.fuelPerBlock.get() : Balance.FUEL_PER_BLOCK; }
}
