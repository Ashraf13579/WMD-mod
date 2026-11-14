package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

public class HeliRotorRenderer extends StagedUnitRenderer<HeliRotorBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing", "blade7", "blade11", "blade15");
    private static final float FOLD_R = 5.6F / 16.0F;

    private final Map<String, float[]> geo = new HashMap<>();
    private BlockState skin;

    public HeliRotorRenderer() { super("heli_rotor", 1, 1); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return bone.startsWith("blade") || (skin != null && "housing".equals(bone)); }

    @Override
    public void render(HeliRotorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(HeliRotorBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("heli_rotor");
        if (skin != null && poses.containsKey("housing"))
            CopycatSkin.draw(mesh, "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        String bone = "blade" + be.bladeLength();
        Matrix4f m = poses.get(bone);
        Matrix3f nm = normals.get(bone);
        if (m == null || nm == null || !mesh.has(bone)) return;
        float[] g = geo.computeIfAbsent(bone, b -> measure(mesh, b));
        if (g == null) return;
        int n = Math.max(2, Math.min(4, be.bladeCount()));
        float pitch = (float) Math.toRadians(be.pitchShown());
        float aft = (float) Math.atan2(-1.0, 0.0);
        for (int k = 0; k < n; k++) {
            float spread = (float) (Math.PI * 2.0 * k / n);
            Matrix4f c = new Matrix4f(m).translate(g[0], g[2], g[1]).rotateY(spread);
            if (be.folded()) {
                float target = aft + (float) Math.toRadians((k - (n - 1) / 2.0) * 6.0);
                float d = wrap(target - (g[3] + spread));
                float hx = (float) Math.cos(g[3]) * FOLD_R, hz = (float) -Math.sin(g[3]) * FOLD_R;
                c.translate(hx, k * 1.1F / 16.0F, hz).rotateY(d).translate(-hx, 0.0F, -hz);
            }
            Vector3f span = new Vector3f((float) Math.cos(g[3]), 0.0F, (float) -Math.sin(g[3]));
            c.rotate(pitch, span).translate(-g[0], -g[2], -g[1]);
            Matrix3f cn = new Matrix3f(nm).rotateY(spread);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(c);
            ps.last().normal().set(cn);
            mesh.draw(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        }
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static float[] measure(MeshModel mesh, String bone) {
        double[] ax = new double[3];
        mesh.forEachQuad("mast", (v, i) -> { for (int j = 0; j < 4; j++) { ax[0] += v[i + j * 8]; ax[1] += v[i + j * 8 + 2]; ax[2]++; } });
        if (ax[2] == 0) return null;
        float cx = (float) (ax[0] / ax[2]), cz = (float) (ax[1] / ax[2]);
        float[] best = { -1.0F, 0.0F, 0.0F, 0.0F };
        mesh.forEachQuad(bone, (v, i) -> {
            for (int j = 0; j < 4; j++) {
                float x = v[i + j * 8] - cx, y = v[i + j * 8 + 1], z = v[i + j * 8 + 2] - cz;
                float r = x * x + z * z;
                if (r > best[0]) { best[0] = r; best[1] = x; best[2] = z; best[3] = y; }
            }
        });
        if (best[0] <= 0.0F) return null;
        return new float[] { cx, cz, best[3], (float) Math.atan2(-best[2], best[1]) };
    }
}
