package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class KrollReactorRenderer extends RefineryUnitRenderer<KrollReactorBlockEntity> {

    private static final Set<String> BONES = Set.of("heat");

    public KrollReactorRenderer() { super("kroll_reactor", 3); }

    @Override protected Set<String> attachBones() { return BONES; }
    @Override protected boolean drawsItself(String bone) { return BONES.contains(bone); }

    @Override
    protected void drawAttached(KrollReactorBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        Matrix4f m = poses.get("heat");
        MeshModel mesh = MeshModel.named("kroll_reactor");
        if (m == null || mesh == null) return;
        int alpha = Math.round(255.0F * Math.min(1.0F, be.heatShown()));
        if (alpha < 8) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get("heat"));
        mesh.drawTinted("heat", ps.last(), buffers, light, overlay, 255, 255, 255, alpha, false);
    }
}
