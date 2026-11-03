package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class BombLineAnim {

    private BombLineAnim() {}

    public enum Item { NONE, CRATE, CASING, FILLED, BOMB }

    public static final double REACH = 20.0, STACK_REACH = 15.0, CRADLE_REACH = 20.0;

    public static final double[] SHELF_Y = { 9.0, 23.0, 37.0 }, SHELF_Z = { 25.0, 13.0 };
    public static final double FORK_Y = 18.0, FORK_Z = 31.0;

    public static final class Pose {
        public double car, arm, reach = REACH, f;
        public Item carry = Item.NONE, cell = Item.NONE;
        public BombLineKind active;
        public double liftY, fork, forkOut, hoistX, hookY;
        public boolean onStacker, placed, onCradle, filled, done;
    }

    static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    private static double stationArm(double f) {
        return f < 0.12 ? ease(f / 0.12) : f < 0.24 ? 1.0 - ease((f - 0.12) / 0.12) : f < 0.76 ? 0.0 : f < 0.88 ? ease((f - 0.76) / 0.12) : 1.0 - ease((f - 0.88) / 0.12);
    }

    private static void station(Pose p, BombLineKind who, double f, Item in, Item cell, Item out) {
        p.car = who.ordinal(); p.active = who; p.arm = stationArm(f);
        if (f < 0.12) p.carry = in;
        else if (f < 0.88) p.cell = cell;
        else p.carry = out;
    }

    public static double storeStop(BombType t) { return t.heavy ? BombLineKind.CRADLE_CAR : BombLineKind.STORAGE.ordinal(); }

    public static Pose compute(BombLineBlockEntity.Stage s, double t, int len, BombType type, int slot) {
        Pose p = new Pose();
        double f = len > 0 ? Mth.clamp(t / len, 0.0, 1.0) : 0.0;
        p.f = f;
        double stop = storeStop(type);
        switch (s) {
            case IDLE -> { }
            case LOAD -> {
                p.active = BombLineKind.CORE;
                p.arm = f < 0.25 ? 0.0 : f < 0.45 ? ease((f - 0.25) / 0.2) : f < 0.65 ? 1.0 - ease((f - 0.45) / 0.2) : 0.0;
                if (f < 0.45) p.cell = Item.CRATE; else p.carry = Item.CRATE;
            }
            case TO_PRESS -> { p.car = ease(f); p.carry = Item.CRATE; }
            case PRESS -> station(p, BombLineKind.PRESS, f, Item.CRATE, f < 0.3 ? Item.CRATE : Item.CASING, Item.CASING);
            case TO_FILLING -> { p.car = 1 + ease(f); p.carry = Item.CASING; }
            case FILLING -> station(p, BombLineKind.FILLING, f, Item.CASING, f < 0.55 ? Item.CASING : Item.FILLED, Item.FILLED);
            case TO_FUZE -> { p.car = 2 + ease(f); p.carry = Item.FILLED; }
            case FUZE -> station(p, BombLineKind.FUZE, f, Item.FILLED, f < 0.6 ? Item.FILLED : Item.BOMB, Item.BOMB);
            case TO_STORE -> { p.car = 3 + (stop - 3) * ease(f); p.carry = Item.BOMB; }
            case STORE -> {
                p.car = stop;
                if (type.heavy) {
                    p.active = BombLineKind.CRADLE; p.reach = CRADLE_REACH;
                    p.arm = f < 0.08 ? ease(f / 0.08) : f < 0.16 ? 1.0 - ease((f - 0.08) / 0.08) : 0.0;
                    if (f < 0.08) p.carry = Item.BOMB; else p.onCradle = true;
                    p.filled = f >= 0.35;
                    p.done = f >= 0.6;
                    double w = Mth.clamp((f - 0.16) / 0.44, 0.0, 1.0);
                    p.hoistX = 40.0 * Math.sin(w * Math.PI * 2.0);
                    p.hookY = -6.0 * Math.abs(Math.sin(w * Math.PI * 4.0));
                } else {
                    p.active = BombLineKind.STORAGE; p.reach = STACK_REACH;
                    p.arm = f < 0.15 ? ease(f / 0.15) : f < 0.3 ? 1.0 - ease((f - 0.15) / 0.15) : 0.0;
                    double ty = SHELF_Y[Mth.clamp(slot / 2, 0, 2)] + 0.8 - FORK_Y;
                    p.forkOut = FORK_Z - SHELF_Z[Math.floorMod(slot, 2)];
                    double go = f < 0.3 ? 0.0 : f < 0.5 ? ease((f - 0.3) / 0.2) : f < 0.74 ? 1.0 : 1.0 - ease((f - 0.74) / 0.21);
                    p.liftY = ty * go;
                    p.fork = f < 0.5 ? 0.0 : f < 0.62 ? ease((f - 0.5) / 0.12) : f < 0.74 ? 1.0 - ease((f - 0.62) / 0.12) : 0.0;
                    if (f < 0.15) p.carry = Item.BOMB;
                    else if (f < 0.62) p.onStacker = true;
                    else p.placed = true;
                }
            }
            case TO_HOME -> p.car = stop - stop * ease(f);
        }
        return p;
    }
}
