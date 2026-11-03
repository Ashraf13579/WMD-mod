package com.ashraf.wmd;

public enum DroneKind {
    CORE, FRAME, AVIONICS, PROPULSION, TEST, STORAGE, HANGAR;

    public static final int STEP = 5;

    public static final double HANGAR_CAR = 5.5;

    public int offset() { return this == HANGAR ? 33 : STEP * ordinal(); }

    public String id() { return name().toLowerCase(); }

    public String block() { return "drone_" + id(); }
}
