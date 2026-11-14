package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

public class OrdnanceLoaderRenderer extends StagedUnitRenderer<OrdnanceLoaderBlockEntity> {

    private static final Set<String> ATTACH = Set.of("hatch_l", "hatch_r", "hoist3", "lift");
    private final String name;
    private BlockState skin;

    public OrdnanceLoaderRenderer(String name, int up, int reach) {
        super(name, up, reach);
        this.name = name;
    }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && bone.startsWith("hatch_"); }

    @Override
    public void render(OrdnanceLoaderBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(OrdnanceLoaderBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin != null) {
            for (String h : new String[] { "hatch_l", "hatch_r" }) {
                if (poses.containsKey(h)) CopycatSkin.draw(MeshModel.named(name), h, poses.get(h), normals.get(h), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
            }
        }
        if (be.getLevel() == null || !be.busy()) return;
        double tt = be.getLevel().getGameTime() + net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        int where = (int) be.pose(tt)[9];
        if (where != 1 && where != 2) return;
        net.minecraft.world.item.ItemStack round = be.carried();
        if (round.isEmpty()) return;
        String mesh = round.getItem() instanceof AamItem a ? AamItem.mesh(a.kind()) : null;
        MeshModel mm = mesh == null ? null : MeshModel.named(mesh);
        if (mm != null && !mm.has("part")) mm = null;
        OrdnanceLoaderBlockEntity.Geo g = be.geo();
        float hang = mm == null ? 8.0F : AamMeshes.hangPx(mesh), half = mm == null ? 6.0F : AamMeshes.lengthPx(mesh) / 2.0F;
        PoseStack ps = new PoseStack();
        if (where == 1) {
            Matrix4f m = poses.get("hoist3");
            if (m == null) return;
            ps.last().pose().set(m);
            ps.last().normal().set(normals.get("hoist3"));
            ps.translate(g.hoistX() / 16.0, (g.hoistTop() + half) / 16.0, g.hoistZ() / 16.0);
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.translate(0.0, -(hang - 0.45) / 2.0 / 16.0, 0.0);
        } else {
            Matrix4f m = poses.get("lift");
            if (m == null) return;
            ps.last().pose().set(m);
            ps.last().normal().set(normals.get("lift"));
            ps.translate(g.c() / 16.0, (g.sadTop() + 0.2) / 16.0, g.sadC() / 16.0);
        }
        if (mm != null) {
            int[] t = MissileParts.tint(AamItem.paint(((AamItem) round.getItem()).kind()));
            mm.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
            return;
        }

        ps.translate(0.0, 6.0 / 16.0, 0.0);
        ps.scale(0.75F, 0.75F, 0.75F);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(round, net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
    }
}
