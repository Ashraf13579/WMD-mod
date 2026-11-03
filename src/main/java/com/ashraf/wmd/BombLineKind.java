package com.ashraf.wmd;

public enum BombLineKind {
    CORE, PRESS, FILLING, FUZE, STORAGE, CRADLE;

    public static final int STEP = 5;
    public static final double CRADLE_CAR = 424.0 / 80.0;

    public int offset() { return this == CRADLE ? 27 : STEP * ordinal(); }

    public String id() { return name().toLowerCase(); }

    public String block() { return "bomb_line_" + id(); }
}
