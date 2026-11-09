package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MagnesiumCellRenderer extends RefineryUnitRenderer<MagnesiumCellBlockEntity> {

    private static final Set<String> BONES = Set.of("mglow", "mwell", "mhot", "mcast");

    public MagnesiumCellRenderer() { super("magnesium_cell", 2); }

    @Override protected Set<String> attachBones() { return BONES; }
    @Override protected boolean drawsItself(String bone) { return BONES.contains(bone); }

    @Override
    protected void drawAttached(MagnesiumCellBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("magnesium_cell");
        if (mesh == null || be.getLevel() == null) return;
        double tt = be.getLevel().getGameTime();
        float cur = be.currentShown();
        for (String n : BONES) {
            Matrix4f m = poses.get(n);
            if (m == null) continue;
            float a = n.equals("mhot") ? be.hotShown(tt) : n.equals("mcast") ? be.castShown(tt) : cur;
            int alpha = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, a)));
            if (alpha < 8) continue;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            ps.last().normal().set(normals.get(n));
            mesh.drawTinted(n, ps.last(), buffers, light, overlay, 255, 255, 255, alpha, false);
        }
    }
}
