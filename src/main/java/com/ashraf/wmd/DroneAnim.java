package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class DroneAnim {

    private DroneAnim() {}

    public enum Item { NONE, CRATE, FRAME, AVIONICS, POWERED, DRONE }

    public static final double REACH = 20.0, STACK_REACH = 15.0, HANGAR_REACH = 12.0, STACK_OUT = 12.0;

    public static final double[] SHELF_X = { -24.85, 0.0, 24.85 }, SHELF_Y = { 9.0, 23.0, 37.0 };
    public static final double FORK_Y = 18.0, HOVER = 9.0;

    public static final class Pose {
        public double car, arm, reach = REACH, f;
        public Item carry = Item.NONE, cell = Item.NONE;

        public DroneKind active;
        public double stkX, liftY, fork, hover;

        public boolean onStacker, placed, spin, inHangar, wingsOn;
    }

    static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    private static double stationArm(double f) {
        return f < 0.12 ? ease(f / 0.12) : f < 0.24 ? 1.0 - ease((f - 0.12) / 0.12) : f < 0.76 ? 0.0 : f < 0.88 ? ease((f - 0.76) / 0.12) : 1.0 - ease((f - 0.88) / 0.12);
    }

    private static void station(Pose p, DroneKind who, double f, Item in, Item cell, Item out) {
        p.car = who.ordinal(); p.active = who; p.arm = stationArm(f);
        if (f < 0.12) p.carry = in;
        else if (f < 0.88) p.cell = cell;
        else p.carry = out;
    }

    public static double storeStop(DroneType t) { return t.big ? DroneKind.HANGAR_CAR : DroneKind.STORAGE.ordinal(); }

    public static Pose compute(DroneModuleBlockEntity.Stage s, double t, int len, DroneType type, int slot) {
        Pose p = new Pose();
        double f = len > 0 ? Mth.clamp(t / len, 0.0, 1.0) : 0.0;
        p.f = f;
        double stop = storeStop(type);
        switch (s) {
            case IDLE -> { }
            case LOAD -> {
                p.active = DroneKind.CORE;
                p.arm = f < 0.25 ? 0.0 : f < 0.45 ? ease((f - 0.25) / 0.2) : f < 0.65 ? 1.0 - ease((f - 0.45) / 0.2) : 0.0;
                if (f < 0.45) p.cell = Item.CRATE; else p.carry = Item.CRATE;
            }
            case TO_FRAME -> { p.car = ease(f); p.carry = Item.CRATE; }
            case FRAME -> station(p, DroneKind.FRAME, f, Item.CRATE, f < 0.25 ? Item.CRATE : Item.FRAME, Item.FRAME);
            case TO_AVIONICS -> { p.car = 1 + ease(f); p.carry = Item.FRAME; }
            case AVIONICS -> station(p, DroneKind.AVIONICS, f, Item.FRAME, f < 0.3 ? Item.FRAME : Item.AVIONICS, Item.AVIONICS);
            case TO_PROPULSION -> { p.car = 2 + ease(f); p.carry = Item.AVIONICS; }
            case PROPULSION -> station(p, DroneKind.PROPULSION, f, Item.AVIONICS, f < 0.35 ? Item.AVIONICS : Item.POWERED, Item.POWERED);
            case TO_TEST -> { p.car = 3 + ease(f); p.carry = Item.POWERED; }
            case TEST -> {
                station(p, DroneKind.TEST, f, Item.POWERED, Item.POWERED, Item.DRONE);
                p.spin = f >= 0.25 && f < 0.75;
                if (p.spin && type.rotors != null) p.hover = HOVER * Math.sin(Math.PI * (f - 0.25) / 0.5);
            }
            case TO_STORE -> { p.car = 4 + (stop - 4) * ease(f); p.carry = Item.DRONE; }
            case STORE -> {
                p.car = stop;
                if (type.big) {
                    p.active = DroneKind.HANGAR; p.reach = HANGAR_REACH;
                    p.arm = f < 0.12 ? ease(f / 0.12) : f < 0.24 ? 1.0 - ease((f - 0.12) / 0.12) : 0.0;
                    if (f < 0.12) p.carry = Item.DRONE; else p.inHangar = true;
                    p.wingsOn = f >= 0.6;
                } else {
                    p.active = DroneKind.STORAGE; p.reach = STACK_REACH;
                    p.arm = f < 0.15 ? ease(f / 0.15) : f < 0.3 ? 1.0 - ease((f - 0.15) / 0.15) : 0.0;
                    double tx = SHELF_X[Math.floorMod(slot, 3)], ty = SHELF_Y[Mth.clamp(slot / 3, 0, 2)] + 0.8 - FORK_Y;
                    double go = f < 0.3 ? 0.0 : f < 0.5 ? ease((f - 0.3) / 0.2) : f < 0.74 ? 1.0 : 1.0 - ease((f - 0.74) / 0.21);
                    p.stkX = tx * go; p.liftY = ty * go;
                    p.fork = f < 0.5 ? 0.0 : f < 0.62 ? ease((f - 0.5) / 0.12) : f < 0.74 ? 1.0 - ease((f - 0.62) / 0.12) : 0.0;
                    if (f < 0.15) p.carry = Item.DRONE;
                    else if (f < 0.62) p.onStacker = true;
                    else p.placed = true;
                }
            }
            case TO_HOME -> p.car = stop - stop * ease(f);
        }
        return p;
    }
}
