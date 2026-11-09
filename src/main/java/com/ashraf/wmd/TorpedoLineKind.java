package com.ashraf.wmd;

public enum TorpedoLineKind {
    SHOP, FITOUT, TEST;

    public static final int STEP = 6;

    public int offset() { return STEP * ordinal(); }

    public String id() { return name().toLowerCase(); }

    public String block() { return "torpedo_line_" + id(); }
}
