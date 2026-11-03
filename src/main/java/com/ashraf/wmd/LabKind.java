package com.ashraf.wmd;

public enum LabKind {
    CORE, PRESS, FILLER, REACTOR, OVEN, STORAGE;

    public static final int STEP = 5;

    public String id() { return name().toLowerCase(); }
}
