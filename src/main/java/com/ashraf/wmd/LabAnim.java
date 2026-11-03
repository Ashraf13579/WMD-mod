package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class LabAnim {

    private LabAnim() {}

    public enum Item { NONE, INGOTS, PLATES, SHELL, CHARGED, WARHEAD }

    public static final class Pose {
        public double car, arm, f, wheelDeg, reach = 24.0, turn;
        public Item carry = Item.NONE, cell = Item.NONE;

        public LabKind active;

        public boolean placed;
    }

    static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    private static double stationArm(double f) {
        return f < 0.12 ? ease(f / 0.12) : f < 0.24 ? 1.0 - ease((f - 0.12) / 0.12) : f < 0.76 ? 0.0 : f < 0.88 ? ease((f - 0.76) / 0.12) : 1.0 - ease((f - 0.88) / 0.12);
    }

    private static void station(Pose p, LabKind who, double f, double car, Item in, Item cellItem, Item out) {
        p.car = car; p.active = who; p.arm = stationArm(f);
        if (f < 0.12) p.carry = in;
        else if (f < 0.88) p.cell = cellItem;
        else p.carry = out;
    }

    public static Pose compute(LabModuleBlockEntity.Stage s, double t, int len, int grade, double wheelNow, double wheelFrom, double wheelTo) {
        Pose p = new Pose();
        double f = len > 0 ? Mth.clamp(t / len, 0.0, 1.0) : 0.0;
        p.f = f;
        p.wheelDeg = wheelNow;
        switch (s) {
            case IDLE -> { }
            case LOAD -> {
                p.active = LabKind.CORE;
                p.arm = f < 0.25 ? 0.0 : f < 0.45 ? ease((f - 0.25) / 0.2) : f < 0.65 ? 1.0 - ease((f - 0.45) / 0.2) : 0.0;
                if (f < 0.45) p.cell = Item.INGOTS; else p.carry = Item.INGOTS;
            }
            case TO_PRESS -> { p.car = ease(f); p.carry = Item.INGOTS; }
            case PRESS -> {

                station(p, LabKind.PRESS, f, 1, Item.INGOTS, f < 0.34 ? Item.INGOTS : f < 0.66 ? Item.PLATES : Item.SHELL, Item.SHELL);
            }
            case TO_FILLER -> { p.car = 1 + ease(f); p.carry = Item.SHELL; }
            case FILLER -> station(p, LabKind.FILLER, f, 2, Item.SHELL, f < 0.30 ? Item.SHELL : Item.CHARGED, Item.CHARGED);
            case TO_REACTOR -> { p.car = 2 + ease(f); p.carry = Item.CHARGED; }
            case REACTOR -> {
                p.car = 3; p.active = LabKind.REACTOR;
                p.arm = t < 12 ? ease(t / 12.0) : t < 24 ? 1.0 - ease((t - 12) / 12.0) : 0.0;
                if (t < 12) p.carry = Item.CHARGED; else p.cell = Item.CHARGED;
            }
            case TO_OVEN -> {
                if (f < 0.28) {
                    p.car = 3; p.active = LabKind.REACTOR;
                    p.arm = f < 0.14 ? ease(f / 0.14) : 1.0 - ease((f - 0.14) / 0.14);
                    if (f < 0.14) p.cell = Item.CHARGED; else p.carry = Item.CHARGED;
                } else { p.car = 3 + ease((f - 0.28) / 0.72); p.carry = Item.CHARGED; }
            }
            case OVEN -> station(p, LabKind.OVEN, f, 4, Item.CHARGED, f < 0.75 ? Item.CHARGED : Item.WARHEAD, Item.WARHEAD);
            case TO_STORE -> { p.car = 4 + ease(f); p.carry = Item.WARHEAD; }
            case STORE -> {
                p.car = 5; p.active = LabKind.STORAGE; p.reach = 28.0;
                p.wheelDeg = wheelFrom + (wheelTo - wheelFrom) * ease(f / 0.35);
                p.arm = f < 0.4 ? 0.0 : f < 0.52 ? ease((f - 0.4) / 0.12) : f < 0.64 ? 1.0 - ease((f - 0.52) / 0.12) : 0.0;
                p.turn = f < 0.4 ? 0.0 : f < 0.52 ? ease((f - 0.4) / 0.12) : 1.0;
                p.placed = f >= 0.52;
                p.carry = p.placed ? Item.NONE : Item.WARHEAD;
            }
            case TO_HOME -> p.car = 5 - 5 * ease(f);
            case FAIL_HOME -> p.car = 3 - 3 * ease(f);
        }
        if (s != LabModuleBlockEntity.Stage.STORE) p.wheelDeg = wheelNow;
        return p;
    }
}
