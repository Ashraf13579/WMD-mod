package com.ashraf.wmd;

final class AamMeshes {

    private AamMeshes() {}

    static final float HEAT_R = 1.76F, HEAT_HL = 19.2F, RADAR_R = 2.56F, RADAR_HL = 24.0F, LUG = 0.45F;

    static final float AGM_R = 3.0F, AGM_HL = 20.8F;

    static boolean big(String mesh) { return "aam_radar".equals(mesh) || "aam_arm".equals(mesh); }

    static float radiusPx(String mesh) { return "aam_agm".equals(mesh) ? AGM_R : big(mesh) ? RADAR_R : HEAT_R; }

    static float lengthPx(String mesh) { return 2.0F * ("aam_agm".equals(mesh) ? AGM_HL : big(mesh) ? RADAR_HL : HEAT_HL); }

    static float hangPx(String mesh) { return 2.0F * radiusPx(mesh) + LUG; }
}
