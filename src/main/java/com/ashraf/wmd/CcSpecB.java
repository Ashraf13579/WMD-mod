package com.ashraf.wmd;

public final class CcSpecB {

    private CcSpecB() {}

    public record Rect(float x0, float x1, float y0, float y1, float z, float tilt) {}

    public static final Rect LAUNCH_SCREEN = new Rect(-21.0F, -4.4F, 20.4F, 29.2F, 15.4F, 0.0F);

    public static final Rect STRIKE_SCREEN = new Rect(-21.4F, 21.4F, 20.4F, 29.2F, 15.4F, 0.0F);

    public static final Rect RETARGET_SCREEN = new Rect(-21.0F, 5.0F, 20.4F, 29.2F, 15.4F, 0.0F);

    public static final Rect CAM_SCREEN = new Rect(-20.4F, 20.4F, 6.0F, 24.6F, -3.6F, 0.0F);

    public static final float COVER_OPEN_DEG = -95.0F;

    public static final float KEY_TURN_DEG = 90.0F;

    public static final float STICK_TILT_DEG = 14.0F;

    public static final int ARMED_TICKS = 200;

    public static final float XS = CcModelSpec.XS;
}
