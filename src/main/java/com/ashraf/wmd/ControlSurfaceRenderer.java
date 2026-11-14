package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class ControlSurfaceRenderer implements BlockEntityRenderer<ControlSurfaceBlock.BE> {

    private static final float HINGE_Y = 8.0F / 16.0F, HINGE_Z = -0.8F / 16.0F;

    public ControlSurfaceRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ControlSurfaceBlock.BE be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("wing_control");
        if (!mesh.has("housing")) return;
        BlockState s = be.getBlockState();
        Direction f = s.getValue(WingParts.FACING);
        BlockState skin = be.copycat().shown();
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case EAST -> -90.0F; case WEST -> 90.0F; default -> 0.0F; }));
        if (s.getValue(WingParts.FIN)) { ps.translate(0.0F, 0.5F, 0.0F); ps.mulPose(Axis.ZP.rotationDegrees(90.0F)); ps.translate(0.0F, -0.5F, 0.0F); }
        ps.translate(0.0F, s.getValue(WingParts.LEVEL).shift(), 0.0F);
        draw(mesh, "housing", skin, be, ps, buffers, light);
        ps.translate(0.0F, HINGE_Y, HINGE_Z);
        ps.mulPose(Axis.XP.rotationDegrees(be.shown(pt)));
        ps.translate(0.0F, -HINGE_Y, -HINGE_Z);
        mesh.draw("flap", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        draw(mesh, "housing_flap", skin, be, ps, buffers, light);
        ps.popPose();
    }

    private static void draw(MeshModel mesh, String bone, BlockState skin, ControlSurfaceBlock.BE be, PoseStack ps, MultiBufferSource buffers, int light) {
        if (skin == null) mesh.draw(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        else CopycatSkin.draw(mesh, bone, ps.last().pose(), ps.last().normal(), skin, be.getLevel(), be.getBlockPos(), buffers, light, OverlayTexture.NO_OVERLAY);
    }
}
