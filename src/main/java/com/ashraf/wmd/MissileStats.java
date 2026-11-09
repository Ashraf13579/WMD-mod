package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class MissileStats {

    private MissileStats() {}

    private static int t(int grade) { return Mth.clamp(grade, 1, 4); }

    public static double weight(MissileBuild b) {
        double motor = b.divert() ? MissileConfig.weightDivert() : MissileConfig.weightMotor(b.motor());
        return MissileConfig.weightAirframe(b.airframe()) + motor + MissileConfig.weightGuidance(b.guidance()) + MissileConfig.weightWarhead(b.warheadType()) + stageWeight(b.staged(), b.motor(), b.boosters());
    }

    private static double stageWeight(boolean staged, int motor, int boosters) {
        return (staged ? Balance.STAGE_WEIGHT_SHARE * (MissileConfig.weightAirframe(4) + MissileConfig.weightMotor(t(motor))) : 0.0) + boosters * Balance.BOOSTER_WEIGHT;
    }

    public static double standardWeight(int motorTier) { return standardWeight(motorTier, false); }

    public static double standardWeight(int motorTier, boolean staged) {
        int m = t(motorTier);
        return MissileConfig.weightAirframe(m) + MissileConfig.weightMotor(m) + MissileConfig.weightGuidance(m) + MissileConfig.weightWarhead("he") + stageWeight(staged, m, staged ? Balance.BOOSTER_STD : 0);
    }

    public static int range(MissileBuild b) {
        if (b.isCruise()) return (int) Balance.CRUISE_RANGE[Mth.clamp(b.airframe(), 1, Balance.CRUISE_RANGE.length) - 1];
        int m = b.divert() ? 3 : t(b.motor());
        double ratio = Mth.clamp(Math.pow(standardWeight(m, b.staged()) / Math.max(0.01, weight(b)), MissileConfig.exponent()), MissileConfig.rangeMin(), MissileConfig.rangeMax());
        double r = (b.isSlbm() ? Balance.SLBM_RANGE_K : 1.0) * MissileConfig.baseRange(m) * ratio * (b.divert() ? MissileConfig.divertRange() : 1.0) * (b.hypersonic() ? Balance.HYPER_RANGE : 1.0) * (b.staged() ? 1.0 - Balance.BOOSTER_RANGE_LOSS * Math.max(0, Balance.BOOSTER_STD - b.boosters()) : 1.0);
        return (int) (Math.round(r / 10.0) * 10);
    }

    public static int fuel(MissileBuild b) {
        if (b.isCruise()) return Balance.CRUISE_FUEL_CANISTERS * Balance.ALKYLATE_CANISTER_MB;
        if (b.isSlbm()) return 0;
        double mb = MissileConfig.fuelBase() + MissileConfig.fuelPerBlock() * range(b) + Balance.BOOSTER_FUEL_MB * b.boosters();
        return (int) (Math.round(mb / 10.0) * 10);
    }

    public static int fuelTicks(MissileBuild b, int padTier) {
        return Math.max(20, (int) Math.ceil(fuel(b) / MissileConfig.fuelRate(padTier)));
    }

    public static double speed(MissileBuild b) {
        if (b.isCruise()) return Balance.CRUISE_SPEED / Flight.V_CRUISE;
        if (b.hypersonic()) return MissileConfig.speed(3) * Balance.HYPER_SPEED;
        return MissileConfig.speed(b.divert() ? 3 : t(b.motor())) * (b.staged() ? (1.0 + Balance.BOOSTER_THRUST_SPEED * b.boosters()) * (1.0 - Balance.BOOSTER_SPEED_LOSS * Math.max(0, Balance.BOOSTER_STD - b.boosters())) : 1.0); }

    public static int health(MissileBuild b) { return (int) MissileConfig.health(b.airframe()); }
}
