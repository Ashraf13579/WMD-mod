package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

public class C4Renderer implements BlockEntityRenderer<C4ChargeBlockEntity> {

    @Override
    public void render(C4ChargeBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("c4_charge");
        if (!mesh.has("base") || be.getLevel() == null) return;
        Direction f = Machines.facing(be.getBlockState());
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        float fuse = be.fuse(be.getLevel(), pt);
        if (fuse >= 0.0F && mesh.has("led")) {
            double period = 20.0 - 17.0 * fuse;
            long t = be.getLevel().getGameTime();
            if ((t + pt) % period < period * 0.5) mesh.draw("led", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        }
        ps.popPose();
    }
}
