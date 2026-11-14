package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class JetCockpitRenderer extends StagedUnitRenderer<JetCockpitBlockEntity> {

    static final int[][] GLASS_TINT = { { 120, 132, 146, 150 }, { 225, 240, 250, 92 }, { 255, 196, 92, 140 } };

    private static final Set<String> ATTACH = Set.of("housing", "glass");
    private final String name;
    private BlockState skin;
    private int tint;
    private boolean ejected;

    public JetCockpitRenderer(String name, int cells) {
        super(name, 3, cells);
        this.name = name;
    }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return "glass".equals(bone) || (skin != null && "housing".equals(bone)); }

    @Override
    public void render(JetCockpitBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copyShown();
        tint = be.tintShown();
        ejected = be.ejectedShown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(JetCockpitBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named(name);
        if (skin != null && poses.containsKey("housing"))
            CopycatSkin.draw(mesh, "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        if (poses.containsKey("housing") && be.hudLitShown() && ClientPodPage.pageOn(be)) ClientPodPage.draw(be, name, poses.get("housing"), buffers);
        if (!ejected && poses.containsKey("glass")) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(poses.get("glass"));
            ps.last().normal().set(normals.get("glass"));
            int[] t = GLASS_TINT[Math.max(0, Math.min(2, tint))];
            mesh.drawTinted("glass", ps.last(), buffers, light, overlay, t[0], t[1], t[2], t[3], false);
        }
    }
}
