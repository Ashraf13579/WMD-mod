package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class XlDockRenderer implements BlockEntityRenderer<XlDockBlockEntity> {

    private static final String NAME = XlDockAnim.NAME;

    @Override
    public void render(XlDockBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("base")) return;
        final int FB = LightTexture.FULL_BRIGHT;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        for (int k = 0; k < 8; k++) {
            String b = "clamp" + k;
            if (mesh.has(b)) SatelliteMesh.drawBone(mesh, NAME, b, XlDockAnim.CHOCK_PIV[k], XlDockAnim.chockRx(k, be.chock(k / 2, partialTick)), 0.0F, 0.0F, ps, buffers, light, overlay);
        }
        float ry = XlDockAnim.ARM_FOLD * (1.0F - be.swing(partialTick));
        if (mesh.has("arm")) SatelliteMesh.drawBone(mesh, NAME, "arm", XlDockAnim.ARM_PIV, 0.0F, ry, 0.0F, ps, buffers, light, overlay);

        float[] pv = SatelliteMesh.pivot(NAME, "arm", XlDockAnim.ARM_PIV);
        ps.pushPose();
        ps.translate(pv[0] / 16.0F, pv[1] / 16.0F, pv[2] / 16.0F);
        ps.mulPose(Axis.YP.rotationDegrees(ry));
        ps.translate(-pv[0] / 16.0F, -pv[1] / 16.0F, -pv[2] / 16.0F);
        if (be.contactClosed() && mesh.has("contact") && !dark(be)) mesh.draw("contact", ps.last(), buffers, FB, overlay);
        ps.translate(0.0F, -XlDockAnim.PLUG_DROP * be.shoe(partialTick) / 16.0F, 0.0F);
        if (mesh.has("plug")) mesh.draw("plug", ps.last(), buffers, light, overlay);
        ps.popPose();

        if (!dark(be)) {
            long tt = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
            int ph = be.phase();
            for (int k = 0; k < XlDockAnim.NGL; k++)
                if (XlDockAnim.guideLit(k, ph, be.seq(), be.leaveAge(), tt) && mesh.has("gl" + k)) mesh.draw("gl" + k, ps.last(), buffers, FB, overlay);
            if (XlDockAnim.thresholdLit(ph, be.leaveAge()) && mesh.has("thresh")) mesh.draw("thresh", ps.last(), buffers, FB, overlay);
            for (int k = 0; k < XlDockAnim.NBAR; k++)
                if (XlDockAnim.barLit(k, be.contactClosed(), be.level10(), be.charging(), tt) && mesh.has("cl" + k)) mesh.draw("cl" + k, ps.last(), buffers, FB, overlay);
            boolean moving = ph == RobotDockBlockEntity.INBOUND || be.leaveAge() >= 0;
            if (moving && SatelliteMesh.blink(tt, 0L) && mesh.has("lamp_b")) mesh.draw("lamp_b", ps.last(), buffers, FB, overlay);
            if (be.charging() && mesh.has("lamp_a")) mesh.draw("lamp_a", ps.last(), buffers, FB, overlay);
            if (!moving && !be.charging() && mesh.has("lamp_g")) mesh.draw("lamp_g", ps.last(), buffers, FB, overlay);
        }
        ps.popPose();
    }

    private static boolean dark(XlDockBlockEntity be) {
        return !be.powered() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
    }

    @Override
    public AABB getRenderBoundingBox(XlDockBlockEntity be) { return new AABB(be.getBlockPos()).inflate(18.0, 4.0, 18.0); }

    @Override
    public int getViewDistance() { return 160; }
}
