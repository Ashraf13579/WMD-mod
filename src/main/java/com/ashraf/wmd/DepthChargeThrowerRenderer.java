package com.ashraf.wmd;

import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;

public class DepthChargeThrowerRenderer extends StagedUnitRenderer<DepthChargeThrowerBlockEntity> {

    private static final float[] SPARE_C = { 0.0F, 10.2F, 16.4F };

    private static final float PV_X = 6.4F, PV_Y = 5.0F, PV_Z = 4.0F, PLATE_UP = 13.7F, CHOCK_Y = 5.3F, LEAN = 30.0F;
    private static final java.util.Set<String> BONES = java.util.Set.of("hook", "drum", "spare");

    public DepthChargeThrowerRenderer() { super("depth_charge_thrower", 2, 2); }

    @Override protected java.util.Set<String> attachBones() { return BONES; }

    private DepthChargeThrowerBlockEntity current;

    @Override
    public void render(DepthChargeThrowerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        current = be;
        try { super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay); }
        finally { current = null; }
    }

    @Override
    protected boolean drawsItself(String bone) {
        DepthChargeThrowerBlockEntity be = current;
        if (be == null) return false;
        return ("drum".equals(bone) || "dial".equals(bone)) && be.arborMine() >= 0 || "spare".equals(bone) && be.spareMine() >= 0;
    }

    @Override
    protected void drawAttached(DepthChargeThrowerBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (be.arborMine() >= 0 && poses.containsKey("drum")) {
            PoseStack ps = at(poses, normals, "drum");
            ps.translate(PV_X / 16.0F, PV_Y / 16.0F, PV_Z / 16.0F);
            ps.mulPose(Axis.ZP.rotationDegrees(LEAN));
            ps.translate(0.0F, PLATE_UP / 16.0F, 0.0F);
            lump(be.arborMine(), ps, buffers, light, overlay);
        }
        if (be.spareMine() >= 0 && poses.containsKey("spare")) {
            PoseStack ps = at(poses, normals, "spare");
            ps.translate(SPARE_C[0] / 16.0F, CHOCK_Y / 16.0F, SPARE_C[2] / 16.0F);
            lump(be.spareMine(), ps, buffers, light, overlay);
        }
        if (be.getLevel() == null || !KGunAnim.carrying(KGunAnim.reload01(be, be.getLevel().getGameTime())) || !poses.containsKey("hook")) return;
        PoseStack ps = at(poses, normals, "hook");
        if (be.spareMine() >= 0) {
            ps.translate(SPARE_C[0] / 16.0F, (CHOCK_Y + KGunAnim.HOOK_DROP) / 16.0F, SPARE_C[2] / 16.0F);
            lump(be.spareMine(), ps, buffers, light, overlay);
            return;
        }
        MeshModel mesh = MeshModel.named("depth_charge");
        if (!mesh.has("part")) return;
        ps.translate(SPARE_C[0] / 16.0F, (SPARE_C[1] - 5.0F + KGunAnim.HOOK_DROP) / 16.0F, SPARE_C[2] / 16.0F);
        mesh.draw("part", ps.last(), buffers, light, overlay);
    }

    private static PoseStack at(Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, String bone) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(poses.get(bone));
        ps.last().normal().set(normals.get(bone));
        return ps;
    }

    private static void lump(int kind, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ps.scale(DepthChargeThrowerBlockEntity.MINE_SCALE, DepthChargeThrowerBlockEntity.MINE_SCALE, DepthChargeThrowerBlockEntity.MINE_SCALE);
        LaidMineRenderer.drawLump(kind, ps, buffers, light, overlay);
    }
}
