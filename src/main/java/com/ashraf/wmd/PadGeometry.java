package com.ashraf.wmd;

public final class PadGeometry {

    private PadGeometry() {}

    public static float hatchHalf(int padTier) {
        return switch (padTier) { case 1 -> 18.0F; case 2 -> 24.0F; default -> 32.0F; };
    }
}
