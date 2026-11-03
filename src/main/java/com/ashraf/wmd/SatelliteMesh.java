package com.ashraf.wmd;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

public final class SatelliteMesh {

    private SatelliteMesh() {}

    static final Map<String, float[]> DEPLOY = Map.of(
            "wing_l", new float[] { 0.0F, 0.0F, 90.0F },
            "wing_r", new float[] { 0.0F, 0.0F, -90.0F },
            "dish", new float[] { -90.0F, 0.0F, 0.0F },
            "dish2", new float[] { 90.0F, 0.0F, 0.0F },
            "door", new float[] { -100.0F, 0.0F, 0.0F },
            "whip", new float[] { -180.0F, 0.0F, 0.0F },
            "turret", new float[] { -90.0F, 0.0F, 0.0F });

    private static final Map<String, Map<String, float[]>> PIVOTS = Map.of(
            "nav", Map.of("wing_l", new float[] { -9.4F, 5.0F, 0.0F }, "wing_r", new float[] { 9.4F, 5.0F, 0.0F }, "dish", new float[] { 0.0F, 5.0F, -9.4F },
                    "blink", new float[] { 0.0F, 33.6F, 0.0F }),
            "recon", Map.of("wing_l", new float[] { -9.2F, 13.8F, 0.0F }, "wing_r", new float[] { 9.2F, 13.8F, 0.0F }, "door", new float[] { 0.0F, 31.6F, -7.6F },
                    "blink", new float[] { 0.0F, 6.0F, 8.5F }),
            "relay", Map.of("wing_l", new float[] { -8.4F, 4.4F, 0.0F }, "wing_r", new float[] { 8.4F, 4.4F, 0.0F }, "dish", new float[] { 0.0F, 4.4F, -8.4F },
                    "dish2", new float[] { 0.0F, 4.4F, 8.4F }, "blink", new float[] { 5.2F, 22.0F, 5.2F }),
            "interceptor", Map.of("wing_l", new float[] { -9.4F, 5.0F, 0.0F }, "wing_r", new float[] { 9.4F, 5.0F, 0.0F }, "blink", new float[] { 0.0F, 33.0F, 0.0F },
                    "kv0", new float[] { 0.0F, 0.0F, 0.0F }, "kv1", new float[] { 0.0F, 0.0F, 0.0F }, "kv2", new float[] { 0.0F, 0.0F, 0.0F }, "kv3", new float[] { 0.0F, 0.0F, 0.0F },
                    "seeker", new float[] { 0.0F, 30.0F, 0.0F }),

            "cubesat", Map.of("wing_l", new float[] { -2.45F, 1.0F, 0.0F }, "wing_r", new float[] { 2.45F, 1.0F, 0.0F }, "blink", new float[] { 0.0F, 11.8F, 0.0F },
                    "whip", new float[] { 0.0F, 11.2F, 2.6F }),

            "designator", Map.of("wing_l", new float[] { -9.4F, 5.0F, 0.0F }, "wing_r", new float[] { 9.4F, 5.0F, 0.0F }, "turret", new float[] { 0.0F, 5.0F, -9.4F },
                    "blink", new float[] { 0.0F, 32.7F, 0.0F }));

    static String meshType(String type) {
        String t = type == null ? "nav" : type;
        return PIVOTS.containsKey(t) && MeshModel.named("sat_" + t).has("body") ? t : "nav";
    }

    public static boolean installed() { return MeshModel.named("sat_nav").has("body"); }

    public static void draw(String type, float deploy, boolean blinkOn, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        drawFired(type, deploy, blinkOn, 0, ps, buffers, light, overlay);
    }

    public static void drawFired(String type, float deploy, boolean blinkOn, int fired, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        String t = meshType(type);

        if (OrbitMath.CUBESAT.equals(type) && !t.equals(OrbitMath.CUBESAT)) {
            ps.pushPose();
            ps.scale(0.3F, 0.3F, 0.3F);
            drawFired(t, deploy, blinkOn, fired, ps, buffers, light, overlay);
            ps.popPose();
            return;
        }
        String name = "sat_" + t;
        MeshModel mesh = MeshModel.named(name);
        if (!mesh.has("body")) return;
        float d = Math.max(0.0F, Math.min(1.0F, deploy));
        mesh.draw("body", ps.last(), buffers, light, overlay);
        for (Map.Entry<String, float[]> e : PIVOTS.get(t).entrySet()) {
            String bone = e.getKey();
            if (bone.equals("blink") && !blinkOn) continue;
            if (bone.startsWith("kv") && bone.length() == 3 && bone.charAt(2) - '0' < fired) continue;
            if (!mesh.has(bone)) continue;
            float[] rot = DEPLOY.getOrDefault(bone, new float[3]);
            float ry = rot[1] * d;
            if (bone.equals("seeker")) ry = (float) ((System.currentTimeMillis() / 50L) % 360L) * d;
            drawBone(mesh, name, bone, e.getValue(), rot[0] * d, ry, rot[2] * d, ps, buffers, light, overlay);
        }
    }

    static void drawBone(MeshModel mesh, String geoName, String bone, float[] fallbackPivot, float rx, float ry, float rz, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        float[] pv = pivot(geoName, bone, fallbackPivot);

        rx = BoneOverrides.deg(geoName, bone, "rotX", rx);
        ry = BoneOverrides.deg(geoName, bone, "rotY", ry);
        rz = BoneOverrides.deg(geoName, bone, "rotZ", rz);
        ps.pushPose();
        float ox = BoneOverrides.px(geoName, bone, "posX"), oy = BoneOverrides.px(geoName, bone, "posY"), oz = BoneOverrides.px(geoName, bone, "posZ");
        if (ox != 0.0F || oy != 0.0F || oz != 0.0F) ps.translate(-ox / 16.0F, oy / 16.0F, oz / 16.0F);
        ps.translate(pv[0] / 16.0F, pv[1] / 16.0F, pv[2] / 16.0F);
        if (rz != 0.0F) ps.mulPose(Axis.ZP.rotationDegrees(rz));
        if (ry != 0.0F) ps.mulPose(Axis.YP.rotationDegrees(ry));
        if (rx != 0.0F) ps.mulPose(Axis.XP.rotationDegrees(rx));
        ps.translate(-pv[0] / 16.0F, -pv[1] / 16.0F, -pv[2] / 16.0F);
        mesh.draw(bone, ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    static float[] pivot(String geoName, String bone, float[] fallback) {
        BakedGeoModel m = GeckoLibCache.getBakedModels().get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + geoName + ".geo.json"));
        if (m != null) {
            GeoBone b = m.getBone(bone).orElse(null);
            if (b != null) return new float[] { b.getPivotX(), b.getPivotY(), b.getPivotZ() };
        }
        return fallback;
    }

    public static boolean blink(double ticks, long phase) { return ((long) ticks + phase) % 30L < 10L; }
}
