package com.ashraf.wmd;

public enum BayKind {
    CORE(8), WELD(8), CRANE(8), WARHEAD(8), CLEAN(8), PAINT(8), CRYO(8), EXTEND(8), RACK(24), RACK_R(24);

    public static final int MAX_EXTENSIONS = 6;

    public final int length;

    public boolean isRack() { return this == RACK || this == RACK_R; }

    BayKind(int length) { this.length = length; }

    public BayKind following() { return this != EXTEND && ordinal() + 1 < values().length ? values()[ordinal() + 1] : null; }
}
