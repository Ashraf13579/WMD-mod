package com.ashraf.wmd;

public final class ChartTerrainView {

    private ChartTerrainView() {}

    public static volatile ChartTerrainPayload tile;
    public static volatile int version;

    public static void set(ChartTerrainPayload p) { tile = p; version++; }
}
