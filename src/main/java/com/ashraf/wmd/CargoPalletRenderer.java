package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class CargoPalletRenderer implements BlockEntityRenderer<CargoPalletBlockEntity> {

    static final String[] BONES = { "base", "net", "pack", "lock" };

    @Override
    public void render(CargoPalletBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mm = MeshModel.named(be.heavy() ? "cargo_pallet_heavy" : "cargo_pallet");
        if (!mm.has("base")) return;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));
        for (String b : BONES) if (mm.has(b)) mm.draw(b, ps.last(), buffers, light, overlay);
        if (mm.has("lamp")) mm.drawColoured("lamp", ps.last(), buffers, 0xF000F0, overlay, 0x4A, 0xA3, 0xFF);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CargoPalletBlockEntity be) { return new AABB(be.getBlockPos()).inflate(2.0); }
}
