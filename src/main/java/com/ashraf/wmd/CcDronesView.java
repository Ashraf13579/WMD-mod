package com.ashraf.wmd;

import java.util.List;
import net.minecraft.core.BlockPos;

public final class CcDronesView {

    private CcDronesView() {}

    private static volatile CcDronesPayload last;

    public static void set(CcDronesPayload p) { last = p; }

    public static List<CcDronesPayload.Row> drones(BlockPos table) { CcDronesPayload p = last; return p != null && p.table().equals(table) ? p.drones() : List.of(); }

    public static List<CcDronesPayload.Site> sites(BlockPos table) { CcDronesPayload p = last; return p != null && p.table().equals(table) ? p.sites() : List.of(); }

    public static void clear() { last = null; }

    static String doing(int kind, int phase) {
        if (phase == Drones.FALLING) return "down";
        if (kind >= 3) return switch (phase) {
            case BigDrones.DROP, BigDrones.LOITER -> "target";
            case BigDrones.RETURN, BigDrones.CHUTE, BigDrones.APPROACH, BigDrones.LANDING, BigDrones.TAXI_IN -> "home";
            default -> "out";
        };
        if (kind >= 1) return switch (phase) {
            case StrikeDrones.HUNT, StrikeDrones.STRIKE -> "target";
            case StrikeDrones.HOME, StrikeDrones.DOCK -> "home";
            case StrikeDrones.PILOT -> "flown";
            default -> "out";
        };
        return switch (phase) { case Drones.LOITER -> "target"; case Drones.RETURN, Drones.LANDING -> "home"; default -> "out"; };
    }
}
